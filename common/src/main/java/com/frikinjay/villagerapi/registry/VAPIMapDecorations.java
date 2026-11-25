package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.villagerpack.VillagerPackCodecs;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VAPIMapDecorations {

    private static final Map<String, Supplier<MapDecorationType>> DYNAMIC_MAP_DECORATIONS = new HashMap<>();

    public static void init() { }

    public static void registerFromPack(String name, JsonObject json) {
        try {
            VillagerPackCodecs.StructureTagData decorationData = VillagerPackCodecs.parseStructureTag(json);

            String rawDecoration = decorationData.decoration();
            String namespace = decorationData.namespace() != null ? decorationData.namespace() : VillagerAPI.MOD_ID;

            String decorationName = rawDecoration;
            if (rawDecoration.contains(":")) {
                String[] split = rawDecoration.split(":");
                namespace = split[0];
                decorationName = split[1];
            }

            String registryName = decorationName.replace("/", "_");
            Identifier decorationId = Identifier.fromNamespaceAndPath(namespace, decorationName);

            Supplier<MapDecorationType> type;
            Optional<Holder.Reference<MapDecorationType>> existing = BuiltInRegistries.MAP_DECORATION_TYPE.get(decorationId);
            if (existing.isPresent()) {
                MapDecorationType existingType = VillagerAPI.unwrapHolder(existing);
                type = () -> existingType;
                LOGGER.debug("Reusing existing map decoration type '{}' instead of re-registering it", decorationId);
            } else {
                type = CommonPlatformHelper.registerMapDecorationType(
                        namespace,
                        registryName,
                        () -> new MapDecorationType(decorationId, true, true)
                );
            }

            String fullKey = namespace + ":" + registryName;
            DYNAMIC_MAP_DECORATIONS.put(fullKey, type);
            DYNAMIC_MAP_DECORATIONS.put(registryName, type);

            LOGGER.info("Registered map decoration type from pack: {} -> {} (keys: {}, {})",
                    decorationName, registryName, fullKey, registryName);
        } catch (Exception e) {
            LOGGER.error("Failed to register map decoration type from structure tag json of pack: {}", name, e);
        }
    }

    public static Supplier<MapDecorationType> getDynamicMapDecorationType(String name) {
        Supplier<MapDecorationType> result = DYNAMIC_MAP_DECORATIONS.get(name);
        if (result != null) {
            return result;
        }

        String convertedName = name.replace("/", "_");
        result = DYNAMIC_MAP_DECORATIONS.get(convertedName);
        if (result != null) {
            return result;
        }

        if (!name.contains(":")) {
            String namespacedKey = VillagerAPI.MOD_ID + ":" + convertedName;
            result = DYNAMIC_MAP_DECORATIONS.get(namespacedKey);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    public static Map<String, Supplier<MapDecorationType>> getDynamicMapDecorationTypes() {
        return new HashMap<>(DYNAMIC_MAP_DECORATIONS);
    }

    public static int hexToInt(String hexColor) {
        if (hexColor.startsWith("#")) {
            hexColor = hexColor.substring(1);
        }
        return Integer.parseInt(hexColor, 16);
    }
}