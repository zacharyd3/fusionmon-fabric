package com.arnau.fusionmon.client.model.atlas;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.BoneInfo;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Declared;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Input;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Kind;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Role;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Skeleton;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.BimanualSwingAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.BipedWalkAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PoseAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.QuadrupedWalkAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.SingleBoneLookAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.WingFlapIdleAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockPoseAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Pose;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * El atlas de esqueletos en el juego: prepara los datos de cada modelo para SkeletonClassifier (huesos en reposo,
 * a qué mira, qué declaran sus animaciones de código y cómo se mueve cada hueso al estar quieto, andar y volar) y
 * guarda el resultado. Las animaciones se evalúan con el propio motor de Cobblemon (BedrockAnimation.run), el mismo
 * que pinta el Pokémon: el Molang de cualquier modelo o resource pack funciona igual que en el juego.
 *
 * Analizar un modelo cuesta unos milisegundos (unas decenas de instantes de sus animaciones) y se hace una vez por
 * modelo, cuando FusionGraft lo pide por primera vez. Se hace en el hilo de render: el modelo se deja en reposo al
 * terminar (setDefault), igual que lo deja Cobblemon antes de animarlo para pintarlo.
 *
 * Si algo falla (un modelo raro, una versión de Cobblemon con otras clases), el esqueleto queda vacío y FusionGraft
 * sigue con los nombres.
 */
public final class SkeletonAtlas {

    /**
     * Cuánto se fía FusionGraft del atlas (/fusionvisual atlas ...). Los resource packs (SkeletonOverrides) mandan
     * siempre salvo con OFF.
     */
    public enum Mode {
        /** Solo los nombres de los huesos, como antes del atlas. */
        OFF,
        /**
         * Por defecto: los nombres primero y el atlas para lo que no encuentran. Del movimiento solo se usa lo que
         * simulado sobre todos los modelos acierta casi siempre (cabeza, patas, alas); brazos y colas, solo si los
         * declara el poser o un resource pack (ver docs/species/skeleton-atlas.md).
         */
        FILL,
        /** El atlas primero (todo lo que deduce) y los nombres para lo que no encuentra. Para probar. */
        PREFER
    }

    /** Instantes por animación. */
    private static final int SAMPLES = 24;
    /** Duración que se recorre de las animaciones sin duración (solo Molang, en bucle). */
    private static final float DEFAULT_LENGTH = 2F;

    private static Mode mode = Mode.FILL;
    private static final Map<PosableModel, Skeleton> SKELETONS = new WeakHashMap<>();
    private static final Map<PosableModel, ResourceLocation> MODEL_NAMES = new WeakHashMap<>();

    private SkeletonAtlas() {
    }

    public static Mode getMode() {
        return mode;
    }

    public static void setMode(Mode value) {
        mode = value;
    }

    /** Al recargar recursos: los modelos son otros. */
    public static void clear() {
        SKELETONS.clear();
        MODEL_NAMES.clear();
    }

    /** El esqueleto de un modelo (vacío con el atlas apagado o si no se ha podido analizar). */
    public static Skeleton of(PosableModel model) {
        if (mode == Mode.OFF || model == null) {
            return Skeleton.EMPTY;
        }
        return analyzed(model);
    }

    /** El esqueleto aunque el atlas esté apagado (para el inspector y la exportación). */
    public static Skeleton analyzed(PosableModel model) {
        Skeleton cached = SKELETONS.get(model);
        if (cached != null) {
            return cached;
        }
        Skeleton skeleton;
        try {
            skeleton = SkeletonClassifier.classify(input(model), SkeletonOverrides.get(modelName(model)));
        } catch (Throwable e) {
            // Throwable: con otra versión de Cobblemon una clase o un método que falta es un LinkageError, no una
            // RuntimeException, y aquí estamos en el hilo de render
            FusionSafety.failed("el atlas de esqueletos (" + modelName(model) + ")", e);
            skeleton = Skeleton.EMPTY;
        } finally {
            resetQuietly(model);
        }
        SKELETONS.put(model, skeleton);
        return skeleton;
    }

    /**
     * Nombre del modelo (.geo) de un poser ya creado, como "cobblemon:charizard.geo", o null. Cobblemon guarda cada
     * poser por la pareja (poser, modelo) en el resolver de su especie: se busca ahí.
     */
    public static ResourceLocation modelName(PosableModel model) {
        if (MODEL_NAMES.containsKey(model)) {
            return MODEL_NAMES.get(model);
        }
        ResourceLocation found = null;
        try {
            search:
            for (VaryingRenderableResolver resolver : VaryingModelRepository.INSTANCE.getVariations().values()) {
                for (Map.Entry<kotlin.Pair<ResourceLocation, ResourceLocation>, PosableModel> entry
                        : resolver.getPosers().entrySet()) {
                    if (entry.getValue() == model) {
                        found = entry.getKey().getSecond();
                        break search;
                    }
                }
            }
        } catch (Throwable e) {
            FusionSafety.failed("el nombre de un modelo para el atlas", e);
        }
        MODEL_NAMES.put(model, found);
        return found;
    }

