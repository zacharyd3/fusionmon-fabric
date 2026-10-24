package com.arnau.fusionmon.network;

import com.arnau.fusionmon.FusionConfig;
import com.arnau.fusionmon.client.ClientPayloads;
import com.arnau.fusionmon.fusion.FusionDiscovery;
import com.arnau.fusionmon.fusion.FusionSelection;
import com.arnau.fusionmon.fusion.FusionWorldAnimation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registra los mensajes propios de Fusionmon. En NeoForge los dos sentidos se registran aquí (parte común), también
 * los receptores del cliente: van en ClientPayloads.
 */
public final class ModNetworking {

    /** Versión de los mensajes: subirla si cambia alguno de forma incompatible. */
    private static final String PROTOCOL = "1";

    private ModNetworking() {
    }

    public static void initialize(IEventBus modBus) {
        modBus.addListener(ModNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ModNetworking::onLogout);
        NeoForge.EVENT_BUS.addListener(ModNetworking::onLogin);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional(): como en Fabric, que falte uno de estos mensajes en el otro lado (un servidor sin Fusionmon, una
        // versión vieja) no impide conectarse; antes de mandar los que puedan faltar se mira con hasChannel.
        // NeoForge ejecuta los receptores en el hilo principal: se puede tocar el equipo directamente
        PayloadRegistrar registrar = event.registrar(PROTOCOL).optional();

        // Receptores del cliente: siempre con una lambda que llama a ClientPayloads, nunca ClientPayloads::metodo. En
        // un servidor dedicado no existen las clases de cliente, y la lambda solo carga ClientPayloads si se ejecuta
        // (cosa que en un servidor no pasa: estos mensajes solo llegan al cliente)
        registrar.playToClient(OpenFusionScreenPayload.TYPE, OpenFusionScreenPayload.CODEC,
                (payload, context) -> ClientPayloads.openFusionScreen(payload));
        registrar.playToClient(OpenUnfuseScreenPayload.TYPE, OpenUnfuseScreenPayload.CODEC,
                (payload, context) -> ClientPayloads.openUnfuseScreen(payload));
        registrar.playToClient(OpenFusionAlbumPayload.TYPE, OpenFusionAlbumPayload.CODEC,
                (payload, context) -> ClientPayloads.openFusionAlbum(payload));
        registrar.playToClient(FusionResultPayload.TYPE, FusionResultPayload.CODEC,
                (payload, context) -> ClientPayloads.fusionResult(payload));
        registrar.playToClient(FusionRulesPayload.TYPE, FusionRulesPayload.CODEC,
                (payload, context) -> ClientPayloads.rules(payload));

        registrar.playToServer(FusionChoicePayload.TYPE, FusionChoicePayload.CODEC,
                (payload, context) -> FusionSelection.handleChoice((ServerPlayer) context.player(), payload));
        registrar.playToServer(UnfuseChoicePayload.TYPE, UnfuseChoicePayload.CODEC,
                (payload, context) -> FusionSelection.handleUnfuseChoice((ServerPlayer) context.player(), payload));
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FusionSelection.forget(player);
            // Una fusión animándose en el mundo se hace ya: el inyector está gastado
            FusionWorldAnimation.finishFor(player);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Las fusiones creadas antes de que existiera el registro cuentan como descubiertas (una vez por jugador)
        FusionDiscovery.registerExisting(player);
        // Las opciones del servidor que el cliente necesita (un cliente con una versión vieja no lo conoce)
        if (player.connection.hasChannel(FusionRulesPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new FusionRulesPayload(FusionConfig.configuredStatBonusPercent(),
                    FusionConfig.forceColorsMode()));
        }
    }
}
