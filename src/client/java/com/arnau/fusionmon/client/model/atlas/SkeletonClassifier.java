package com.arnau.fusionmon.client.model.atlas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Atlas de esqueletos: qué es cada hueso de un modelo (cabeza, patas, brazos, alas, cola) deducido de cómo se MUEVE,
 * no de cómo se llama. Los nombres de huesos no siguen ninguna convención (AllTheMons junta palabras, Blockbench deja
 * "bone12", Cobblemon 1.8.2 rehízo 36 modelos con "head_ai", "head_angle"...), pero las animaciones sí dicen la verdad:
 *  - la cabeza es el hueso al que Cobblemon hace mirar al jugador (q.look en el poser);
 *  - las patas son lo que se balancea al andar y llega al suelo; los brazos, lo que se balancea a los lados sin
 *    llegar; las alas, lo que aletea al volar; la cola, lo que se mece detrás, en el centro.
 * Además, algunos posers declaran las extremidades con animaciones hechas en código (q.biped_walk, q.quadruped_walk,
 * q.bimanual_swing, q.sine_wing_flap): esas valen más que el movimiento.
 *
 * Puro: sin clases de Minecraft ni de Cobblemon, para poder probarlo fuera del juego sobre todos los modelos
 * (tools/skeleton-atlas). Los datos los prepara SkeletonAtlas en el juego (con el motor de animaciones de Cobblemon)
 * o la herramienta (leyendo los .json). FusionGraft lo usa para lo que no encuentra por los nombres.
 *
 * Marco de coordenadas de la entrada: bloques, Y hacia arriba, la cara hacia -Z (como en Blockbench).
 */
public final class SkeletonClassifier {

    /** Lo que se puede saber de un hueso. */
    public enum Role { HEAD, LEG, ARM, WING, TAIL }

    /** De dónde sale lo que se sabe, de más a menos fiable. */
    public enum Source {
        /** Un resource pack lo dice (assets/<ns>/fusionmon/skeletons/*.json). */
        OVERRIDE,
        /** El poser lo declara con una animación de código (q.biped_walk('leg_left', 'leg_right')...). */
        DECLARED,
        /** El hueso al que el poser hace mirar (q.look). */
        LOOK,
        /** Cómo se mueve en las animaciones de andar, estar quieto y volar. */
        MOTION
    }

    /**
     * Un hueso del modelo en reposo.
     *
     * @param parent    nombre del padre, o null en la raíz
     * @param pivot     punto de giro (x, y, z)
     * @param ownBox    caja de sus propios cubos (minX, minY, minZ, maxX, maxY, maxZ), o null si no tiene
     * @param ownVolume volumen real de sus cubos (los planos de grosor cero no cuentan)
     */
    public record BoneInfo(String name, String parent, float[] pivot, float[] ownBox, float ownVolume) {
    }

    /**
     * Una extremidad que el poser declara con una animación de código. El lado se mide en el modelo (los nombres
     * "left"/"right" son unas veces del Pokémon y otras de quien lo mira).
     *
     * @param depth en q.quadruped_walk, -1 las patas de delante y 1 las de detrás; si no, 0
     */
    public record Declared(Role role, int depth) {
    }

    /**
     * Lo que se analiza de un modelo.
     *
     * @param look     huesos a los que hace mirar alguna pose (q.look), en cualquier orden
     * @param declared extremidades declaradas por animaciones de código, por hueso
     * @param stand    giro de cada hueso (en grados, respecto al reposo) en varios instantes de la animación de estar
     *                 quieto: [instante][x, y, z]. Huesos que no se mueven: pueden faltar
     * @param walk     lo mismo al andar
     * @param fly      lo mismo al volar (vacío si no vuela)
     */
    public record Input(List<BoneInfo> bones, Collection<String> look, Map<String, Declared> declared,
                        Map<String, float[][]> stand, Map<String, float[][]> walk, Map<String, float[][]> fly) {
    }

