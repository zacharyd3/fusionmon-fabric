package com.arnau.fusionmon.compat.kubejs;

import com.arnau.fusionmon.api.FusionmonEvent;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Integración con KubeJS (opcional). KubeJS carga esta clase solo si está instalado: la apunta
 * src/main/resources/kubejs.plugins.txt (sin KubeJS nadie lee ese archivo, y estas clases nunca se cargan).
 *
 * Reenvía los eventos públicos de Fusionmon (api/FusionmonEvent) a los scripts de servidor como FusionmonEvents.*:
 *
 *   FusionmonEvents.beforeFuse(event => { if (event.bodySpecies == 'cobblemon:mewtwo') event.cancel() })
 *   FusionmonEvents.fused(event => event.player.tell(`¡${event.fusionName}!`))
 *
 * y añade el objeto Fusionmon con utilidades (Fusionmon.isFusion(pokemon)...). Ejemplos: docs/integrations.md.
 */
public class FusionmonKubeJSPlugin implements KubeJSPlugin {

    static final EventGroup GROUP = EventGroup.of("FusionmonEvents");

    static final EventHandler BEFORE_FUSE = GROUP.server("beforeFuse", () -> FusionmonKubeEvents.BeforeFuse.class)
            .hasResult();
    static final EventHandler BEFORE_UNFUSE = GROUP.server("beforeUnfuse",
            () -> FusionmonKubeEvents.BeforeUnfuse.class).hasResult();
    static final EventHandler FUSED = GROUP.server("fused", () -> FusionmonKubeEvents.Fused.class);
    static final EventHandler UNFUSED = GROUP.server("unfused", () -> FusionmonKubeEvents.Unfused.class);
    static final EventHandler DISCOVERED = GROUP.server("discovered", () -> FusionmonKubeEvents.Discovered.class);
    static final EventHandler OBTAINED = GROUP.server("obtained", () -> FusionmonKubeEvents.Obtained.class);

    @Override
    public void init() {
        // Los eventos de Fusionmon van por el bus de NeoForge: aquí se escuchan y se pasan a los scripts (solo si
        // algún script escucha ese evento)
        NeoForge.EVENT_BUS.addListener(FusionmonEvent.BeforeFuse.class, event -> {
            if (BEFORE_FUSE.hasListeners()) {
                BEFORE_FUSE.post(ScriptType.SERVER, new FusionmonKubeEvents.BeforeFuse(event)).applyCancel(event);
            }
        });
        NeoForge.EVENT_BUS.addListener(FusionmonEvent.BeforeUnfuse.class, event -> {
            if (BEFORE_UNFUSE.hasListeners()) {
                BEFORE_UNFUSE.post(ScriptType.SERVER, new FusionmonKubeEvents.BeforeUnfuse(event)).applyCancel(event);
            }
        });
        NeoForge.EVENT_BUS.addListener(FusionmonEvent.Fused.class,
                event -> post(FUSED, new FusionmonKubeEvents.Fused(event)));
        NeoForge.EVENT_BUS.addListener(FusionmonEvent.Unfused.class,
                event -> post(UNFUSED, new FusionmonKubeEvents.Unfused(event)));
        NeoForge.EVENT_BUS.addListener(FusionmonEvent.Discovered.class,
                event -> post(DISCOVERED, new FusionmonKubeEvents.Discovered(event)));
        NeoForge.EVENT_BUS.addListener(FusionmonEvent.Obtained.class,
                event -> post(OBTAINED, new FusionmonKubeEvents.Obtained(event)));
    }

    private static void post(EventHandler handler, KubeEvent event) {
        if (handler.hasListeners()) {
            handler.post(ScriptType.SERVER, event);
        }
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("Fusionmon", FusionmonKubeUtils.class);
    }
}