    /** Lo que sabe el atlas de un modelo, en una línea (para el inspector). */
    public static String summary(PosableModel model) {
        Skeleton skeleton = analyzed(model);
        StringBuilder text = new StringBuilder();
        text.append("head ").append(skeleton.head() == null ? "-" : skeleton.head() + " [" + source(skeleton.headSource()) + "]");
        for (Role role : List.of(Role.LEG, Role.ARM, Role.WING, Role.TAIL)) {
            List<SkeletonClassifier.Part> parts = skeleton.of(role);
            text.append(" · ").append(role.name().toLowerCase(java.util.Locale.ROOT)).append(' ');
            if (parts.isEmpty()) {
                text.append(skeleton.corrected().contains(role) ? "none [pack]" : "-");
                continue;
            }
            List<String> names = new ArrayList<>();
            for (SkeletonClassifier.Part part : parts) {
                names.add(part.bone() + " [" + source(part.source()) + "]");
            }
            text.append(String.join(", ", names));
        }
        return text.toString();
    }

    private static String source(SkeletonClassifier.Source source) {
        return source == null ? "?" : switch (source) {
            case OVERRIDE -> "pack";
            case DECLARED -> "declared";
            case LOOK -> "look";
            case MOTION -> "motion";
        };
    }

    /**
     * Escribe el atlas de todos los modelos base (los de cada especie sin aspects) en un JSON con el mismo formato que
     * las correcciones de los resource packs (/fusionatlas export): para comparar con tools/skeleton-atlas, con otra
     * versión de Cobblemon, o como punto de partida para corregir un modelo.
     *
     * @return cuántos modelos se han escrito
     */
    public static int export(java.nio.file.Path file) throws java.io.IOException {
        com.google.gson.JsonArray all = new com.google.gson.JsonArray();
        java.util.Set<PosableModel> done = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<ResourceLocation, VaryingRenderableResolver> entry
                : new java.util.TreeMap<>(VaryingModelRepository.INSTANCE.getVariations()).entrySet()) {
            PosableModel model;
            try {
                model = entry.getValue().getPoser(new FloatingState());
            } catch (Throwable e) {
                continue;
            }
            if (!done.add(model)) {
                continue;
            }
            Skeleton skeleton = analyzed(model);
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            com.google.gson.JsonArray models = new com.google.gson.JsonArray();
            ResourceLocation name = modelName(model);
            models.add(name == null ? entry.getKey().toString() : name.toString());
            json.add("models", models);
            json.addProperty("species", entry.getKey().toString());
            json.addProperty("head", skeleton.head() == null ? "" : skeleton.head());
            json.add("legs", bones(skeleton, Role.LEG));
            json.add("arms", bones(skeleton, Role.ARM));
            json.add("wings", bones(skeleton, Role.WING));
            json.add("tail", bones(skeleton, Role.TAIL));
            json.addProperty("summary", summary(model));
            all.add(json);
        }
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting()
                .disableHtmlEscaping().create().toJson(all), java.nio.charset.StandardCharsets.UTF_8);
        return all.size();
    }

    private static com.google.gson.JsonArray bones(Skeleton skeleton, Role role) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (SkeletonClassifier.Part part : skeleton.of(role)) {
            array.add(part.bone());
        }
        return array;
    }

    // ---- Datos del modelo ----

    private static Input input(PosableModel model) {
        model.setDefault();
        Map<ModelPart, String> names = names(model);
        List<BoneInfo> bones = bones(model, names);

        Map<String, Collection<String>> poseTypes = new LinkedHashMap<>();
        Set<String> look = new LinkedHashSet<>();
        Map<String, Declared> declared = new LinkedHashMap<>();
        for (Map.Entry<String, Pose> entry : model.getPoses().entrySet()) {
            List<String> types = new ArrayList<>();
            for (PoseType type : entry.getValue().getPoseTypes()) {
                types.add(type.name());
            }
            poseTypes.put(entry.getKey(), types);
            for (PoseAnimation animation : entry.getValue().getAnimations()) {
                declare(animation, names, look, declared);
            }
        }

        FloatingState state = new FloatingState();
        state.setCurrentModel(model);
        Map<String, float[][]> stand = sample(model, state, SkeletonClassifier.pickPose(poseTypes, Kind.STAND), names);
        Map<String, float[][]> walk = sample(model, state, SkeletonClassifier.pickPose(poseTypes, Kind.WALK), names);
        Map<String, float[][]> fly = sample(model, state, SkeletonClassifier.pickPose(poseTypes, Kind.FLY), names);
        return new Input(bones, look, declared, stand, walk, fly);
    }

    /** Hueso → su nombre, de todos los que el poser conoce (los que pueden nombrar sus animaciones). */
    private static Map<ModelPart, String> names(PosableModel model) {
        Map<ModelPart, String> names = new IdentityHashMap<>();
        for (Map.Entry<String, ModelPart> entry : model.getRelevantPartsByName().entrySet()) {
            names.putIfAbsent(entry.getValue(), entry.getKey());
        }
        return names;
    }

    /** Lo que dicen las animaciones de una pose: a qué hueso mira y qué extremidades mueve el código. */
    private static void declare(PoseAnimation animation, Map<ModelPart, String> names, Set<String> look,
                                Map<String, Declared> declared) {
        if (animation instanceof SingleBoneLookAnimation lookAnimation) {
            addName(lookAnimation.getBone(), names, look);
        } else if (animation instanceof BipedWalkAnimation biped) {
            put(biped.getLeftLeg(), names, declared, new Declared(Role.LEG, 0));
            put(biped.getRightLeg(), names, declared, new Declared(Role.LEG, 0));
        } else if (animation instanceof QuadrupedWalkAnimation quadruped) {
            put(quadruped.getLegFrontLeft(), names, declared, new Declared(Role.LEG, -1));
            put(quadruped.getLegFrontRight(), names, declared, new Declared(Role.LEG, -1));
            put(quadruped.getLegBackLeft(), names, declared, new Declared(Role.LEG, 1));
            put(quadruped.getLegBackRight(), names, declared, new Declared(Role.LEG, 1));
        } else if (animation instanceof BimanualSwingAnimation arms) {
            put(arms.getLeftArm(), names, declared, new Declared(Role.ARM, 0));
            put(arms.getRightArm(), names, declared, new Declared(Role.ARM, 0));
        } else if (animation instanceof WingFlapIdleAnimation wings) {
            put(wings.getLeftWing(), names, declared, new Declared(Role.WING, 0));
            put(wings.getRightWing(), names, declared, new Declared(Role.WING, 0));
        }
    }

    private static void addName(Bone bone, Map<ModelPart, String> names, Set<String> into) {
        if ((Object) bone instanceof ModelPart part && names.containsKey(part)) {
            into.add(names.get(part));
        }
    }

    private static void put(Bone bone, Map<ModelPart, String> names, Map<String, Declared> declared, Declared value) {
        if ((Object) bone instanceof ModelPart part && names.containsKey(part)) {
            declared.putIfAbsent(names.get(part), value);
        }
    }

    /**
     * Los huesos en reposo, en el marco del atlas: bloques, Y hacia arriba (en Minecraft los modelos van con Y hacia
     * abajo), la cara hacia -Z. Los hijos "%hueso%n" que Cobblemon crea para los cubos girados no son huesos: sus
     * cubos cuentan como del padre (ver FusionGraft.ownBox).
     */
    private static List<BoneInfo> bones(PosableModel model, Map<ModelPart, String> names) {
        List<BoneInfo> bones = new ArrayList<>();
        if (!((Object) model.getRootPart() instanceof ModelPart root)) {
            return bones;
        }
        // Cajas: ModelPart.visit da cada cubo con la postura de su hueso y su ruta ("/body/torso", "" la raíz)
        Map<String, float[]> boxes = new HashMap<>();
        Map<String, Float> volumes = new HashMap<>();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            String owner = ownerOf(path);
            float[] box = boxes.computeIfAbsent(owner, key -> new float[]{Float.POSITIVE_INFINITY,
                    Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY});
            Matrix4f matrix = pose.pose();
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = matrix.transformPosition(new Vector3f(
                        ((corner & 1) != 0 ? cube.maxX : cube.minX) / 16F,
                        ((corner & 2) != 0 ? cube.maxY : cube.minY) / 16F,
                        ((corner & 4) != 0 ? cube.maxZ : cube.minZ) / 16F));
                // Y hacia arriba
                point.y = -point.y;
                box[0] = Math.min(box[0], point.x);
                box[1] = Math.min(box[1], point.y);
                box[2] = Math.min(box[2], point.z);
                box[3] = Math.max(box[3], point.x);
                box[4] = Math.max(box[4], point.y);
                box[5] = Math.max(box[5], point.z);
            }
            volumes.merge(owner, (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ)
                    / 4096F, Float::sum);
        });
        PoseStack stack = new PoseStack();
        // La raíz del poser es un hueso con nombre ("charizard"), pero ModelPart.visit no lo da: se busca entre los
        // conocidos, y si no está, uno que no choque con los del modelo
        collect(root, names.getOrDefault(root, "#root"), null, "", stack, boxes, volumes, bones);
        return bones;
    }

    /** La ruta del hueso dueño de un cubo: sin los "%hueso%n" del final. */
    private static String ownerOf(String path) {
        int cut = path.length();
        while (cut > 0) {
            int slash = path.lastIndexOf('/', cut - 1);
            if (slash < 0 || !path.startsWith("%", slash + 1)) {
                break;
            }
            cut = slash;
        }
        return path.substring(0, cut);
    }

    private static void collect(ModelPart part, String name, String parent, String path, PoseStack stack,
                                Map<String, float[]> boxes, Map<String, Float> volumes, List<BoneInfo> bones) {
        stack.pushPose();
        part.translateAndRotate(stack);
        Vector3f pivot = stack.last().pose().transformPosition(new Vector3f());
        float[] box = boxes.get(path);
        bones.add(new BoneInfo(name, parent, new float[]{pivot.x, -pivot.y, pivot.z}, box,
                volumes.getOrDefault(path, 0F)));
        for (Map.Entry<String, Bone> child : ((Bone) (Object) part).getChildren().entrySet()) {
            if (child.getKey().startsWith("%") || !((Object) child.getValue() instanceof ModelPart childPart)) {
                continue;
            }
            collect(childPart, child.getKey(), name, path + "/" + child.getKey(), stack, boxes, volumes, bones);
        }
        stack.popPose();
    }

    /**
     * Giro de cada hueso (grados, respecto al reposo) en SAMPLES instantes de las animaciones Bedrock de una pose.
     * Solo las de la propia pose sin condición o cuya condición se cumple en un estado suelto (las de montar o llevar
     * objeto no): las animaciones de código (mirar, andar en código) ya se han leído en declare.
     */
    private static Map<String, float[][]> sample(PosableModel model, FloatingState state, String poseName,
                                                 Map<ModelPart, String> names) {
        Map<String, float[][]> result = new HashMap<>();
        Pose pose = poseName == null ? null : model.getPoses().get(poseName);
        if (pose == null) {
            return result;
        }
        List<BedrockAnimation> animations = new ArrayList<>();
        float length = 0;
        for (PoseAnimation animation : pose.getAnimations()) {
            if (!(animation instanceof BedrockPoseAnimation bedrock)) {
                continue;
            }
            try {
                if (!Boolean.TRUE.equals(animation.getCondition().invoke(state))) {
                    continue;
                }
            } catch (RuntimeException e) {
                // Una condición que necesita entidad: esa animación no es la de andar a secas
                continue;
            }
            animations.add(bedrock.getAnimation());
            double own = bedrock.getAnimation().getAnimationLength();
            length = Math.max(length, own > 0 ? (float) own : DEFAULT_LENGTH);
        }
        if (animations.isEmpty()) {
            return result;
        }
        state.setPose(poseName);

        Map<String, ModelPart> parts = model.getRelevantPartsByName();
        // El reposo de cada hueso, para restarlo
        model.setDefault();
        Map<ModelPart, float[]> rest = new IdentityHashMap<>();
        for (ModelPart part : parts.values()) {
            rest.put(part, new float[]{part.xRot, part.yRot, part.zRot});
        }
        for (int i = 0; i < SAMPLES; i++) {
            float time = length * i / SAMPLES;
            model.setDefault();
            for (BedrockAnimation animation : animations) {
                try {
                    animation.run((RenderContext) null, parts, model.getRootPart(), state, time, 0F, 0F, time * 20F,
                            1F);
                } catch (RuntimeException e) {
                    // Molang que pide algo de la entidad: se pierde ese instante de esa animación, nada más
                }
            }
            for (Map.Entry<ModelPart, String> entry : names.entrySet()) {
                ModelPart part = entry.getKey();
                float[] base = rest.get(part);
                if (base == null) {
                    continue;
                }
                float x = (float) Math.toDegrees(part.xRot - base[0]);
                float y = (float) Math.toDegrees(part.yRot - base[1]);
                float z = (float) Math.toDegrees(part.zRot - base[2]);
                if (x == 0 && y == 0 && z == 0 && !result.containsKey(entry.getValue())) {
                    continue;
                }
                float[][] samples = result.computeIfAbsent(entry.getValue(), key -> new float[SAMPLES][3]);
                samples[i][0] = x;
                samples[i][1] = y;
                samples[i][2] = z;
            }
        }
        return result;
    }

    private static void resetQuietly(PosableModel model) {
        try {
            model.setDefault();
        } catch (Throwable e) {
            FusionSafety.failed("dejar en reposo un modelo tras el atlas", e);
        }
    }
}