    /**
     * Lo que dice un resource pack de un modelo (ver SkeletonOverrides): manda sobre todo lo demás.
     *
     * @param head  hueso de la cabeza; null = no lo dice, "" = el modelo no tiene cabeza
     * @param parts por cada papel que menciona, sus huesos (lista vacía = no tiene); los que no menciona, como siempre
     */
    public record Correction(String head, Map<Role, List<String>> parts) {
    }

    /**
     * Una pieza reconocida: el hueso de más arriba de una extremidad, ala o cola, con todo lo que cuelga.
     *
     * @param side  -1 o 1 según el lado (X), 0 en el centro
     * @param depth en patas de animales de cuatro patas, -1 delante y 1 detrás; si no, 0
     */
    public record Part(String bone, Role role, int side, int depth, Source source) {
    }

    /**
     * El esqueleto de un modelo.
     *
     * @param head       hueso de la cabeza (lo que mira al jugador), o null si no se sabe
     * @param headSource de dónde sale
     * @param parts      patas, brazos, alas y cola
     * @param corrected  papeles que ha fijado un resource pack (HEAD si la cabeza): esos no se completan con nada
     *                   (una lista vacía en el pack quiere decir "no tiene")
     */
    public record Skeleton(String head, Source headSource, List<Part> parts, Set<Role> corrected) {

        public static final Skeleton EMPTY = new Skeleton(null, null, List.of(), Set.of());

        public List<Part> of(Role role) {
            List<Part> found = new ArrayList<>();
            for (Part part : parts) {
                if (part.role() == role) {
                    found.add(part);
                }
            }
            return found;
        }

        /** La pieza cuyo hueso de arriba es este, o null. */
        public Part part(String bone) {
            for (Part part : parts) {
                if (part.bone().equals(bone)) {
                    return part;
                }
            }
            return null;
        }

        public boolean isEmpty() {
            return head == null && parts.isEmpty();
        }
    }

    // Umbrales (grados y proporciones del modelo). Simulados con tools/skeleton-atlas sobre los modelos de Cobblemon
    // 1.8.2: ver docs/species/skeleton-atlas.md

    /** Una pata o un brazo se balancea al andar al menos esto... */
    static final float WALK_SWING = 15F;
    /** ...y esto más que estando quieto (si no, es un adorno que se mueve siempre: pelo, llamas). */
    static final float WALK_EXTRA = 8F;
    /** Un ala aletea al volar al menos esto, y WALK_EXTRA más que estando quieto. */
    static final float FLY_FLAP = 20F;
    /** Una cola se mece al menos esto, quieto o andando. */
    static final float TAIL_SWAY = 10F;
    /**
     * Llega al suelo: lo más bajo de su rama está a menos de esta parte del alto del modelo del suelo. Holgado: en el
     * .geo hay patas estiradas por debajo del suelo que las animaciones recogen (las de atrás de Rattata), y las
     * delanteras quedaban "en el aire".
     */
    static final float GROUND = 0.15F;
    /** Una pata va hacia abajo: baja desde su cadera al menos esta parte de lo que se aleja en horizontal. */
    static final float LEG_DROP = 0.5F;
    /** Tiene lado: el centro de su rama está a más de esta parte del ancho del modelo del centro. */
    static final float SIDE = 0.08F;
    /** La cola está detrás: el centro de su rama, al menos esta parte del fondo del modelo detrás del centro. */
    static final float BEHIND = 0.05F;
    /** Los brazos salen de la parte de arriba: su pivote, al menos a esta parte del alto del modelo. */
    static final float ARM_HEIGHT = 0.3F;
    /** La cola sale de la parte de abajo o del medio: su pivote, como mucho a esta parte del alto (el pelo, más arriba). */
    static final float TAIL_HEIGHT = 0.6F;
    /** La cola va hacia atrás: lo que se aleja hacia atrás, al menos esta parte de lo que sube o baja. */
    static final float TAIL_BACK = 0.8F;
    /** Un ala sale de la espalda: su pivote, detrás del centro del tronco del que cuelga, esta parte de su fondo. */
    static final float WING_BACK = 0.1F;
    /** Un hueso con al menos esta parte del volumen del modelo es el cuerpo, no una extremidad. */
    static final float BODY_SHARE = 0.5F;

    private SkeletonClassifier() {
    }

