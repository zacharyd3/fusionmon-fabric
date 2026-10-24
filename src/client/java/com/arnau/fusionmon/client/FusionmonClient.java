package com.arnau.fusionmon.client;

import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.MixinHealth;
import com.arnau.fusionmon.client.model.FusionGraft;
import com.arnau.fusionmon.client.screen.FusionDexScreen;
import com.arnau.fusionmon.client.screen.SpeciesInspectorScreen;
import com.arnau.fusionmon.client.texture.FusionTextures;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Arranque de la parte de cliente (NeoForge solo la crea en el cliente; la común es Fusionmon). Los mensajes del
 * servidor (abrir pantallas, resultado de la fusión, opciones) los registra ModNetworking: ver ClientPayloads.
 */
@Mod(value = Fusionmon.MOD_ID, dist = Dist.CLIENT)
public class FusionmonClient {

	public FusionmonClient(IEventBus modBus) {
		// Al arrancar, sin servidor todavía: el modo colores del archivo del propio juego. En la preparación del
		// cliente, que llega cuando la parte común ya ha leído config/fusionmon.json
		modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(ServerRules::init));
		// Las opciones del servidor llegan al entrar (ClientPayloads.rules); al salir se olvidan
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ServerRules.reset());
		// Enganches de cliente que no se han podido aplicar (ver MixinHealth): al log, al terminar de cargar el juego y al
		// entrar en un mundo (algunas clases de Cobblemon se cargan solo entonces)
		modBus.addListener(FMLLoadCompleteEvent.class, event -> event.enqueueWork(MixinHealth::check));
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> MixinHealth.check());

		NeoForge.EVENT_BUS.addListener(FusionmonClient::registerCommands);
		modBus.addListener(FusionmonClient::registerReloadListeners);
	}

	/** Comandos de cliente: no pasan por el servidor ni necesitan trucos. NeoForge los pide al entrar en cada mundo. */
	private static void registerCommands(RegisterClientCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

		// Herramientas para afinar los visuales (ajustes finos de /fusionvisual, /fusioninspect): solo en desarrollo
		// (runClient); el inspector lee archivos que solo hay en el proyecto. /fusionviewer, también con creatorTools: el
		// visor enseña todas las fusiones sin jugar (la gracia es capturar, probar y ver qué sale), así que solo lo tiene
		// quien lo activa a mano
		boolean dev = Fusionmon.isDevelopment();

		// /fusionvisual colors|graft: elige cómo se ven las fusiones en este cliente (graft = cabeza sobre cuerpo, ver
		// FusionGraft; colors = la cabeza con los colores del cuerpo), por si alguna sale rara o va lenta.
		LiteralArgumentBuilder<CommandSourceStack> visual = Commands.literal("fusionvisual")
				.then(Commands.literal("colors").executes(context -> {
					FusionGraft.setEnabled(false);
					feedback(context, Component.translatable("command.fusionmon.visual.colors"));
					return 1;
				}))
				.then(Commands.literal("graft").executes(context -> {
					FusionGraft.setEnabled(true);
					// Con el modo colores forzado (config/fusionmon.json del juego o del servidor) no cambia nada
					feedback(context, Component.translatable(FusionGraft.isForcedColors()
							? "command.fusionmon.visual.forced" : "command.fusionmon.visual.graft"));
					return 1;
				}));
		if (dev) {
			// /fusionvisual tail|decor|top on|off, align pivot|skull|base: para comparar reglas del graft
			visual.then(Commands.literal("tail")
							.then(Commands.literal("on").executes(context -> {
								FusionGraft.setTails(true);
								feedback(context, Component.translatable("command.fusionmon.visual.tail.on"));
								return 1;
							}))
							.then(Commands.literal("off").executes(context -> {
								FusionGraft.setTails(false);
								feedback(context, Component.translatable("command.fusionmon.visual.tail.off"));
								return 1;
							})))
					.then(Commands.literal("decor")
							.then(Commands.literal("on").executes(context -> {
								FusionGraft.setDecorations(true);
								feedback(context, Component.translatable("command.fusionmon.visual.decor.on"));
								return 1;
							}))
							.then(Commands.literal("off").executes(context -> {
								FusionGraft.setDecorations(false);
								feedback(context, Component.translatable("command.fusionmon.visual.decor.off"));
								return 1;
							})))
					// /fusionvisual top on|off: cuerpos sin cabeza (Voltorb, Lunatone...) con los complementos de la cabeza o en
					// modo colores
					.then(Commands.literal("top")
							.then(Commands.literal("on").executes(context -> {
								FusionGraft.setTops(true);
								feedback(context, Component.translatable("command.fusionmon.visual.top.on"));
								return 1;
							}))
							.then(Commands.literal("off").executes(context -> {
								FusionGraft.setTops(false);
								feedback(context, Component.translatable("command.fusionmon.visual.top.off"));
								return 1;
							})))
					// /fusionvisual align pivot|skull|base: cabeza pegada pivote con pivote, cráneo por cráneo o base con
					// base (ver FusionGraft.Align)
					.then(Commands.literal("align")
							.then(Commands.literal("pivot").executes(context -> {
								FusionGraft.setAlign(FusionGraft.Align.PIVOT);
								feedback(context, Component.translatable("command.fusionmon.visual.align.pivot"));
								return 1;
							}))
							.then(Commands.literal("skull").executes(context -> {
								FusionGraft.setAlign(FusionGraft.Align.SKULL);
								feedback(context, Component.translatable("command.fusionmon.visual.align.skull"));
								return 1;
							}))
							.then(Commands.literal("base").executes(context -> {
								FusionGraft.setAlign(FusionGraft.Align.BASE);
								feedback(context, Component.translatable("command.fusionmon.visual.align.base"));
								return 1;
							})));
		}
		dispatcher.register(visual);

		if (dev || Fusionmon.recordingTools()) {
			// /fusionviewer: visor de TODAS las fusiones (FusionDexScreen), para probar visuales y, con creatorTools en
			// config/fusionmon.json, para que los creadores de contenido hagan renders (P). No se llama /fusionalbum:
			// ese es el del jugador, con solo sus fusiones descubiertas (y un comando de cliente taparía al del servidor).
			// La pantalla se abre en la siguiente vuelta del bucle del juego: al terminar un comando, Minecraft cierra el
			// chat, y cerraría también una pantalla abierta aquí mismo
			dispatcher.register(Commands.literal("fusionviewer").executes(context -> {
				Minecraft client = Minecraft.getInstance();
				client.tell(() -> client.setScreen(new FusionDexScreen()));
				return 1;
			}));
		}
		if (dev) {
			// /fusioninspect: inspector de especies (SpeciesInspectorScreen), para revisar una a una lo que detecta el
			// graft
			dispatcher.register(Commands.literal("fusioninspect").executes(context -> {
				Minecraft client = Minecraft.getInstance();
				client.tell(() -> client.setScreen(new SpeciesInspectorScreen()));
				return 1;
			}));
		}
	}

	/** Mensaje en el chat del propio jugador (en un comando de cliente, sendSuccess solo lo pinta en su chat). */
	private static void feedback(CommandContext<CommandSourceStack> context, Component message) {
		context.getSource().sendSuccess(() -> message, false);
	}

	private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
		// Las texturas de fusión se generan a partir de las de Cobblemon: si se recargan los recursos
		// (F3+T, otro resource pack), hay que volver a generarlas
		event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
			FusionTextures.clear();
			// Con los modelos nuevos (otro resource pack...) una pareja que falló puede ir bien
			FusionGraft.clearFailures();
		});
	}
}
