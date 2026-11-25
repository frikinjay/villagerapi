package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.mixin.PoiTypesInvoker;
import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.villagerpack.VillagerPackCodecs;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VAPIPoiTypes {

    private static final Map<String, Supplier<PoiType>> DYNAMIC_POI_TYPES = new HashMap<>();
    private static final Map<String, Identifier> POI_BLOCK_IDS = new HashMap<>();
    private static final Map<String, Identifier> POI_IDS = new HashMap<>();
    private static final Set<Identifier> PACK_REGISTERED_POI_IDS = new HashSet<>();
    private static final Set<String> PENDING_BLOCK_LINKS = new LinkedHashSet<>();

    public static void init() {}

    public static Identifier registerFromPack(String name, JsonObject json) {
        try {
            VillagerPackCodecs.PoiTypeData poiData = VillagerPackCodecs.parsePoiType(json);
            String registryName = name.replace("/", "_");
            String namespace = poiData.namespace() != null ? poiData.namespace() : VillagerAPI.MOD_ID;

            Identifier poiId = Identifier.fromNamespaceAndPath(namespace, registryName);

            POI_BLOCK_IDS.put(name, poiData.block());
            POI_IDS.put(name, poiId);

            Supplier<PoiType> poiType = CommonPlatformHelper.registerPoiType(
                    namespace,
                    registryName,
                    () -> {
                        Block block = findBlock(poiData.block());
                        if (block == null) {
                            PENDING_BLOCK_LINKS.add(name);
                            LOGGER.debug("Block {} for POI type {} is not registered yet, it will be linked as soon as it is registered",
                                    poiData.block(), registryName);
                            return Collections.emptySet();
                        }
                        PENDING_BLOCK_LINKS.remove(name);
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

    public static void validatePoiTypes() {
        linkPendingBlocks();

        int valid = 0;
        int invalid = 0;

        for (Map.Entry<String, Identifier> entry : POI_BLOCK_IDS.entrySet()) {
            String name = entry.getKey();
            Identifier blockId = entry.getValue();

            if (findBlock(blockId) == null) {
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

    public static synchronized void onBlockRegistered(Identifier blockId) {
        if (PENDING_BLOCK_LINKS.isEmpty() || blockId == null) return;

        for (String name : new ArrayList<>(PENDING_BLOCK_LINKS)) {
            if (blockId.equals(POI_BLOCK_IDS.get(name))) {
                linkPoi(name);
            }
        }
    }

    private static synchronized void linkPendingBlocks() {
        if (PENDING_BLOCK_LINKS.isEmpty()) return;

        for (String name : new ArrayList<>(PENDING_BLOCK_LINKS)) {
            linkPoi(name);
        }
    }

    private static void linkPoi(String name) {
        Identifier blockId = POI_BLOCK_IDS.get(name);
        Identifier poiId = POI_IDS.get(name);
        Block block = findBlock(blockId);

        if (block == null || poiId == null) {
            return;
        }

        Optional<Holder.Reference<PoiType>> holder = BuiltInRegistries.POINT_OF_INTEREST_TYPE
                .get(ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, poiId));

        if (holder.isEmpty()) {
            LOGGER.debug("POI type {} is not registered yet, it will be linked to block {} later", poiId, blockId);
            return;
        }

        Map<BlockState, Holder<PoiType>> typeByState = PoiTypesInvoker.getTypeByState();
        Set<BlockState> states = new HashSet<>();
        for (BlockState state : PoiTypesInvoker.invokeGetBlockStates(block)) {
            Holder<PoiType> existing = typeByState.get(state);
            if (existing == null) {
                states.add(state);
            } else if (!existing.unwrapKey().map(key -> key.identifier().equals(poiId)).orElse(false)) {
                LOGGER.warn("Block state {} already belongs to POI type {}, it can't also be used by {}",
                        state, existing.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown"), poiId);
            }
        }

        if (!states.isEmpty()) {
            PoiTypesInvoker.invokeRegisterBlockStates(holder.get(), states);
        }

        PENDING_BLOCK_LINKS.remove(name);
        LOGGER.info("Linked POI type {} to block {}", poiId, blockId);
    }

    private static Block findBlock(Identifier blockId) {
        if (blockId == null) return null;
        Block block = BuiltInRegistries.BLOCK.get(blockId).map(Holder.Reference::value).orElse(null);
        return block == null || block == Blocks.AIR ? null : block;
    }

    public static boolean isPackRegisteredPoi(Identifier id) {
        return PACK_REGISTERED_POI_IDS.contains(id);
    }

    public static ResourceKey<PoiType> getPoiKey(String name) {
        Identifier id = POI_IDS.get(name);
        return id == null ? null : ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, id);
    }

    public static Supplier<PoiType> getDynamicPoiType(String name) {
        return DYNAMIC_POI_TYPES.get(name);
    }

    public static Map<String, Supplier<PoiType>> getDynamicPoiTypes() {
        return new HashMap<>(DYNAMIC_POI_TYPES);
    }
}