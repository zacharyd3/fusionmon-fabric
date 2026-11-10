import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.BoneInfo;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Declared;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Input;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Kind;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Part;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Role;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Skeleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prueba el atlas de esqueletos (SkeletonClassifier) sobre TODOS los modelos de Cobblemon (y de los packs que se le
 * pasen), fuera del juego, y escribe el informe en docs/species/skeleton-atlas.{md,csv}. Sirve para afinar los umbrales
 * del clasificador antes de tocarlos en el juego (como tools/species-table.ps1 con las reglas de FusionGraft).
 *
 * Solo necesita un JDK 21+ (sin Gradle ni dependencias). Desde la raíz del proyecto:
 *   javac -d build/skeleton-atlas tools/skeleton-atlas/SkeletonAtlasCheck.java \
 *         src/client/java/com/arnau/fusionmon/client/model/atlas/SkeletonClassifier.java
 *   java -cp build/skeleton-atlas SkeletonAtlasCheck docs/species <jar de Cobblemon o carpetas/zips de packs>...
 * El jar de Cobblemon está en la caché de Gradle: ~/.gradle/caches/modules-2/files-2.1/com.cobblemon/neoforge/.
 *
 * Lo que hace el juego con el motor de Cobblemon (SkeletonAtlas), aquí se hace a mano y aproximado: un evaluador de
 * Molang pequeño (lo que usan las animaciones: q.anim_time, math.sin/cos...) y la postura del .geo. Para los umbrales
 * basta: se miran amplitudes de giro, no posiciones exactas.
 */
public final class SkeletonAtlasCheck {

    /** Instantes por animación. */
    private static final int SAMPLES = 24;

