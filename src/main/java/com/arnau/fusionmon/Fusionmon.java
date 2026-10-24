package com.arnau.fusionmon;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.arnau.fusionmon.command.FusionCommands;
import com.arnau.fusionmon.fusion.FusionAspects;
import com.arnau.fusionmon.fusion.FusionDiscovery;
import com.arnau.fusionmon.fusion.FusionLevelUp;
import com.arnau.fusionmon.fusion.FusionStatProvider;
import com.arnau.fusionmon.fusion.FusionWorldAnimation;
import com.arnau.fusionmon.item.ModItems;
import com.arnau.fusionmon.network.ModNetworking;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider;
import com.cobblemon.mod.common.api.pokemon.stats.StatProvider;

/**
 * Arranque del mod (parte común). NeoForge crea esta clase al cargar el juego, después de Cobblemon (ordering AFTER en
 * neoforge.mods.toml); la parte de cliente es FusionmonClient.
 */
@Mod(Fusionmon.MOD_ID)
public class Fusionmon {
	public static final String MOD_ID = "fusionmon";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** modBus: el bus de eventos de carga del mod (registros, mensajes); los del juego van por NeoForge.EVENT_BUS. */
	public Fusionmon(IEventBus modBus) {
		// Opciones del servidor (config/fusionmon.json): antes de nada que calcule stats
		FusionConfig.load();
		ModItems.initialize(modBus);
		FusionCommands.register();
		ModNetworking.initialize(modBus);
		// Registro de fusiones descubiertas de cada jugador (dato guardado con su partida)
		FusionDiscovery.initialize(modBus);
		// Fusión animada en el mundo cuando los dos Pokémon están fuera (avanza en cada tick del servidor)
		FusionWorldAnimation.initialize();
		// Lo que se engancha a Cobblemon, en el hilo principal y uno detrás de otro: NeoForge construye los mods a la vez
		// en varios hilos (Fabric, uno a uno), y otro addon de Cobblemon podría estar apuntando lo suyo en las mismas
		// listas en ese momento. Sigue siendo mucho antes de que exista ningún Pokémon
		modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(Fusionmon::hookCobblemon));

		// Antes de cargar los mundos (y con ellos ningún Pokémon), como SERVER_STARTING en Fabric
		NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, event -> checkStatProvider());
		// Enganches opcionales que no se han podido aplicar (ver MixinHealth): al log
		NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> MixinHealth.check());
	}

	private static void hookCobblemon() {
		// Envuelve la calculadora de stats de Cobblemon para que las fusiones usen sus propios stats base
		Cobblemon.INSTANCE.setStatProvider(new FusionStatProvider(Cobblemon.INSTANCE.getStatProvider()));
		// Al subir de nivel, la fusión también aprende los movimientos del cuerpo
		FusionLevelUp.register();
		// Etiquetas (aspects) que dicen al cliente que un Pokémon es una fusión y quién es su cuerpo
		AspectProvider.Companion.register(new FusionAspects());
	}

	/**
	 * Otro mod puede cambiar la calculadora de stats de Cobblemon después de nosotros (un mod de stats, de dificultad...):
	 * la nuestra desaparecería y las fusiones pelearían con los stats de su cabeza, sin que nadie lo notase. Antes de
	 * arrancar el servidor se comprueba; si no es la nuestra, se envuelve la suya (las fusiones con nuestros stats, el
	 * resto con los del otro mod). Si el otro mod envolvió la nuestra, queda envuelta dos veces: no pasa nada.
	 */
	private static void checkStatProvider() {
		StatProvider current = Cobblemon.INSTANCE.getStatProvider();
		if (!(current instanceof FusionStatProvider)) {
			LOGGER.info("Fusionmon: otro mod ha cambiado la calculadora de stats de Cobblemon ({}); se envuelve para"
					+ " que las fusiones sigan con sus stats", current.getClass().getName());
			Cobblemon.INSTANCE.setStatProvider(new FusionStatProvider(current));
		}
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	/**
	 * Herramientas para grabar (/fusionmon spawn y forget en el servidor, para ops; /fusionviewer con renders en el
	 * cliente): en desarrollo (runClient/runServer), o con el jar publicado si "creatorTools": true en
	 * config/fusionmon.json (la forma documentada, para creadores de contenido y servidores; 0.1.3-beta). Siguen valiendo
	 * las de antes: el archivo config/fusionmon-devtools o el argumento de Java -Dfusionmon.devtools=true (CurseForge
	 * borra los argumentos al lanzar, por eso el archivo). Ningún jugador lo tiene sin ponerlo a mano: la gracia del mod
	 * es descubrir las fusiones jugando.
	 */
	public static boolean recordingTools() {
		return isDevelopment()
				|| FusionConfig.creatorTools()
				|| Boolean.getBoolean("fusionmon.devtools")
				|| Files.exists(FMLPaths.CONFIGDIR.get().resolve("fusionmon-devtools"));
	}

	/** En desarrollo (runClient/runServer), no con el jar publicado: lo que en Fabric era isDevelopmentEnvironment. */
	public static boolean isDevelopment() {
		return !FMLEnvironment.production;
	}
}