    /** Las clases de pose que se analizan (los PoseType de Cobblemon que se buscan en cada una, por orden). */
    public enum Kind {
        STAND("STAND", "NONE"),
        WALK("WALK"),
        FLY("FLY", "HOVER");

        private final List<String> types;

        Kind(String... types) {
            this.types = List.of(types);
        }
    }

    /**
     * Qué pose de un poser se analiza para cada clase: la primera que tenga su PoseType y su nombre ("walking",
     * "standing", "fly"), si no la primera con su PoseType. Nunca las de combate ni las de montar o saltar: llevan
     * otras animaciones (Charizard tiene "jump" con STAND y WALK antes que "walking"). La misma regla en el juego y
     * en tools/skeleton-atlas.
     *
     * @param poses nombre de cada pose → sus PoseType, en el orden del poser
     * @return el nombre de la pose, o null si no hay ninguna
     */
    public static String pickPose(Map<String, ? extends Collection<String>> poses, Kind kind) {
        String word = switch (kind) {
            case STAND -> "stand";
            case WALK -> "walk";
            case FLY -> "fly";
        };
        for (String type : kind.types) {
            String fallback = null;
            for (Map.Entry<String, ? extends Collection<String>> pose : poses.entrySet()) {
                String name = pose.getKey().toLowerCase(java.util.Locale.ROOT);
                if (!pose.getValue().contains(type) || name.contains("battle") || name.contains("ride")
                        || name.contains("jump") || name.contains("sleep")) {
                    continue;
                }
                if (name.contains(word) || kind == Kind.STAND && name.contains("idle")) {
                    return pose.getKey();
                }
                if (fallback == null) {
                    fallback = pose.getKey();
                }
            }
            if (fallback != null) {
                return fallback;
            }
        }
        return null;
    }

    public static Skeleton classify(Input input) {
        return classify(input, null);
    }

    public static Skeleton classify(Input input, Correction override) {
        Model model = new Model(input.bones());
        if (model.total <= 0) {
            return Skeleton.EMPTY;
        }
        Skeleton found = analyze(model, input);
        return override == null ? found : apply(model, found, override);
    }

    /** Cambia lo que diga el resource pack: la cabeza y, por papel, todas sus piezas. */
    private static Skeleton apply(Model model, Skeleton found, Correction override) {
        String head = found.head();
        Source headSource = found.headSource();
        Set<Role> corrected = new HashSet<>(override.parts().keySet());
        if (override.head() != null) {
            corrected.add(Role.HEAD);
            head = override.head().isEmpty() || !model.bones.containsKey(override.head()) ? null : override.head();
            headSource = Source.OVERRIDE;
        }
        List<Part> parts = new ArrayList<>();
        for (Part part : found.parts()) {
            if (!override.parts().containsKey(part.role())) {
                parts.add(part);
            }
        }
        for (Map.Entry<Role, List<String>> entry : override.parts().entrySet()) {
            List<Part> legs = new ArrayList<>();
            for (String bone : entry.getValue()) {
                BoneNode node = model.bones.get(bone);
                if (node != null) {
                    Part part = new Part(bone, entry.getKey(), entry.getKey() == Role.TAIL ? 0 : model.side(node), 0,
                            Source.OVERRIDE);
                    (entry.getKey() == Role.LEG ? legs : parts).add(part);
                }
            }
            parts.addAll(withDepth(model, legs));
        }
        return new Skeleton(head, head == null ? null : headSource, List.copyOf(parts), Set.copyOf(corrected));
    }

    /** Las patas con delante/detrás si son cuatro o más (en un bípedo no significa nada). */
    private static List<Part> withDepth(Model model, List<Part> legs) {
        if (legs.size() < 4) {
            return legs;
        }
        float legsZ = 0;
        for (Part leg : legs) {
            legsZ += model.bones.get(leg.bone()).info.pivot()[2];
        }
        legsZ /= legs.size();
        List<Part> result = new ArrayList<>();
        for (Part leg : legs) {
            float z = model.bones.get(leg.bone()).info.pivot()[2];
            result.add(leg.depth() != 0 ? leg
                    : new Part(leg.bone(), leg.role(), leg.side(), z < legsZ ? -1 : 1, leg.source()));
        }
        return result;
    }

