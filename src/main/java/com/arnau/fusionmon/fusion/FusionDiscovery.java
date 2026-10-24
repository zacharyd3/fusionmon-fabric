package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.network.OpenFusionAlbumPayload;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Registro de fusiones descubiertas de cada jugador: las parejas cabeza + cuerpo que ha creado él (al fusionar o
 * invertir). Una pareja sin descubrir sale en la pantalla de fusionar como silueta, sin nombre, tipos ni stats: se ve
 * por primera vez en la animación (decisión del usuario: descubrir las fusiones jugando).
 *
 * Se guarda pegado al jugador con los "data attachments" de NeoForge: se guarda con su partida (en el servidor) y
 * sobrevive a morir (copyOnDeath). Con Fabric se guardaba en otro sitio de la partida: un mundo de la versión de Fabric
 * llega con el registro vacío (registerExisting vuelve a apuntar las fusiones que el jugador tenga). Es una lista de claves "cabeza>cuerpo" (el orden cuenta: cada orden se ve distinto);
 * aunque alguien descubra miles, son pocos KB.
 */
public final class FusionDiscovery {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Fusionmon.MOD_ID);

    private static final Supplier<AttachmentType<List<String>>> DISCOVERED = ATTACHMENT_TYPES.register(
            "discovered_fusions", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf()).copyOnDeath().build());
    /** Si ya se han apuntado las fusiones que el jugador tenía de antes del registro (ver registerExisting). */
    private static final Supplier<AttachmentType<Boolean>> EXISTING_REGISTERED = ATTACHMENT_TYPES.register(
            "discovered_existing", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL).copyOnDeath().build());
    /**
     * Todas las parejas cuentan como descubiertas (ninguna sale en silueta), sin apuntarlas en el Fusion Album: para
     * grabar o enseñar fusiones sin tener que crearlas antes (/fusionmon reveal, solo desarrollo). Se guarda con el
     * jugador hasta que lo quite (reveal off o forget).
     */
    private static final Supplier<AttachmentType<Boolean>> REVEAL_ALL = ATTACHMENT_TYPES.register(
            "reveal_all", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL).copyOnDeath().build());

    private FusionDiscovery() {
    }

    /** Los tipos de dato tienen que estar registrados antes de cargar un mundo: NeoForge los registra en su momento. */
    public static void initialize(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    /**
     * Clave de una pareja: especie y forma de cada parte ("cobblemon:charizard/normal>cobblemon:pikachu/normal"). Con
     * la forma porque una forma regional se ve distinta (Raichu de Alola).
     */
    public static String key(Pokemon head, Pokemon body) {
        return key(head.getForm(), body.getForm());
    }

    public static String key(FormData head, FormData body) {
        return part(head) + ">" + part(body);
    }

    private static String part(FormData form) {
        return form.getSpecies().getResourceIdentifier() + "/" + form.getName().toLowerCase(Locale.ROOT);
    }

    public static boolean isDiscovered(ServerPlayer player, String key) {
        return player.getData(REVEAL_ALL) || player.getData(DISCOVERED).contains(key);
    }

    /** Ver REVEAL_ALL. */
    public static void setRevealAll(ServerPlayer player, boolean reveal) {
        player.setData(REVEAL_ALL, reveal);
    }

    /** Apunta una pareja como descubierta (si ya lo estaba, no hace nada). */
    public static void register(ServerPlayer player, String key) {
        List<String> discovered = player.getData(DISCOVERED);
        if (discovered.contains(key)) {
            return;
        }
        // Lista nueva en vez de cambiar la guardada: lo que devuelve el codec al cargar puede no admitir cambios
        List<String> updated = new ArrayList<>(discovered);
        updated.add(key);
        player.setData(DISCOVERED, List.copyOf(updated));
    }

    /**
     * Olvida todas las fusiones descubiertas del jugador (vuelven a salir en silueta) y devuelve cuántas eran. Solo para
     * desarrollo: para volver a grabar la revelación de una pareja. No se vuelve a llamar a registerExisting, así que
     * las fusiones que ya tiene en el equipo o el PC tampoco cuentan hasta que las vuelva a crear.
     */
    public static int forgetAll(ServerPlayer player) {
        int count = player.getData(DISCOVERED).size();
        player.setData(DISCOVERED, List.of());
        // Si no, con reveal puesto seguirían sin salir en silueta, que es para lo que se usa forget
        setRevealAll(player, false);
        return count;
    }

    /**
     * Una vez por jugador: las fusiones que ya tiene en el equipo o el PC cuentan como descubiertas (se crearon antes
     * de que existiera el registro y, si no, no saldrían en su Fusion Album).
     */
    public static void registerExisting(ServerPlayer player) {
        if (player.getData(EXISTING_REGISTERED)) {
            return;
        }
        player.setData(EXISTING_REGISTERED, true);
        try {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                registerFusion(player, pokemon);
            }
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) {
                registerFusion(player, pokemon);
            }
        } catch (RuntimeException e) {
            // No es grave: solo faltarían en su Fusion Album. Que nunca impida entrar al mundo
            Fusionmon.LOGGER.warn("No se han podido apuntar las fusiones de {}", player.getName().getString(), e);
        }
    }

    private static void registerFusion(ServerPlayer player, Pokemon pokemon) {
        FormData head = FusionData.headForm(pokemon);
        FormData body = FusionData.bodyForm(pokemon);
        if (head != null && body != null) {
            register(player, key(head, body));
        }
    }

    /** Abre el Fusion Album del jugador: sus fusiones descubiertas, de la más reciente a la más antigua. */
    public static void open(ServerPlayer player) {
        List<String> discovered = new ArrayList<>(player.getData(DISCOVERED));
        Collections.reverse(discovered);
        PacketDistributor.sendToPlayer(player, new OpenFusionAlbumPayload(discovered));
    }
}
