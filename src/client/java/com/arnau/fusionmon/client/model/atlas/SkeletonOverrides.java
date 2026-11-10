package com.arnau.fusionmon.client.model.atlas;

import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Correction;
import com.arnau.fusionmon.client.model.atlas.SkeletonClassifier.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Correcciones del atlas de esqueletos en resource packs: para los modelos que ni los nombres ni las animaciones
 * aciertan, quien hace el pack dice qué es cada hueso. Archivos assets/&lt;cualquier namespace&gt;/fusionmon/skeletons/
 * &lt;nombre&gt;.json:
 * <pre>
 * {
 *   "models": ["cobblemon:skeledirge.geo"],
 *   "head": "head_top",
 *   "legs": ["leg_left", "leg_right"],
 *   "arms": [],
 *   "wings": ["wing_left", "wing_right"],
 *   "tail": ["tail"]
 * }
 * </pre>
 * Lo que no se menciona se deduce como siempre; una lista vacía dice que no tiene esa pieza; "head": "" dice que no
 * tiene cabeza. Mandan sobre los nombres y las animaciones (salvo con /fusionvisual atlas off). Guía:
 * docs/skeleton-atlas.md.
 */
public final class SkeletonOverrides {

    public static final String DIRECTORY = "fusionmon/skeletons";

    private static Map<ResourceLocation, Correction> overrides = Map.of();

    private SkeletonOverrides() {
    }

    /** Lo que dice algún pack del modelo, o null. */
    public static Correction get(ResourceLocation model) {
        return model == null ? null : overrides.get(model);
    }

    public static int size() {
        return overrides.size();
    }

    /** Al recargar recursos (F3+T, otro pack): se vuelven a leer todos. */
    public static void reload(ResourceManager resources) {
        Map<ResourceLocation, Correction> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> file
                : resources.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = file.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                Correction override = parse(json);
                for (JsonElement model : json.getAsJsonArray("models")) {
                    loaded.put(ResourceLocation.parse(model.getAsString()), override);
                }
            } catch (Exception e) {
                // Un archivo mal escrito no debe tumbar la recarga de recursos: se avisa y se sigue con los demás
                Fusionmon.LOGGER.warn("Fusionmon: no se ha podido leer {} (atlas de esqueletos)", file.getKey(), e);
            }
        }
        overrides = Map.copyOf(loaded);
        if (!loaded.isEmpty()) {
            Fusionmon.LOGGER.info("Fusionmon: {} modelos corregidos por resource packs en el atlas de esqueletos",
                    loaded.size());
        }
        SkeletonAtlas.clear();
    }

    static Correction parse(JsonObject json) {
        String head = json.has("head") && json.get("head").isJsonPrimitive() ? json.get("head").getAsString() : null;
        Map<Role, List<String>> parts = new EnumMap<>(Role.class);
        read(json, "legs", Role.LEG, parts);
        read(json, "arms", Role.ARM, parts);
        read(json, "wings", Role.WING, parts);
        read(json, "tail", Role.TAIL, parts);
        return new Correction(head, parts);
    }

    private static void read(JsonObject json, String key, Role role, Map<Role, List<String>> parts) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return;
        }
        JsonArray array = json.getAsJsonArray(key);
        List<String> bones = new ArrayList<>();
        for (JsonElement bone : array) {
            bones.add(bone.getAsString());
        }
        parts.put(role, List.copyOf(bones));
    }
}