    private static Skeleton analyze(Model model, Input input) {

        Map<String, Part> parts = new LinkedHashMap<>();
        // 1. Lo que declara el poser
        for (Map.Entry<String, Declared> entry : input.declared().entrySet()) {
            BoneNode node = model.bones.get(entry.getKey());
            // Sin cubos debajo no hay nada que pegar (posers copiados de otra especie declaran patas que no existen)
            if (node != null && node.hasCubes) {
                Declared declared = entry.getValue();
                parts.put(entry.getKey(), new Part(entry.getKey(), declared.role(), model.side(node), declared.depth(),
                        Source.DECLARED));
            }
        }

        // 2. La cabeza: lo que mira al jugador
        String head = lookHead(model, input.look());

        // 3. Lo que se mueve. No pueden ser extremidades: la cabeza y lo que cuelga de ella, el camino de la raíz a
        // la cabeza (cuello, tronco: se mecen al andar) ni los huesos que son casi todo el modelo (el cuerpo)
        Set<String> excluded = new HashSet<>();
        if (head != null) {
            excluded.addAll(model.subtree(head));
            excluded.addAll(model.ancestors(head));
        }
        for (BoneNode node : model.bones.values()) {
            if (node.subtreeVolume >= BODY_SHARE * model.total || node.parent == null) {
                excluded.add(node.info.name());
            }
        }
        for (String declared : parts.keySet()) {
            excluded.addAll(model.subtree(declared));
        }
        Motion motion = new Motion(input);
        List<Part> found = new ArrayList<>();
        for (BoneNode root : model.roots) {
            collect(model, motion, root, excluded, found);
        }
        // Patas, brazos y alas van por parejas: una sola que se mueve es otra cosa (un segmento de serpiente que roza el
        // suelo, una aleta, un mechón). Sin pareja a la otra mano, fuera
        found.removeIf(part -> part.role() != Role.TAIL && !paired(part, found));
        // Delante o detrás: solo con cuatro patas o más
        List<Part> legs = new ArrayList<>();
        for (Part part : found) {
            if (part.role() == Role.LEG) {
                legs.add(part);
            } else {
                parts.putIfAbsent(part.bone(), part);
            }
        }
        for (Part leg : withDepth(model, legs)) {
            parts.putIfAbsent(leg.bone(), leg);
        }

        return new Skeleton(head, head == null ? null : Source.LOOK, List.copyOf(parts.values()), Set.of());
    }

    /**
     * La cabeza entre los huesos a los que hace mirar el poser. Muchos posers miran con toda una cadena (Charizard:
     * neck, neck2, neck3, neck4 y head) y algunos también con adornos que deben girar con ella (las hojas de
     * Venusaur, la barba de Corviknight): la cabeza es el final de la cadena más larga y, si hay varias, la de más
     * volumen. Si alguna lleva dentro el punto donde Cobblemon pone los sombreros (locator_head), esa.
     */
    static String lookHead(Model model, Collection<String> look) {
        List<String> looks = new ArrayList<>();
        for (String name : look) {
            BoneNode node = model.bones.get(name);
            // La raíz no: "quitarla" sería quitar el Pokémon entero. Ni un hueso sin cubos debajo (un pivote suelto)
            if (node != null && node.parent != null && node.hasCubes && !looks.contains(name)) {
                looks.add(name);
            }
        }
        if (looks.isEmpty()) {
            return null;
        }
        if (model.bones.containsKey("locator_head")) {
            Set<String> holders = model.ancestors("locator_head");
            String best = null;
            for (String name : looks) {
                if (holders.contains(name) && (best == null || model.depth(name) > model.depth(best))) {
                    best = name;
                }
            }
            if (best != null) {
                return best;
            }
        }
        String best = null;
        int bestChain = -1;
        for (String name : looks) {
            if (hasLookBelow(model, name, looks)) {
                continue;
            }
            int chain = 0;
            for (String ancestor : model.ancestors(name)) {
                if (looks.contains(ancestor)) {
                    chain++;
                }
            }
            if (chain > bestChain || chain == bestChain
                    && model.bones.get(name).subtreeVolume > model.bones.get(best).subtreeVolume) {
                best = name;
                bestChain = chain;
            }
        }
        return best;
    }

