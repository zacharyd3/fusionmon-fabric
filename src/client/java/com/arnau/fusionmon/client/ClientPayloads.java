package com.arnau.fusionmon.client;

import com.arnau.fusionmon.client.screen.FusionAlbumScreen;
import com.arnau.fusionmon.client.screen.FusionAnimationScreen;
import com.arnau.fusionmon.client.screen.FusionConfirmScreen;
import com.arnau.fusionmon.client.screen.UnfuseConfirmScreen;
import com.arnau.fusionmon.network.FusionResultPayload;
import com.arnau.fusionmon.network.FusionRulesPayload;
import com.arnau.fusionmon.network.OpenFusionAlbumPayload;
import com.arnau.fusionmon.network.OpenFusionScreenPayload;
import com.arnau.fusionmon.network.OpenUnfuseScreenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Lo que hace el cliente con cada mensaje del servidor. Los registra ModNetworking (en NeoForge, los dos sentidos se
 * registran en la parte común) y NeoForge los ejecuta en el hilo del cliente. Solo cliente: ModNetworking llega aquí
 * con lambdas, para que un servidor dedicado no cargue nunca esta clase.
 */
public final class ClientPayloads {

    private ClientPayloads() {
    }

    // El servidor pide abrir una pantalla de confirmación
    public static void openFusionScreen(OpenFusionScreenPayload payload) {
        Minecraft.getInstance().setScreen(new FusionConfirmScreen(payload));
    }

    public static void openUnfuseScreen(OpenUnfuseScreenPayload payload) {
        Minecraft.getInstance().setScreen(new UnfuseConfirmScreen(payload));
    }

    // El Fusion Album del jugador (Shift + clic con el inyector, o /fusionalbum)
    public static void openFusionAlbum(OpenFusionAlbumPayload payload) {
        Minecraft.getInstance().setScreen(new FusionAlbumScreen(payload.discovered()));
    }

    // La fusión aceptada ya está hecha: animación de pantalla, o ninguna si se ve en el mundo
    public static void fusionResult(FusionResultPayload payload) {
        FusionAnimationScreen.onResult(Minecraft.getInstance(), payload.inWorld());
    }

    // Opciones del servidor (bonus de stats): al entrar; al salir se olvidan
    public static void rules(FusionRulesPayload payload) {
        ServerRules.set(payload.statBonusPercent(), payload.forceColorsMode());
    }

    /**
     * Si se puede mandar este mensaje al servidor: hay conexión y el servidor lo conoce (un servidor sin Fusionmon, o
     * una pantalla que se cierra después de desconectarse). Lo que en Fabric era ClientPlayNetworking.canSend.
     */
    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }
}