    private static final Pattern LOOK = Pattern.compile("q(?:uery)?\\.look\\(\\s*'([^']+)'");
    private static final Pattern BEDROCK = Pattern.compile("q(?:uery)?\\.bedrock\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'");
    private static final Pattern CALL = Pattern.compile("q(?:uery)?\\.(biped_walk|quadruped_walk|bimanual_swing|sine_wing_flap)\\(([^)]*)\\)");

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: SkeletonAtlasCheck <carpeta de salida> <jar|zip|carpeta>...");
            System.exit(1);
        }
        Assets assets = new Assets();
        for (int i = 1; i < args.length; i++) {
            assets.load(Path.of(args[i]));
        }
        System.out.printf(Locale.ROOT, "Modelos %d, posers %d, grupos de animaciones %d, resolvers %d%n",
                assets.geometries.size(), assets.posers.size(), assets.animations.size(), assets.resolvers.size());

        List<Row> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Object>> resolver : assets.resolvers.entrySet()) {
            Map<String, Object> json = resolver.getValue();
            String species = String.valueOf(json.getOrDefault("species", json.getOrDefault("name", "?")));
            Object variations = json.get("variations");
            if (!(variations instanceof List<?> list)) {
                continue;
            }
            // Cada variación hereda el poser y el modelo de las anteriores (Cobblemon busca el último que encaja): para
            // el informe vale cada pareja poser + modelo distinta
            String poser = null;
            for (Object variation : list) {
                if (!(variation instanceof Map<?, ?> map)) {
                    continue;
                }
                if (map.get("poser") instanceof String value) {
                    poser = value;
                }
                if (!(map.get("model") instanceof String model) || poser == null) {
                    continue;
                }
                if (!seen.add(poser + " " + model)) {
                    continue;
                }
                rows.add(analyze(assets, species, poser, model));
            }
        }
        int skipped = (int) rows.stream().filter(row -> row == null).count();
        rows.removeIf(row -> row == null);
        rows.sort((a, b) -> a.model.compareTo(b.model));
        write(Path.of(args[0]), rows, assets, skipped);
    }

    // ---- Un modelo ----

    private record Row(String species, String poser, String model, Skeleton skeleton, Names names, String lookBones,
                       String error) {
    }

    /** Lo que encontrarían los nombres (aproximado, como las reglas más simples de FusionGraft). */
    private record Names(boolean head, boolean legs, boolean arms, boolean wings, boolean tail) {
    }

    private static Row analyze(Assets assets, String species, String poser, String model) {
        Map<String, Object> geo = assets.geometries.get(geometryId(model));
        Map<String, Object> poserJson = assets.posers.get(poser);
        if (geo == null || poserJson == null) {
            return null;
        }
        try {
            List<BoneInfo> bones = Geo.bones(geo);
            Map<String, Collection<String>> poseTypes = new LinkedHashMap<>();
            Map<String, Object> poses = map(poserJson.get("poses"));
            for (Map.Entry<String, Object> pose : poses.entrySet()) {
                List<String> types = new ArrayList<>();
                for (Object type : list(map(pose.getValue()).get("poseTypes"))) {
                    types.add(String.valueOf(type));
                }
                poseTypes.put(pose.getKey(), types);
            }
            Set<String> look = new LinkedHashSet<>();
            Map<String, Declared> declared = new LinkedHashMap<>();
            for (Object pose : poses.values()) {
                for (Object animation : list(map(pose).get("animations"))) {
                    String text = animation instanceof String value ? value
                            : animation instanceof Map<?, ?> conditional ? String.valueOf(conditional.get("animation")) : "";
                    Matcher look_ = LOOK.matcher(text);
                    while (look_.find()) {
                        look.add(look_.group(1));
                    }
                    Matcher call = CALL.matcher(text);
                    while (call.find()) {
                        declare(call.group(1), call.group(2), declared);
                    }
                }
            }
            Set<String> names = new LinkedHashSet<>();
            for (BoneInfo bone : bones) {
                names.add(bone.name());
            }
            Input input = new Input(bones, look, declared,
                    sample(assets, poses, SkeletonClassifier.pickPose(poseTypes, Kind.STAND), names),
                    sample(assets, poses, SkeletonClassifier.pickPose(poseTypes, Kind.WALK), names),
                    sample(assets, poses, SkeletonClassifier.pickPose(poseTypes, Kind.FLY), names));
            Skeleton skeleton = SkeletonClassifier.classify(input);
            // ATLAS_DEBUG=charizard: tabla de cada hueso de ese modelo (para afinar umbrales)
            String debug = System.getenv("ATLAS_DEBUG");
            if (debug != null && geometryId(model).equals("geometry." + debug)) {
                debug(input, skeleton, poseTypes);
            }
            return new Row(species, poser, model, skeleton, names(names), String.join(" ", look), null);
        } catch (RuntimeException e) {
            return new Row(species, poser, model, Skeleton.EMPTY, new Names(false, false, false, false, false), "",
                    e.toString());
        }
    }

    private static void debug(Input input, Skeleton skeleton, Map<String, Collection<String>> poseTypes) {
        System.out.println("poses: stand=" + SkeletonClassifier.pickPose(poseTypes, Kind.STAND) + " walk="
                + SkeletonClassifier.pickPose(poseTypes, Kind.WALK) + " fly="
                + SkeletonClassifier.pickPose(poseTypes, Kind.FLY));
        System.out.println("look=" + input.look() + " declared=" + input.declared().keySet());
        for (BoneInfo bone : input.bones()) {
            System.out.printf(Locale.ROOT, "%-28s parent=%-20s pivot=%s box=%s vol=%.4f stand=%.1f walk=%.1f fly=%.1f%n",
                    bone.name(), bone.parent(), java.util.Arrays.toString(bone.pivot()),
                    bone.ownBox() == null ? "-" : java.util.Arrays.toString(bone.ownBox()), bone.ownVolume(),
                    amplitude(input.stand().get(bone.name())), amplitude(input.walk().get(bone.name())),
                    amplitude(input.fly().get(bone.name())));
        }
        System.out.println("=> head=" + skeleton.head() + " parts=" + skeleton.parts());
    }

    private static float amplitude(float[][] samples) {
        if (samples == null) {
            return 0;
        }
        float best = 0;
        for (int axis = 0; axis < 3; axis++) {
            float min = Float.POSITIVE_INFINITY;
            float max = Float.NEGATIVE_INFINITY;
            for (float[] sample : samples) {
                min = Math.min(min, sample[axis]);
                max = Math.max(max, sample[axis]);
            }
            best = Math.max(best, max - min);
        }
        return best;
    }

    /** Las extremidades de q.biped_walk(periodo, amplitud, 'izq', 'der') y compañía, con sus nombres por defecto. */
    private static void declare(String function, String arguments, Map<String, Declared> declared) {
        List<String> strings = new ArrayList<>();
        Matcher quoted = Pattern.compile("'([^']*)'").matcher(arguments);
        while (quoted.find()) {
            strings.add(quoted.group(1));
        }
        switch (function) {
            case "biped_walk" -> {
                declared.put(at(strings, 0, "leg_left"), new Declared(Role.LEG, 0));
                declared.put(at(strings, 1, "leg_right"), new Declared(Role.LEG, 0));
            }
            case "quadruped_walk" -> {
                declared.put(at(strings, 0, "leg_front_left"), new Declared(Role.LEG, -1));
                declared.put(at(strings, 1, "leg_front_right"), new Declared(Role.LEG, -1));
                declared.put(at(strings, 2, "leg_back_left"), new Declared(Role.LEG, 1));
                declared.put(at(strings, 3, "leg_back_right"), new Declared(Role.LEG, 1));
            }
            case "bimanual_swing" -> {
                declared.put(at(strings, 0, "arm_left"), new Declared(Role.ARM, 0));
                declared.put(at(strings, 1, "arm_right"), new Declared(Role.ARM, 0));
            }
            case "sine_wing_flap" -> {
                // El cuarto parámetro es el eje ('y'): los nombres van después
                int first = strings.size() > 2 ? 1 : 0;
                declared.put(at(strings, first, "wing_left"), new Declared(Role.WING, 0));
                declared.put(at(strings, first + 1, "wing_right"), new Declared(Role.WING, 0));
            }
            default -> {
            }
        }
    }

    private static String at(List<String> list, int index, String fallback) {
        return index < list.size() ? list.get(index) : fallback;
    }

    /** Giro de cada hueso en SAMPLES instantes de las animaciones de una pose (sin las que tienen condición). */
    private static Map<String, float[][]> sample(Assets assets, Map<String, Object> poses, String pose,
                                                 Set<String> bones) {
        Map<String, float[][]> result = new LinkedHashMap<>();
        if (pose == null) {
            return result;
        }
        for (Object animation : list(map(poses.get(pose)).get("animations"))) {
            if (!(animation instanceof String text)) {
                continue;
            }
            Matcher matcher = BEDROCK.matcher(text);
            if (!matcher.find()) {
                continue;
            }
            Map<String, Object> group = assets.animations.get(matcher.group(1));
            Map<String, Object> json = group == null ? null
                    : map(map(group.get("animations")).get("animation." + matcher.group(1) + "." + matcher.group(2)));
            if (json == null || json.isEmpty()) {
                continue;
            }
            double length = number(json.get("animation_length"), 0);
            if (length <= 0) {
                length = 2;
            }
            for (Map.Entry<String, Object> bone : map(json.get("bones")).entrySet()) {
                if (!bones.contains(bone.getKey())) {
                    continue;
                }
                Object rotation = map(bone.getValue()).get("rotation");
                if (rotation == null) {
                    continue;
                }
                float[][] samples = result.computeIfAbsent(bone.getKey(), key -> new float[SAMPLES][3]);
                for (int i = 0; i < SAMPLES; i++) {
                    double time = length * i / SAMPLES;
                    double[] value = Channel.resolve(rotation, time);
                    for (int axis = 0; axis < 3; axis++) {
                        samples[i][axis] += (float) value[axis];
                    }
                }
            }
        }
        return result;
    }

    private static Names names(Set<String> bones) {
        boolean head = false;
        boolean legs = false;
        boolean arms = false;
        boolean wings = false;
        boolean tail = false;
        for (String bone : bones) {
            String name = bone.toLowerCase(Locale.ROOT);
            head |= name.equals("head") || name.equals("locator_head") || name.matches("head\\d+");
            legs |= name.matches(".*(leg|foot|feet).*");
            arms |= name.matches(".*(arm|shoulder|hand).*") && !name.contains("armor");
            wings |= name.contains("wing");
            tail |= name.startsWith("tail");
        }
        return new Names(head, legs, arms, wings, tail);
    }

    private static String geometryId(String model) {
        // "cobblemon:charizard.geo" → "geometry.charizard"
        String path = model.substring(model.indexOf(':') + 1);
        if (path.endsWith(".geo")) {
            path = path.substring(0, path.length() - 4);
        }
        return "geometry." + path;
    }

    // ---- Informe ----

    private static void write(Path out, List<Row> rows, Assets assets, int skipped) throws IOException {
        Files.createDirectories(out);
        int errors = 0;
        int withHead = 0;
        int headFill = 0;
        int legs = 0;
        int legFill = 0;
        int arms = 0;
        int armFill = 0;
        int wings = 0;
        int wingFill = 0;
        int tails = 0;
        int tailFill = 0;
        Map<SkeletonClassifier.Source, Integer> sources = new TreeMap<>();
        List<String> headFills = new ArrayList<>();
        List<String> legFills = new ArrayList<>();
        List<String> armFills = new ArrayList<>();
        List<String> wingFills = new ArrayList<>();
        List<String> tailFills = new ArrayList<>();
        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(out.resolve("skeleton-atlas.csv"),
                StandardCharsets.UTF_8))) {
            // BOM: así Excel lo abre en UTF-8, como especies.csv
            csv.print('﻿');
            csv.println("\"especie\";\"poser\";\"modelo\";\"mira\";\"cabeza\";\"patas\";\"brazos\";\"alas\";\"cola\";\"error\"");
            for (Row row : rows) {
                Skeleton skeleton = row.skeleton;
                if (row.error != null) {
                    errors++;
                }
                for (Part part : skeleton.parts()) {
                    sources.merge(part.source(), 1, Integer::sum);
                }
                if (skeleton.head() != null) {
                    withHead++;
                    if (!row.names.head) {
                        headFill++;
                        headFills.add(short_(row) + " (" + skeleton.head() + ")");
                    }
                }
                if (!skeleton.of(Role.LEG).isEmpty()) {
                    legs++;
                    if (!row.names.legs) {
                        legFill++;
                        legFills.add(short_(row) + " (" + bones(skeleton.of(Role.LEG)) + ")");
                    }
                }
                if (!skeleton.of(Role.ARM).isEmpty()) {
                    arms++;
                    if (!row.names.arms) {
                        armFill++;
                        armFills.add(short_(row) + " (" + bones(skeleton.of(Role.ARM)) + ")");
                    }
                }
                if (!skeleton.of(Role.WING).isEmpty()) {
                    wings++;
                    if (!row.names.wings) {
                        wingFill++;
                        wingFills.add(short_(row) + " (" + bones(skeleton.of(Role.WING)) + ")");
                    }
                }
                if (!skeleton.of(Role.TAIL).isEmpty()) {
                    tails++;
                    if (!row.names.tail) {
                        tailFill++;
                        tailFills.add(short_(row) + " (" + bones(skeleton.of(Role.TAIL)) + ")");
                    }
                }
                csv.println(String.join(";", quote(row.species), quote(row.poser), quote(row.model),
                        quote(row.lookBones), quote(skeleton.head() == null ? "" : skeleton.head()),
                        quote(parts(skeleton.of(Role.LEG))), quote(parts(skeleton.of(Role.ARM))),
                        quote(parts(skeleton.of(Role.WING))), quote(parts(skeleton.of(Role.TAIL))),
                        quote(row.error == null ? "" : row.error)));
            }
        }
        try (PrintWriter md = new PrintWriter(Files.newBufferedWriter(out.resolve("skeleton-atlas.md"),
                StandardCharsets.UTF_8))) {
            md.println("# Atlas de esqueletos (generado)");
            md.println();
            md.println("Generado por `tools/skeleton-atlas/SkeletonAtlasCheck.java` (no editar a mano). Lo que");
            md.println("`SkeletonClassifier` deduce de las **animaciones** de cada modelo: la cabeza es lo que mira al");
            md.println("jugador (`q.look`), las patas lo que se balancea al andar y llega al suelo, los brazos lo que se");
            md.println("balancea a los lados, las alas lo que aletea al volar, la cola lo que se mece detrás. Las");
            md.println("animaciones de código (`q.biped_walk`, `q.quadruped_walk`, `q.bimanual_swing`, `q.sine_wing_flap`)");
            md.println("cuentan como declaradas. Datos por modelo en `skeleton-atlas.csv`.");
            md.println();
            md.println("En el juego `FusionGraft` sigue usando primero los nombres (`docs/species/`) y el atlas rellena lo");
            md.println("que los nombres no encuentran (`/fusionvisual atlas fill`, por defecto). **Rellena** = el atlas lo");
            md.println("encuentra y una regla simple de nombres no.");
            md.println();
            md.printf(Locale.ROOT, "Modelos analizados: **%d** (modelos %d, posers %d, grupos de animaciones %d) · con error: %d%n",
                    rows.size(), assets.geometries.size(), assets.posers.size(), assets.animations.size(), errors);
            md.println();
            md.printf(Locale.ROOT, "Sin analizar: %d parejas poser + modelo cuyo poser no es un JSON (Cobblemon aún tiene algunos"
                    + " escritos en Kotlin) o cuyo modelo no aparece con ese nombre. En el juego (`SkeletonAtlas`) se analizan"
                    + " todos.%n", skipped);
            md.println();
            md.println("| Pieza | Modelos | Rellena |");
            md.println("|---|---:|---:|");
            md.printf(Locale.ROOT, "| cabeza (mira) | %d | %d |%n", withHead, headFill);
            md.printf(Locale.ROOT, "| patas | %d | %d |%n", legs, legFill);
            md.printf(Locale.ROOT, "| brazos | %d | %d |%n", arms, armFill);
            md.printf(Locale.ROOT, "| alas | %d | %d |%n", wings, wingFill);
            md.printf(Locale.ROOT, "| cola | %d | %d |%n", tails, tailFill);
            md.println();
            md.print("Piezas por fuente:");
            for (Map.Entry<SkeletonClassifier.Source, Integer> source : sources.entrySet()) {
                md.print(" " + source.getKey().name().toLowerCase(Locale.ROOT) + " " + source.getValue());
            }
            md.println();
            section(md, "Cabezas que los nombres no encuentran", headFills);
            section(md, "Patas que los nombres no encuentran", legFills);
            section(md, "Brazos que los nombres no encuentran", armFills);
            section(md, "Alas que los nombres no encuentran", wingFills);
            section(md, "Colas que los nombres no encuentran", tailFills);
        }
        System.out.printf(Locale.ROOT, "%d modelos; cabeza %d (+%d), patas %d (+%d), brazos %d (+%d), alas %d (+%d), cola %d (+%d); errores %d%n",
                rows.size(), withHead, headFill, legs, legFill, arms, armFill, wings, wingFill, tails, tailFill, errors);
    }

    private static void section(PrintWriter md, String title, List<String> items) {
        md.println();
        md.println("## " + title + " (" + items.size() + ")");
        md.println();
        if (items.isEmpty()) {
            md.println("Ninguno.");
            return;
        }
        md.println(String.join(", ", items));
    }

    private static String short_(Row row) {
        String model = row.model.substring(row.model.indexOf(':') + 1);
        return model.endsWith(".geo") ? model.substring(0, model.length() - 4) : model;
    }

    private static String bones(List<Part> parts) {
        List<String> names = new ArrayList<>();
        for (Part part : parts) {
            names.add(part.bone());
        }
        return String.join(" ", names);
    }

    private static String parts(List<Part> parts) {
        List<String> names = new ArrayList<>();
        for (Part part : parts) {
            String side = part.side() < 0 ? "-" : part.side() > 0 ? "+" : "0";
            String depth = part.depth() < 0 ? " delante" : part.depth() > 0 ? " detrás" : "";
            names.add(part.bone() + " [" + side + depth + ", " + part.source().name().toLowerCase(Locale.ROOT) + "]");
        }
        return String.join(", ", names);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // ---- Recursos: carpetas, jars y zips ----

    private static final class Assets {
        final Map<String, Map<String, Object>> geometries = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> posers = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> animations = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> resolvers = new LinkedHashMap<>();
        private static final Pattern PATH = Pattern.compile(
                "(?:^|.*/)assets/([^/]+)/bedrock/pokemon/(models|posers|animations|resolvers)/(?:.*/)?([^/]+)\\.json$");

        void load(Path path) throws IOException {
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.walk(path)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        String name = path.relativize(file).toString().replace('\\', '/');
                        if (Files.isRegularFile(file)) {
                            accept(name, () -> Files.readString(file, StandardCharsets.UTF_8));
                        }
                    }
                }
            } else {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                        if (!entry.isDirectory()) {
                            accept(entry.getName(), () -> {
                                try (InputStream in = zip.getInputStream(entry)) {
                                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                                }
                            });
                        }
                    }
                }
            }
        }

        private interface Reader {
            String read() throws IOException;
        }

        private void accept(String name, Reader reader) throws IOException {
            Matcher matcher = PATH.matcher(name);
            if (!matcher.matches()) {
                return;
            }
            String namespace = matcher.group(1);
            String kind = matcher.group(2);
            String file = matcher.group(3);
            Map<String, Object> json;
            try {
                json = map(Json.parse(reader.read()));
            } catch (RuntimeException e) {
                System.err.println("JSON roto: " + name + " (" + e.getMessage() + ")");
                return;
            }
            switch (kind) {
                case "models" -> {
                    for (Object geometry : list(json.get("minecraft:geometry"))) {
                        Map<String, Object> description = map(map(geometry).get("description"));
                        if (description.get("identifier") instanceof String id) {
                            geometries.put(id, map(geometry));
                        }
                    }
                }
                case "posers" -> posers.put(namespace + ":" + file, json);
                case "animations" -> animations.put(file.replace(".animation", ""), json);
                case "resolvers" -> resolvers.put(namespace + ":" + name, json);
                default -> {
                }
            }
        }
    }

    // ---- .geo: huesos en reposo ----

    private static final class Geo {

        /**
         * Huesos con su pivote y la caja de sus cubos en el espacio del modelo, en bloques, Y arriba, cara hacia -Z,
         * con la postura del .geo (los giros de los huesos padres mueven a los hijos, como en el juego).
         */
        static List<BoneInfo> bones(Map<String, Object> geometry) {
            Map<String, Map<String, Object>> bones = new LinkedHashMap<>();
            for (Object bone : list(geometry.get("bones"))) {
                Map<String, Object> json = map(bone);
                if (json.get("name") instanceof String name) {
                    bones.put(name, json);
                }
            }
            Map<String, double[][]> matrices = new LinkedHashMap<>();
            List<BoneInfo> result = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : bones.entrySet()) {
                matrix(entry.getKey(), bones, matrices);
            }
            for (Map.Entry<String, Map<String, Object>> entry : bones.entrySet()) {
                Map<String, Object> bone = entry.getValue();
                String parent = bone.get("parent") instanceof String value && bones.containsKey(value) ? value : null;
                double[] pivot = vector(bone.get("pivot"));
                double[][] parentMatrix = parent == null ? identity() : matrices.get(parent);
                double[] worldPivot = transform(parentMatrix, pivot);
                double[][] own = matrices.get(entry.getKey());
                float[] box = null;
                float volume = 0;
                for (Object cubeJson : list(bone.get("cubes"))) {
                    Map<String, Object> cube = map(cubeJson);
                    double[] origin = vector(cube.get("origin"));
                    double[] size = vector(cube.get("size"));
                    double inflate = number(cube.get("inflate"), 0);
                    double[][] matrix = own;
                    if (cube.get("rotation") != null) {
                        double[] cubePivot = cube.get("pivot") != null ? vector(cube.get("pivot")) : pivot;
                        matrix = multiply(own, around(cubePivot, vector(cube.get("rotation"))));
                    }
                    for (int corner = 0; corner < 8; corner++) {
                        double[] point = {
                                origin[0] - inflate + ((corner & 1) != 0 ? size[0] + 2 * inflate : 0),
                                origin[1] - inflate + ((corner & 2) != 0 ? size[1] + 2 * inflate : 0),
                                origin[2] - inflate + ((corner & 4) != 0 ? size[2] + 2 * inflate : 0)};
                        double[] world = transform(matrix, point);
                        if (box == null) {
                            box = new float[]{(float) world[0], (float) world[1], (float) world[2],
                                    (float) world[0], (float) world[1], (float) world[2]};
                        } else {
                            for (int i = 0; i < 3; i++) {
                                box[i] = Math.min(box[i], (float) world[i]);
                                box[i + 3] = Math.max(box[i + 3], (float) world[i]);
                            }
                        }
                    }
                    volume += (float) (size[0] * size[1] * size[2]);
                }
                if (box != null) {
                    for (int i = 0; i < 6; i++) {
                        box[i] /= 16;
                    }
                }
                result.add(new BoneInfo(entry.getKey(), parent, new float[]{(float) worldPivot[0] / 16,
                        (float) worldPivot[1] / 16, (float) worldPivot[2] / 16}, box, volume / 4096F));
            }
            return result;
        }

        private static double[][] matrix(String name, Map<String, Map<String, Object>> bones,
                                         Map<String, double[][]> done) {
            double[][] cached = done.get(name);
            if (cached != null) {
                return cached;
            }
            Map<String, Object> bone = bones.get(name);
            String parent = bone.get("parent") instanceof String value && bones.containsKey(value)
                    && !value.equals(name) ? value : null;
            // Evita ciclos en modelos rotos
            done.put(name, identity());
            double[][] base = parent == null ? identity() : matrix(parent, bones, done);
            double[][] result = bone.get("rotation") != null
                    ? multiply(base, around(vector(bone.get("pivot")), vector(bone.get("rotation")))) : base;
            done.put(name, result);
            return result;
        }

        /** Giro (grados, X luego Y luego Z como Bedrock) alrededor de un punto. */
        private static double[][] around(double[] pivot, double[] degrees) {
            double[][] rotation = multiply(rotate(2, degrees[2]), multiply(rotate(1, degrees[1]), rotate(0, degrees[0])));
            return multiply(translate(pivot[0], pivot[1], pivot[2]),
                    multiply(rotation, translate(-pivot[0], -pivot[1], -pivot[2])));
        }

        private static double[][] identity() {
            double[][] m = new double[4][4];
            for (int i = 0; i < 4; i++) {
                m[i][i] = 1;
            }
            return m;
        }

        private static double[][] translate(double x, double y, double z) {
            double[][] m = identity();
            m[0][3] = x;
            m[1][3] = y;
            m[2][3] = z;
            return m;
        }

        private static double[][] rotate(int axis, double degrees) {
            double[][] m = identity();
            double c = Math.cos(Math.toRadians(degrees));
            double s = Math.sin(Math.toRadians(degrees));
            int a = (axis + 1) % 3;
            int b = (axis + 2) % 3;
            m[a][a] = c;
            m[a][b] = -s;
            m[b][a] = s;
            m[b][b] = c;
            return m;
        }

        private static double[][] multiply(double[][] x, double[][] y) {
            double[][] m = new double[4][4];
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    for (int k = 0; k < 4; k++) {
                        m[i][j] += x[i][k] * y[k][j];
                    }
                }
            }
            return m;
        }

        private static double[] transform(double[][] m, double[] p) {
            double[] r = new double[3];
            for (int i = 0; i < 3; i++) {
                r[i] = m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3];
            }
            return r;
        }
    }

    // ---- Canales de animación (giro de un hueso a lo largo del tiempo) ----

    private static final class Channel {

        /** Valor de un canal ("rotation": número, texto Molang, [x, y, z] o fotogramas {"0.5": ...}) en un instante. */
        static double[] resolve(Object channel, double time) {
            if (channel instanceof Map<?, ?> frames) {
                if (frames.containsKey("vector")) {
                    return value(frames.get("vector"), time);
                }
                TreeMap<Double, Object> keys = new TreeMap<>();
                for (Map.Entry<?, ?> frame : frames.entrySet()) {
                    try {
                        keys.put(Double.parseDouble(String.valueOf(frame.getKey())), frame.getValue());
                    } catch (NumberFormatException e) {
                        // "lerp_mode" y otras claves que no son instantes
                    }
                }
                if (keys.isEmpty()) {
                    return new double[3];
                }
                Map.Entry<Double, Object> after = keys.higherEntry(time);
                Map.Entry<Double, Object> before = keys.floorEntry(time);
                if (before == null) {
                    return frame(after.getValue(), "pre", time);
                }
                if (after == null) {
                    return frame(before.getValue(), "post", time);
                }
                double[] from = frame(before.getValue(), "post", time);
                double[] to = frame(after.getValue(), "pre", time);
                double ratio = (time - before.getKey()) / (after.getKey() - before.getKey());
                return new double[]{from[0] + (to[0] - from[0]) * ratio, from[1] + (to[1] - from[1]) * ratio,
                        from[2] + (to[2] - from[2]) * ratio};
            }
            return value(channel, time);
        }

        private static double[] frame(Object frame, String side, double time) {
            if (frame instanceof Map<?, ?> map) {
                Object value = map.containsKey(side) ? map.get(side)
                        : map.containsKey("vector") ? map.get("vector") : map.get(side.equals("pre") ? "post" : "pre");
                return value(value, time);
            }
            return value(frame, time);
        }

        private static double[] value(Object value, double time) {
            if (value instanceof List<?> list) {
                double[] result = new double[3];
                for (int i = 0; i < 3 && i < list.size(); i++) {
                    result[i] = scalar(list.get(i), time);
                }
                if (list.size() == 1) {
                    result[1] = result[2] = result[0];
                }
                return result;
            }
            double scalar = scalar(value, time);
            return new double[]{scalar, scalar, scalar};
        }

        private static double scalar(Object value, double time) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String text) {
                return Molang.eval(text, time);
            }
            return 0;
        }
    }

    // ---- Molang: lo que usan las animaciones de Cobblemon ----

    /**
     * Evaluador pequeño de Molang. Las animaciones de Cobblemon 1.8.2 usan q.anim_time, math.sin, math.cos,
     * math.clamp, math.abs y poco más (contado). Lo que no conoce vale 0; math.random devuelve la media (sin ruido
     * para medir amplitudes). Los ángulos de math.sin/cos van en grados, como en Bedrock.
     */
    static final class Molang {
        private final String text;
        private final double time;
        private int pos;

        private Molang(String text, double time) {
            this.text = text.toLowerCase(Locale.ROOT);
            this.time = time;
        }

        static double eval(String text, double time) {
            try {
                Molang parser = new Molang(text, time);
                // Varias sentencias: vale la última ("v.x = 1; return v.x * 2" no aparece en animaciones de huesos)
                double value = parser.ternary();
                parser.skip();
                return Double.isFinite(value) ? value : 0;
            } catch (RuntimeException e) {
                return 0;
            }
        }

        private void skip() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private boolean eat(String token) {
            skip();
            if (text.startsWith(token, pos)) {
                pos += token.length();
                return true;
            }
            return false;
        }

        private double ternary() {
            double condition = or();
            if (eat("?")) {
                double yes = ternary();
                if (!eat(":")) {
                    return condition != 0 ? yes : 0;
                }
                double no = ternary();
                return condition != 0 ? yes : no;
            }
            return condition;
        }

        private double or() {
            double value = and();
            while (eat("||")) {
                double other = and();
                value = value != 0 || other != 0 ? 1 : 0;
            }
            return value;
        }

        private double and() {
            double value = comparison();
            while (eat("&&")) {
                double other = comparison();
                value = value != 0 && other != 0 ? 1 : 0;
            }
            return value;
        }

        private double comparison() {
            double value = sum();
            while (true) {
                if (eat("<=")) {
                    value = value <= sum() ? 1 : 0;
                } else if (eat(">=")) {
                    value = value >= sum() ? 1 : 0;
                } else if (eat("==")) {
                    value = value == sum() ? 1 : 0;
                } else if (eat("!=")) {
                    value = value != sum() ? 1 : 0;
                } else if (eat("<")) {
                    value = value < sum() ? 1 : 0;
                } else if (eat(">")) {
                    value = value > sum() ? 1 : 0;
                } else {
                    return value;
                }
            }
        }

        private double sum() {
            double value = product();
            while (true) {
                if (eat("+")) {
                    value += product();
                } else if (eat("-")) {
                    value -= product();
                } else {
                    return value;
                }
            }
        }

        private double product() {
            double value = unary();
            while (true) {
                if (eat("*")) {
                    value *= unary();
                } else if (eat("/")) {
                    double divisor = unary();
                    value = divisor == 0 ? 0 : value / divisor;
                } else {
                    return value;
                }
            }
        }

        private double unary() {
            if (eat("-")) {
                return -unary();
            }
            if (eat("+")) {
                return unary();
            }
            if (eat("!")) {
                return unary() == 0 ? 1 : 0;
            }
            return primary();
        }

        private double primary() {
            skip();
            if (eat("(")) {
                double value = ternary();
                eat(")");
                return value;
            }
            int start = pos;
            if (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                    pos++;
                }
                // "1.5f"
                if (pos < text.length() && text.charAt(pos) == 'f') {
                    pos++;
                    return Double.parseDouble(text.substring(start, pos - 1));
                }
                return Double.parseDouble(text.substring(start, pos));
            }
            while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_'
                    || text.charAt(pos) == '.')) {
                pos++;
            }
            String name = text.substring(start, pos);
            if (name.isEmpty()) {
                throw new IllegalStateException("Molang: " + text);
            }
            List<Double> args = new ArrayList<>();
            if (eat("(")) {
                if (!eat(")")) {
                    do {
                        args.add(ternary());
                    } while (eat(","));
                    eat(")");
                }
            }
            return call(name, args);
        }

        private double call(String name, List<Double> args) {
            double a = args.isEmpty() ? 0 : args.get(0);
            double b = args.size() > 1 ? args.get(1) : 0;
            double c = args.size() > 2 ? args.get(2) : 0;
            return switch (name) {
                case "q.anim_time", "query.anim_time", "q.life_time", "query.life_time" -> time;
                case "math.pi" -> Math.PI;
                case "math.sin" -> Math.sin(Math.toRadians(a));
                case "math.cos" -> Math.cos(Math.toRadians(a));
                case "math.asin" -> Math.toDegrees(Math.asin(a));
                case "math.acos" -> Math.toDegrees(Math.acos(a));
                case "math.atan" -> Math.toDegrees(Math.atan(a));
                case "math.atan2" -> Math.toDegrees(Math.atan2(a, b));
                case "math.abs" -> Math.abs(a);
                case "math.clamp" -> Math.max(b, Math.min(c, a));
                case "math.min" -> Math.min(a, b);
                case "math.max" -> Math.max(a, b);
                case "math.mod" -> b == 0 ? 0 : a % b;
                case "math.round" -> Math.round(a);
                case "math.floor" -> Math.floor(a);
                case "math.ceil" -> Math.ceil(a);
                case "math.trunc" -> a < 0 ? Math.ceil(a) : Math.floor(a);
                case "math.pow" -> Math.pow(a, b);
                case "math.sqrt" -> Math.sqrt(a);
                case "math.exp" -> Math.exp(a);
                case "math.ln" -> Math.log(a);
                case "math.lerp" -> a + (b - a) * c;
                case "math.random", "math.random_integer" -> (a + b) / 2;
                default -> 0;
            };
        }
    }

    // ---- JSON ----

    /** Lector de JSON sin dependencias, tolerante como el de Cobblemon (comentarios y comas de sobra). */
    static final class Json {
        private final String text;
        private int pos;

        private Json(String text) {
            this.text = text;
        }

        static Object parse(String text) {
            Json json = new Json(text);
            json.skip();
            if (json.pos < text.length() && text.charAt(json.pos) == '﻿') {
                json.pos++;
            }
            return json.value();
        }

        private void skip() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isWhitespace(c) || c == '﻿') {
                    pos++;
                } else if (text.startsWith("//", pos)) {
                    int end = text.indexOf('\n', pos);
                    pos = end < 0 ? text.length() : end;
                } else if (text.startsWith("/*", pos)) {
                    int end = text.indexOf("*/", pos + 2);
                    pos = end < 0 ? text.length() : end + 2;
                } else {
                    return;
                }
            }
        }

        private Object value() {
            skip();
            if (pos >= text.length()) {
                throw new IllegalStateException("fin inesperado");
            }
            char c = text.charAt(pos);
            if (c == '{') {
                pos++;
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    skip();
                    if (text.charAt(pos) == '}') {
                        pos++;
                        return map;
                    }
                    String key = string();
                    skip();
                    expect(':');
                    map.put(key, value());
                    skip();
                    if (text.charAt(pos) == ',') {
                        pos++;
                    }
                }
            }
            if (c == '[') {
                pos++;
                List<Object> list = new ArrayList<>();
                while (true) {
                    skip();
                    if (text.charAt(pos) == ']') {
                        pos++;
                        return list;
                    }
                    list.add(value());
                    skip();
                    if (text.charAt(pos) == ',') {
                        pos++;
                    }
                }
            }
            if (c == '"') {
                return string();
            }
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            int start = pos;
            while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalStateException("carácter inesperado '" + c + "' en " + pos);
            }
            return Double.parseDouble(text.substring(start, pos));
        }

        private String string() {
            skip();
            expect('"');
            StringBuilder out = new StringBuilder();
            while (text.charAt(pos) != '"') {
                char c = text.charAt(pos++);
                if (c == '\\') {
                    char escaped = text.charAt(pos++);
                    switch (escaped) {
                        case 'n' -> out.append('\n');
                        case 't' -> out.append('\t');
                        case 'r' -> out.append('\r');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'u' -> {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> out.append(escaped);
                    }
                } else {
                    out.append(c);
                }
            }
            pos++;
            return out.toString();
        }

        private void expect(char c) {
            if (pos >= text.length() || text.charAt(pos) != c) {
                throw new IllegalStateException("se esperaba '" + c + "' en " + pos);
            }
            pos++;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double[] vector(Object value) {
        double[] result = new double[3];
        List<?> list = list(value);
        for (int i = 0; i < 3 && i < list.size(); i++) {
            result[i] = number(list.get(i), 0);
        }
        return result;
    }
}
