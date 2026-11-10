package com.arnau.fusionmon.advancement;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;
import java.util.Optional;

/**
 * Criterio de logro "fusionmon:fusion": se cumple cuando el jugador fusiona, invierte, separa, descubre o consigue una
 * fusión. Con él van los logros de data/fusionmon/advancement, y cualquier datapack o modpack puede hacer los suyos;
 * FTB Quests tiene tareas de tipo "advancement", así que las misiones de Fusionmon se apoyan en estos logros sin que
 * Fusionmon dependa de FTB Quests.
 *
 * Condiciones (todas opcionales):
 *   "action": "fuse" | "reverse" | "unfuse" | "discover" | "obtain"
 *   "min_discovered": parejas en el Fusion Album, como mínimo (se comprueba en cualquier acción)
 *   "head" / "body": especie de la cabeza o del cuerpo ("charizard" o "cobblemon:charizard")
 */
public class FusionTrigger extends SimpleCriterionTrigger<FusionTrigger.Instance> {

    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Fusionmon.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, FusionTrigger> FUSION =
            TRIGGERS.register("fusion", FusionTrigger::new);

    public static void initialize(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }

    public enum Action implements StringRepresentable {
        FUSE, REVERSE, UNFUSE, DISCOVER, OBTAIN;

        public static final Codec<Action> CODEC = StringRepresentable.fromEnum(Action::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** Comprueba los logros con este criterio del jugador. head/body pueden ser null (si no se conocen). */
    public static void trigger(ServerPlayer player, Action action, FormData head, FormData body, int discovered) {
        FUSION.get().trigger(player, instance -> instance.matches(action, head, body, discovered));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<Action> action,
                           Optional<Integer> minDiscovered, Optional<String> head, Optional<String> body)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Action.CODEC.optionalFieldOf("action").forGetter(Instance::action),
                Codec.INT.optionalFieldOf("min_discovered").forGetter(Instance::minDiscovered),
                Codec.STRING.optionalFieldOf("head").forGetter(Instance::head),
                Codec.STRING.optionalFieldOf("body").forGetter(Instance::body)
        ).apply(builder, Instance::new));

        boolean matches(Action done, FormData headForm, FormData bodyForm, int discovered) {
            return action.map(a -> a == done).orElse(true)
                    && minDiscovered.map(min -> discovered >= min).orElse(true)
                    && head.map(name -> isSpecies(headForm, name)).orElse(true)
                    && body.map(name -> isSpecies(bodyForm, name)).orElse(true);
        }

        private static boolean isSpecies(FormData form, String name) {
            if (form == null) {
                return false;
            }
            String id = form.getSpecies().getResourceIdentifier().toString();
            String wanted = name.toLowerCase(Locale.ROOT);
            return id.equals(wanted) || form.getSpecies().getResourceIdentifier().getPath().equals(wanted);
        }
    }
}
