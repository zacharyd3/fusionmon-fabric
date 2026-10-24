package com.arnau.fusionmon.item;

import com.arnau.fusionmon.Fusionmon;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Objetos del mod. En NeoForge los registros del juego están cerrados cuando se construye el mod: se apuntan aquí y
 * NeoForge los registra en su momento. Hasta entonces FUSION_INJECTOR no tiene objeto: usar get() solo en el juego.
 */
public class ModItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Fusionmon.MOD_ID);

    public static final DeferredItem<FusionInjectorItem> FUSION_INJECTOR =
            ITEMS.registerItem("fusion_injector", FusionInjectorItem::new, new Item.Properties());

    public static void initialize(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(FUSION_INJECTOR.get());
        }
    }
}
