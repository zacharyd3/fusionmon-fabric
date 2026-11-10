package com.arnau.fusionmon.worldfirst;

import com.arnau.fusionmon.FusionConfig;
import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.fusion.FusionDiscovery;
import com.cobblemon.mod.common.pokemon.FormData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * World First: un registro público, compartido por todos los servidores que participan, de qué entrenador descubrió
 * primero cada fusión. Cuando un jugador crea una pareja nueva para él (FusionService → FusionDiscovery.register), el
 * servidor se lo cuenta al servicio (una Cloudflare Worker, carpeta worldfirst/ del repositorio); si nadie la había
 * descubierto antes en ningún servidor, es un World First y se anuncia en el chat (y en Discord, si el servicio tiene
 * webhook).
 *
 * Solo si el dueño del servidor lo activa (worldFirstUrl + worldFirstToken en config/fusionmon.json): por defecto no sale
 * nada del servidor. Cada jugador decide si va con su nombre (/worldfirst name); si no, va con un identificador que no
 * se puede volver a su cuenta. Se avisa a cada jugador la primera vez que entra.
 *
 * Todo pasa en el hilo del servidor salvo las peticiones, que van en segundo plano (HttpClient asíncrono) y vuelven a
 * él con server.execute. Lo que no se ha podido mandar (servicio caído, servidor apagado) se guarda en la partida
 * (fusionmon/worldfirst-queue.json) y se manda después, con esperas crecientes entre intentos.
 */
