package com.frikinjay.villagerapi.villagerpack;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class BiomeMappingCache {
    private static final Map<ResourceKey<Biome>, ResourceKey<VillagerType>> BIOME_MAPPINGS = new HashMap<>();

    public static void registerMapping(ResourceKey<Biome> biome, ResourceKey<VillagerType> villagerTypeKey) {
        BIOME_MAPPINGS.put(biome, villagerTypeKey);
        LOGGER.debug("Registered biome mapping: {} -> {}", biome.identifier(), villagerTypeKey.identifier());
    }

    public static ResourceKey<VillagerType> getVillagerTypeForBiome(ResourceKey<Biome> biome) {
        return BIOME_MAPPINGS.get(biome);
    }

    public static void clear() {
        BIOME_MAPPINGS.clear();
        LOGGER.info("Cleared biome mappings cache");
    }

    public static Map<ResourceKey<Biome>, ResourceKey<VillagerType>> getAllMappings() {
        return new HashMap<>(BIOME_MAPPINGS);
    }

    public static int size() {
        return BIOME_MAPPINGS.size();
    }
}