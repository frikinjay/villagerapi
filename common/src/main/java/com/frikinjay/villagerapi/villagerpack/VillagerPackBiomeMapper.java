package com.frikinjay.villagerapi.villagerpack;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackBiomeMapper {

    public static void registerBiomeMappings(Map<String, JsonObject> data, MinecraftServer server) {
        if (data.isEmpty()) {
            LOGGER.debug("No biome mappings to register");
            return;
        }

        BiomeMappingCache.clear();
        BiomeTradesCache.clear();

        Registry<VillagerType> villagerTypeRegistry = server.registryAccess()
                .lookupOrThrow(Registries.VILLAGER_TYPE);

        int registered = 0;
        for (Map.Entry<String, JsonObject> entry : data.entrySet()) {
            try {
                JsonObject mappings = entry.getValue();
                if (!mappings.has("biomes")) {
                    LOGGER.warn("Biome mapping file {} has no 'biomes' field", entry.getKey());
                    continue;
                }

                JsonObject biomes = mappings.getAsJsonObject("biomes");
                for (String biomeKey : biomes.keySet()) {
                    String villagerTypeId = biomes.get(biomeKey).getAsString();

                    ResourceKey<Biome> biomeResourceKey = ResourceKey.create(
                            Registries.BIOME,
                            Identifier.parse(biomeKey)
                    );

                    Identifier villagerTypeLocation = Identifier.parse(villagerTypeId);
                    ResourceKey<VillagerType> villagerTypeKey = ResourceKey.create(
                            Registries.VILLAGER_TYPE,
                            villagerTypeLocation
                    );

                    Holder.Reference<VillagerType> villagerTypeHolder = villagerTypeRegistry
                            .get(villagerTypeLocation)
                            .orElse(null);

                    if (villagerTypeHolder == null) {
                        LOGGER.warn("Villager type {} not found for biome mapping", villagerTypeId);
                        continue;
                    }

                    BiomeMappingCache.registerMapping(biomeResourceKey, villagerTypeKey);
                    registered++;
                    LOGGER.debug("Mapped biome {} to villager type {}", biomeKey, villagerTypeId);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process biome mapping: {}", entry.getKey(), e);
            }
        }

        LOGGER.info("Registered {} biome-to-villager-type mappings from villagerpacks", registered);
    }
}