public final class WorldFirst {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String QUEUE_FILE = "worldfirst-queue.json";
    /** Peticiones a la vez como mucho. */
    private static final int MAX_IN_FLIGHT = 2;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** Primera espera tras un fallo; se dobla en cada fallo seguido, hasta MAX_BACKOFF. */
    private static final long FIRST_BACKOFF = 15_000;
    private static final long MAX_BACKOFF = 10 * 60_000;
    /** Cada cuántos ticks se intenta mandar y, si ha cambiado, se guarda la cola. */
    private static final int PUMP_TICKS = 20;
    private static final int SAVE_TICKS = 20 * 60;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Fusionmon.MOD_ID);
    /** Lo que el jugador ha elegido para su nombre: DEFAULT (lo del servidor), SHOW o HIDE. */
    private static final Supplier<AttachmentType<Integer>> NAME_CHOICE = ATTACHMENT_TYPES.register(
            "world_first_name", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    /** Si ya se le ha explicado al jugador que este servidor participa. */
    private static final Supplier<AttachmentType<Boolean>> NOTIFIED = ATTACHMENT_TYPES.register(
            "world_first_notified", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath()
                    .build());
    private static final int DEFAULT = 0;
    private static final int SHOW = 1;
    private static final int HIDE = 2;

    /** Lo que se manda de un descubrimiento (también es lo que se guarda en la cola). */
    record Report(String head, String body, String headName, String bodyName, String playerId, String playerName,
                  UUID player) {
    }

    private static MinecraftServer server;
    private static HttpClient client;
    private static final Deque<Report> QUEUE = new ArrayDeque<>();
    private static final List<Report> IN_FLIGHT = new ArrayList<>();
    private static boolean dirty;
    private static int failures;
    private static long nextAttempt;
    /** El servicio ha rechazado el token: no se insiste hasta reiniciar (la cola se guarda para entonces). */
    private static boolean rejected;
    private static int ticks;

    private WorldFirst() {
    }

    public static void initialize(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> start(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> stop());
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> tick());
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                notice(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> registerCommands(event.getDispatcher()));
    }

    public static boolean enabled() {
        return !FusionConfig.worldFirstUrl().isEmpty() && !FusionConfig.worldFirstToken().isEmpty();
    }

    /** Un jugador ha creado por primera vez una pareja (ver FusionService): se cuenta a World First. */
    public static void discovered(ServerPlayer player, FormData head, FormData body) {
        if (!enabled()) {
            return;
        }
        try {
            boolean show = showsName(player);
            UUID uuid = player.getUUID();
            QUEUE.addLast(new Report(FusionDiscovery.part(head), FusionDiscovery.part(body), displayName(head),
                    displayName(body), show ? uuid.toString() : anonymousId(uuid),
                    show ? player.getGameProfile().getName() : null, uuid));
            dirty = true;
            pump();
        } catch (RuntimeException e) {
            // Un World First de menos nunca debe estropear una fusión, que ya está hecha
            Fusionmon.LOGGER.warn("Fusionmon: no se ha podido preparar el aviso de World First", e);
        }
    }

    // ---- Ciclo de vida ----

    private static void start(MinecraftServer started) {
        server = started;
        failures = 0;
        nextAttempt = 0;
        rejected = false;
        QUEUE.clear();
        IN_FLIGHT.clear();
        if (!enabled()) {
            return;
        }
        client = HttpClient.newBuilder().connectTimeout(TIMEOUT).proxy(ProxySelector.getDefault()).build();
        Path file = queueFile();
        if (Files.exists(file)) {
            try {
                List<Report> saved = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                        new TypeToken<List<Report>>() { }.getType());
                if (saved != null) {
                    QUEUE.addAll(saved);
                    Fusionmon.LOGGER.info("Fusionmon: {} descubrimientos pendientes de mandar a World First", saved.size());
                }
            } catch (IOException | RuntimeException e) {
                Fusionmon.LOGGER.warn("Fusionmon: no se ha podido leer {}", file, e);
            }
        }
    }

    private static void stop() {
        if (server != null && enabled()) {
            save();
        }
        server = null;
    }

    private static void tick() {
        if (server == null || !enabled()) {
            return;
        }
        ticks++;
        if (ticks % PUMP_TICKS == 0) {
            pump();
        }
        if (dirty && ticks % SAVE_TICKS == 0) {
            save();
        }
    }

    /** La cola (con lo que está en camino: si no llega, mejor mandarlo dos veces que perderlo) en la partida. */
    private static void save() {
        Path file = queueFile();
        try {
            List<Report> all = new ArrayList<>(IN_FLIGHT);
            all.addAll(QUEUE);
            if (all.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(all), StandardCharsets.UTF_8);
            }
            dirty = false;
        } catch (IOException e) {
            Fusionmon.LOGGER.warn("Fusionmon: no se ha podido guardar {}", file, e);
        }
    }

    private static Path queueFile() {
        return server.getWorldPath(LevelResource.ROOT).resolve(Fusionmon.MOD_ID).resolve(QUEUE_FILE);
    }

    // ---- Envío ----

    private static void pump() {
        if (server == null || client == null || rejected || System.currentTimeMillis() < nextAttempt) {
            return;
        }
        while (IN_FLIGHT.size() < MAX_IN_FLIGHT && !QUEUE.isEmpty()) {
            Report report = QUEUE.pollFirst();
            IN_FLIGHT.add(report);
            send(report);
        }
    }

    private static void send(Report report) {
        JsonObject body = new JsonObject();
        body.addProperty("pair", report.head() + ">" + report.body());
        body.addProperty("head", report.head());
        body.addProperty("body", report.body());
        body.addProperty("headName", report.headName());
        body.addProperty("bodyName", report.bodyName());
        body.addProperty("playerId", report.playerId());
        if (report.playerName() != null) {
            body.addProperty("playerName", report.playerName());
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(FusionConfig.worldFirstUrl() + "/api/discoveries"))
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .header("authorization", "Bearer " + FusionConfig.worldFirstToken())
                .header("user-agent", "Fusionmon")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        MinecraftServer target = server;
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) ->
                target.execute(() -> handle(report, response, error)));
    }

    /** La respuesta a un envío, ya en el hilo del servidor. */
    private static void handle(Report report, HttpResponse<String> response, Throwable error) {
        IN_FLIGHT.remove(report);
        dirty = true;
        int status = response == null ? 0 : response.statusCode();
        if (error != null || status == 429 || status >= 500 || status == 0) {
            // Servicio caído, sin red o demasiadas peticiones: vuelve a la cola y se espera cada vez más
            QUEUE.addFirst(report);
            failures++;
            nextAttempt = System.currentTimeMillis() + Math.min(MAX_BACKOFF, FIRST_BACKOFF << Math.min(failures - 1, 10));
            if (failures == 1) {
                Fusionmon.LOGGER.warn("Fusionmon: World First no responde ({}); se reintentará",
                        error != null ? error.toString() : "HTTP " + status);
            }
            return;
        }
        failures = 0;
        if (status == 401 || status == 403) {
            QUEUE.addFirst(report);
            rejected = true;
            Fusionmon.LOGGER.warn("Fusionmon: World First rechaza este servidor (HTTP {}: {}). Revisa worldFirstToken en"
                    + " config/fusionmon.json; los descubrimientos se guardan hasta reiniciar", status, response.body());
            return;
        }
        if (status != 200) {
            // Algo que el servicio no acepta (no debería pasar): se descarta para no atascar la cola
            Fusionmon.LOGGER.warn("Fusionmon: World First no acepta {}>{} (HTTP {}: {})", report.head(), report.body(),
                    status, response.body());
            return;
        }
        try {
            announce(report, JsonParser.parseString(response.body()).getAsJsonObject());
        } catch (RuntimeException e) {
            Fusionmon.LOGGER.warn("Fusionmon: respuesta de World First que no se entiende: {}", response.body(), e);
        }
    }

    /** World First: a todo el servidor. Si no, al jugador, quién la descubrió antes. */
    private static void announce(Report report, JsonObject result) {
        if (server == null) {
            return;
        }
        Component fusion = Component.literal(report.headName() + " + " + report.bodyName())
                .withStyle(ChatFormatting.AQUA);
        if (result.has("first") && result.get("first").getAsBoolean()) {
            Component who = report.playerName() != null
                    ? Component.literal(report.playerName()).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("worldfirst.fusionmon.anonymous");
            server.getPlayerList().broadcastSystemMessage(Component.translatable("worldfirst.fusionmon.first",
                    number(result, "number"), who, fusion).withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(report.player());
        if (player == null) {
            return;
        }
        Component who = text(result, "discoverer") != null ? Component.literal(text(result, "discoverer"))
                : Component.translatable("worldfirst.fusionmon.anonymous");
        String date = result.has("discoveredAt")
                ? DATE.format(Instant.ofEpochMilli(result.get("discoveredAt").getAsLong())) : "?";
        player.sendSystemMessage(Component.translatable("worldfirst.fusionmon.taken", fusion, who,
                String.valueOf(text(result, "server")), date).withStyle(ChatFormatting.GRAY));
    }

    // ---- Jugadores ----

    private static boolean showsName(ServerPlayer player) {
        int choice = player.getData(NAME_CHOICE);
        return choice == SHOW || choice == DEFAULT && FusionConfig.worldFirstShowNames();
    }

    /**
     * Identificador de un jugador anónimo: el mismo en este servidor (sus descubrimientos van juntos), pero no se puede
     * volver a su cuenta sin el token del servidor, que es secreto.
     */
    private static String anonymousId(UUID uuid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((FusionConfig.worldFirstToken() + ":" + uuid).getBytes(StandardCharsets.UTF_8));
            return "anon-" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** La primera vez que entra en un servidor que participa: qué se comparte y cómo dejar de salir con su nombre. */
    private static void notice(ServerPlayer player) {
        if (!enabled() || player.getData(NOTIFIED)) {
            return;
        }
        player.setData(NOTIFIED, true);
        player.sendSystemMessage(Component.translatable(showsName(player)
                ? "worldfirst.fusionmon.notice.named" : "worldfirst.fusionmon.notice.anonymous")
                .withStyle(ChatFormatting.GRAY));
    }

    /** "Raichu (Alola)"; las formas normales, solo la especie. */
    private static String displayName(FormData form) {
        String species = form.getSpecies().getName();
        String name = form.getName();
        return name == null || name.isBlank() || name.equalsIgnoreCase("normal") ? species : species + " (" + name + ")";
    }

    // ---- Comandos ----

    /**
     * /worldfirst: si este servidor participa, cómo sale tu nombre y las cifras de todos los servidores.
     * /worldfirst name show|hide|default: con tu nombre, anónimo o lo que diga el servidor (para lo que descubras desde
     * ahora).
     */
    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("worldfirst")
                .executes(WorldFirst::status)
                .then(Commands.literal("name")
                        .then(Commands.literal("show").executes(context -> setName(context, SHOW)))
                        .then(Commands.literal("hide").executes(context -> setName(context, HIDE)))
                        .then(Commands.literal("default").executes(context -> setName(context, DEFAULT)))));
    }

    private static int setName(CommandContext<CommandSourceStack> context, int choice) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        player.setData(NAME_CHOICE, choice);
        context.getSource().sendSuccess(() -> Component.translatable(showsName(player)
                ? "command.fusionmon.worldfirst.name.show" : "command.fusionmon.worldfirst.name.hide"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!enabled()) {
            source.sendSuccess(() -> Component.translatable("command.fusionmon.worldfirst.off"), false);
            return 1;
        }
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            source.sendSuccess(() -> Component.translatable(showsName(player)
                    ? "command.fusionmon.worldfirst.on.named" : "command.fusionmon.worldfirst.on.anonymous",
                    FusionConfig.worldFirstUrl()), false);
        }
        if (source.hasPermission(2)) {
            int pending = QUEUE.size() + IN_FLIGHT.size();
            source.sendSuccess(() -> Component.translatable(rejected ? "command.fusionmon.worldfirst.rejected"
                    : "command.fusionmon.worldfirst.queue", pending), false);
        }
        if (client == null) {
            return 1;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(FusionConfig.worldFirstUrl() + "/api/stats?limit=3"))
                .timeout(TIMEOUT).header("user-agent", "Fusionmon").GET().build();
        MinecraftServer target = source.getServer();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) ->
                target.execute(() -> showStats(source, response, error)));
        return 1;
    }

    private static void showStats(CommandSourceStack source, HttpResponse<String> response, Throwable error) {
        if (error != null || response.statusCode() != 200) {
            source.sendFailure(Component.translatable("command.fusionmon.worldfirst.unreachable"));
            return;
        }
        try {
            JsonObject stats = JsonParser.parseString(response.body()).getAsJsonObject();
            source.sendSuccess(() -> Component.translatable("command.fusionmon.worldfirst.stats",
                    number(stats, "discovered"), number(stats, "players"), number(stats, "servers"),
                    String.format(Locale.ROOT, "%.3f", stats.get("percent").getAsDouble())), false);
            JsonArray top = stats.getAsJsonArray("topTrainers");
            if (top != null && !top.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (JsonElement trainer : top) {
                    JsonObject row = trainer.getAsJsonObject();
                    names.add(text(row, "name") + " (" + number(row, "count") + ")");
                }
                source.sendSuccess(() -> Component.translatable("command.fusionmon.worldfirst.top",
                        String.join(", ", names)), false);
            }
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable("command.fusionmon.worldfirst.unreachable"));
        }
    }

    private static String number(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? String.format(Locale.ROOT, "%,d", json.get(key).getAsLong())
                : "?";
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }
}