    private static boolean hasLookBelow(Model model, String name, List<String> looks) {
        for (String other : looks) {
            if (!other.equals(name) && model.ancestors(other).contains(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Busca extremidades bajando por el árbol: la primera que se mueve en cada rama es la de arriba (la cadera, el
     * hombro). Un hueso que agrupa las dos patas (o los dos brazos) se mueve con ellas al andar pero tiene una de cada
     * lado: ese no, se mira dentro.
     */
    private static void collect(Model model, Motion motion, BoneNode node, Set<String> excluded, List<Part> found) {
        String name = node.info.name();
        if (!excluded.contains(name) && node.hasCubes && motion.moves(name) && !splits(model, motion, node)) {
            Part part = classify(model, motion, node);
            if (part != null) {
                found.add(part);
                return;
            }
        }
        for (BoneNode child : node.children) {
            collect(model, motion, child, excluded, found);
        }
    }

    /** ¿Hay debajo de este hueso piezas que se mueven a los dos lados? (entonces agrupa una pareja). */
    private static boolean splits(Model model, Motion motion, BoneNode node) {
        boolean left = false;
        boolean right = false;
        for (String name : model.subtree(node.info.name())) {
            if (name.equals(node.info.name()) || !motion.moves(name)) {
                continue;
            }
            int side = model.side(model.bones.get(name));
            left |= side < 0;
            right |= side > 0;
        }
        return left && right;
    }

    private static boolean paired(Part part, List<Part> found) {
        if (part.side() == 0) {
            return false;
        }
        for (Part other : found) {
            if (other.role() == part.role() && other.side() == -part.side()) {
                return true;
            }
        }
        return false;
    }

    /** Qué es una rama que se mueve, o null si no parece nada. */
    private static Part classify(Model model, Motion motion, BoneNode node) {
        String name = node.info.name();
        float[] box = node.subtreeBox;
        int side = model.side(node);
        float walk = motion.walk(name);
        float stand = motion.stand(name);
        float fly = motion.fly(name);
        boolean swings = walk >= WALK_SWING && walk >= stand + WALK_EXTRA;
        boolean flaps = fly >= FLY_FLAP && fly >= stand + WALK_EXTRA;

        if (swings && standsOn(model, node)) {
            return new Part(name, Role.LEG, side, 0, Source.MOTION);
        }
        boolean grounded = box[1] <= model.box[1] + GROUND * model.height();
        // Un ala aletea al volar. Los brazos de muchos modelos están en cruz en el .geo (las animaciones los bajan) y
        // se extienden hacia fuera como un ala: sin vuelo no se puede distinguir, así que solo con vuelo
        // Y sale de la espalda: los brazos que se agitan al flotar (Snorlax, Munchlax) salen de los lados
        if (side != 0 && !grounded && flaps && fromBack(node)) {
            return new Part(name, Role.WING, side, 0, Source.MOTION);
        }
        float centerZ = (box[2] + box[5]) / 2;
        float modelCenterZ = (model.box[2] + model.box[5]) / 2;
        boolean behind = centerZ >= modelCenterZ + BEHIND * model.depth();
        float[] pivot = node.info.pivot();
        if (side == 0 && behind && Math.max(walk, stand) >= TAIL_SWAY && pointsBack(node)
                && pivot[1] <= model.box[1] + TAIL_HEIGHT * model.height()) {
            return new Part(name, Role.TAIL, 0, 0, Source.MOTION);
        }
        if (side != 0 && !grounded && swings && pivot[1] >= model.box[1] + ARM_HEIGHT * model.height()) {
            return new Part(name, Role.ARM, side, 0, Source.MOTION);
        }
        return null;
    }

    /**
     * ¿Es una pata? Llega al suelo y va hacia abajo desde su cadera: la cola de Charizard también se balancea al andar
     * y casi toca el suelo, pero va hacia atrás.
     */
    private static boolean standsOn(Model model, BoneNode node) {
        return node.subtreeBox[1] <= model.box[1] + GROUND * model.height() && pointsDown(node);
    }

    /** ¿Va hacia atrás más que hacia arriba o abajo? (una cola; el pelo de la nuca cuelga). */
    private static boolean pointsBack(BoneNode node) {
        float[] box = node.subtreeBox;
        float[] pivot = node.info.pivot();
        float back = box[5] - pivot[2];
        float vertical = Math.max(pivot[1] - box[1], box[4] - pivot[1]);
        return back > 0 && back >= TAIL_BACK * vertical;
    }

    /** ¿Sale de la mitad de atrás del tronco del que cuelga (el primer hueso con cubos propios por encima)? */
    private static boolean fromBack(BoneNode node) {
        for (BoneNode trunk = node.parent; trunk != null; trunk = trunk.parent) {
            float[] own = trunk.info.ownBox();
            if (own != null) {
                float center = (own[2] + own[5]) / 2;
                return node.info.pivot()[2] > center + WING_BACK * (own[5] - own[2]);
            }
        }
        return false;
    }

    /** Lo que baja desde el pivote es al menos LEG_DROP de lo que se aleja en horizontal (a un lado o adelante/atrás). */
    private static boolean pointsDown(BoneNode node) {
        float[] box = node.subtreeBox;
        float[] pivot = node.info.pivot();
        float down = pivot[1] - box[1];
        float across = Math.max(Math.max(pivot[0] - box[0], box[3] - pivot[0]),
                Math.max(pivot[2] - box[2], box[5] - pivot[2]));
        return down > 0 && down >= LEG_DROP * across;
    }

    /** Cuánto se mueve cada hueso en cada animación (lo máximo que cambia su giro en un eje, en grados). */
    private static final class Motion {
        private final Map<String, Float> stand;
        private final Map<String, Float> walk;
        private final Map<String, Float> fly;

        Motion(Input input) {
            stand = amplitudes(input.stand());
            walk = amplitudes(input.walk());
            fly = amplitudes(input.fly());
        }

        float stand(String bone) {
            return stand.getOrDefault(bone, 0F);
        }

        float walk(String bone) {
            return walk.getOrDefault(bone, 0F);
        }

        float fly(String bone) {
            return fly.getOrDefault(bone, 0F);
        }

        /** ¿Se mueve lo bastante para ser algo? (después se mira qué) */
        boolean moves(String bone) {
            float stand = stand(bone);
            float walk = walk(bone);
            float fly = fly(bone);
            return walk >= WALK_SWING && walk >= stand + WALK_EXTRA
                    || fly >= FLY_FLAP && fly >= stand + WALK_EXTRA
                    || Math.max(walk, stand) >= TAIL_SWAY;
        }

        private static Map<String, Float> amplitudes(Map<String, float[][]> samples) {
            Map<String, Float> result = new HashMap<>();
            if (samples == null) {
                return result;
            }
            for (Map.Entry<String, float[][]> entry : samples.entrySet()) {
                result.put(entry.getKey(), amplitude(entry.getValue()));
            }
            return result;
        }
    }

    /** Lo máximo que cambia un giro en uno de sus ejes a lo largo de la animación. */
    static float amplitude(float[][] samples) {
        float best = 0;
        for (int axis = 0; axis < 3; axis++) {
            float min = Float.POSITIVE_INFINITY;
            float max = Float.NEGATIVE_INFINITY;
            for (float[] sample : samples) {
                min = Math.min(min, sample[axis]);
                max = Math.max(max, sample[axis]);
            }
            if (max >= min) {
                best = Math.max(best, max - min);
            }
        }
        return best;
    }

    /** Un hueso con sus hijos y lo que mide su rama. */
    static final class BoneNode {
        final BoneInfo info;
        BoneNode parent;
        final List<BoneNode> children = new ArrayList<>();
        float subtreeVolume;
        /** Si la rama tiene cubos (aunque sean planos sin volumen: la cola de Pikachu). */
        boolean hasCubes;
        /** Caja de los cubos de la rama, o del pivote si no tiene. */
        float[] subtreeBox;
        int depth;

        BoneNode(BoneInfo info) {
            this.info = info;
        }
    }

    /** El árbol de huesos con sus medidas. */
    static final class Model {
        final Map<String, BoneNode> bones = new LinkedHashMap<>();
        final List<BoneNode> roots = new ArrayList<>();
        /** Caja de todos los cubos. */
        final float[] box = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        final float total;
        private final Map<String, Set<String>> ancestors = new HashMap<>();
        private final Map<String, List<String>> subtrees = new HashMap<>();

        Model(List<BoneInfo> infos) {
            for (BoneInfo info : infos) {
                bones.putIfAbsent(info.name(), new BoneNode(info));
            }
            for (BoneNode node : bones.values()) {
                BoneNode parent = node.info.parent() == null ? null : bones.get(node.info.parent());
                // Un padre que falta (modelo roto) se trata como raíz
                if (parent == null || parent == node) {
                    roots.add(node);
                } else {
                    node.parent = parent;
                    parent.children.add(node);
                }
            }
            float sum = 0;
            for (BoneNode root : roots) {
                sum += measure(root, 0);
            }
            total = sum;
        }

        private float measure(BoneNode node, int depth) {
            node.depth = depth;
            float[] own = node.info.ownBox();
            float volume = node.info.ownVolume();
            float[] box = own != null ? own.clone() : new float[]{node.info.pivot()[0], node.info.pivot()[1],
                    node.info.pivot()[2], node.info.pivot()[0], node.info.pivot()[1], node.info.pivot()[2]};
            boolean hasCubes = own != null;
            for (BoneNode child : node.children) {
                volume += measure(child, depth + 1);
                if (child.hasCubes) {
                    if (!hasCubes) {
                        box = child.subtreeBox.clone();
                        hasCubes = true;
                    } else {
                        include(box, child.subtreeBox);
                    }
                }
            }
            node.subtreeVolume = volume;
            node.subtreeBox = box;
            node.hasCubes = hasCubes;
            if (own != null) {
                include(this.box, own);
            }
            return volume;
        }

        float width() {
            return Math.max(box[3] - box[0], 1E-3F);
        }

        float height() {
            return Math.max(box[4] - box[1], 1E-3F);
        }

        float depth() {
            return Math.max(box[5] - box[2], 1E-3F);
        }

        int depth(String name) {
            return bones.get(name).depth;
        }

        /** Lado de una rama (por el centro de su caja; si cae en medio, por su pivote): -1, 0 o 1. */
        int side(BoneNode node) {
            float center = (box[0] + box[3]) / 2;
            float[] own = node.subtreeBox;
            float offset = (own[0] + own[3]) / 2 - center;
            if (Math.abs(offset) < SIDE * width()) {
                offset = node.info.pivot()[0] - center;
            }
            return Math.abs(offset) < SIDE * width() ? 0 : (int) Math.signum(offset);
        }

        /** Los huesos de los que cuelga (sin él). */
        Set<String> ancestors(String name) {
            return ancestors.computeIfAbsent(name, key -> {
                Set<String> result = new HashSet<>();
                BoneNode node = bones.get(key);
                for (BoneNode current = node == null ? null : node.parent; current != null; current = current.parent) {
                    result.add(current.info.name());
                }
                return result;
            });
        }

        /** Él y todo lo que cuelga de él. */
        List<String> subtree(String name) {
            return subtrees.computeIfAbsent(name, key -> {
                List<String> result = new ArrayList<>();
                BoneNode node = bones.get(key);
                if (node != null) {
                    addSubtree(node, result);
                }
                return result;
            });
        }

        private static void addSubtree(BoneNode node, List<String> result) {
            result.add(node.info.name());
            for (BoneNode child : node.children) {
                addSubtree(child, result);
            }
        }
    }

    static void include(float[] box, float[] other) {
        for (int i = 0; i < 3; i++) {
            box[i] = Math.min(box[i], other[i]);
            box[i + 3] = Math.max(box[i + 3], other[i + 3]);
        }
    }
}
