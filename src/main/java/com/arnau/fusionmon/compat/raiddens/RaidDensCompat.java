package com.arnau.fusionmon.compat.raiddens;

import com.arnau.fusionmon.Fusionmon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Cobblemon Raid Dens (opcional). No hace falta código suyo: sus jefes se escriben en JSON con las propiedades de
 * Cobblemon, y "fusion_body" en "custom_properties" ya los convierte en fusiones (FusionRequest). Aquí solo se añade,
 * si Raid Dens está instalado, el datapack incluido en el jar (datapacks/raid_dens):
 *  - jefes de incursión fusionados en todos los niveles (data/fusionmon/raid/boss)
 *  - inyectores de fusión en los premios de las incursiones (modificador de botín de NeoForge)
 *
 * Sale en /datapack list como "fusionmon:datapacks/raid_dens" y se activa solo (también en mundos que ya existen);
 * quien no lo quiera: /datapack disable "fusionmon:datapacks/raid_dens".
 */
public final class RaidDensCompat {

    public static final String MOD_ID = "cobblemonraiddens";

    private RaidDensCompat() {
    }

    public static void initialize(IEventBus modBus) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        modBus.addListener(AddPackFindersEvent.class, event -> {
            if (event.getPackType() == PackType.SERVER_DATA) {
                event.addPackFinders(Fusionmon.id("datapacks/raid_dens"), PackType.SERVER_DATA,
                        Component.literal("Fusionmon: fusion raid bosses"), PackSource.BUILT_IN, false,
                        Pack.Position.TOP);
            }
        });
    }
}
