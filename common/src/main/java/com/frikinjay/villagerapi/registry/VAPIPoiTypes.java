package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.mixin.PoiTypesInvoker;
import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.villagerpack.VillagerPackCodecs;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VAPIPoiTypes {

    private static final Map<String, Supplier<PoiType>> DYNAMIC_POI_TYPES = new HashMap<>();
    private static final Map<String, Identifier> POI_BLOCK_IDS = new HashMap<>();
    private static final Set<Identifier> PACK_REGISTERED_POI_IDS = new HashSet<>();

    public static void init() {}

    public static Identifier registerFromPack(String name, JsonObject json) {
        try {
            VillagerPackCodecs.PoiTypeData poiData = VillagerPackCodecs.parsePoiType(json);
            String registryName = name.replace("/", "_");

            String namespace = poiData.namespace() != null ? poiData.namespace() : VillagerAPI.MOD_ID;

            Identifier poiId = Identifier.fromNamespaceAndPath(
                    namespace,
                    registryName
            );

            POI_BLOCK_IDS.put(name, poiData.block());

            Supplier<PoiType> poiType = CommonPlatformHelper.registerPoiType(
                    namespace,
                    registryName,
                    () -> {
                        Block block = VillagerAPI.unwrapHolder(BuiltInRegistries.BLOCK.get(poiData.block()));
                        if (block == null || block == Blocks.AIR) {
                            LOGGER.warn("Block {} not found for POI type {}, using empty block state set",poiData.block(), registryName);
                            return Collections.emptySet();
                        }
                        return PoiTypesInvoker.invokeGetBlockStates(block);
                    }
            );

            DYNAMIC_POI_TYPES.put(name, poiType);
            PACK_REGISTERED_POI_IDS.add(poiId);

            LOGGER.info("Registered POI type from pack: {} with namespace: {}", name, namespace);
            return poiId;
        } catch (Exception e) {
            LOGGER.error("Failed to register POI type from pack: {}", name, e);
            return null;
        }
    }

    /**
     * Validate all registered POI types after blocks are loaded
     */
    public static void validatePoiTypes() {
        int valid = 0;
        int invalid = 0;

        for (Map.Entry<String, Identifier> entry : POI_BLOCK_IDS.entrySet()) {
            String name = entry.getKey();
            Identifier blockId = entry.getValue();

            Block block = BuiltInRegistries.BLOCK.get(blockId)
                    .map(Holder.Reference::value)
                    .orElse(null);
            if (block == null || block == Blocks.AIR) {
                invalid++;
                LOGGER.warn("POI type '{}' references missing block {} - profession will not work", name, blockId);
            } else {
                valid++;
                LOGGER.debug("POI type '{}' validated with block {}", name, blockId);
            }
        }

        if (valid > 0 || invalid > 0) {
            LOGGER.info("POI type validation: {} valid, {} invalid", valid, invalid);
        }
    }

    public static boolean isPackRegisteredPoi(Identifier id) {
        return PACK_REGISTERED_POI_IDS.contains(id);
    }

    public static Supplier<PoiType> getDynamicPoiType(String name) {
        return DYNAMIC_POI_TYPES.get(name);
    }

    public static Map<String, Supplier<PoiType>> getDynamicPoiTypes() {
        return new HashMap<>(DYNAMIC_POI_TYPES);
    }
}