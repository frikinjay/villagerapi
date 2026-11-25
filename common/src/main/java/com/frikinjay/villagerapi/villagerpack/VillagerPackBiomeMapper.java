package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;

import java.util.*;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackBiomeMapper {

    public static void registerBiomeMappings(MinecraftServer server) {
        Registry<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        Registry<VillagerType> villagerTypes = server.registryAccess().lookupOrThrow(Registries.VILLAGER_TYPE);

        VillagerPackLoader loader = new VillagerPackLoader(CommonPlatformHelper.getGameDirectory());
        List<VillagerPackLoader.VillagerPack> packs = loader.getDiscoveredPacks();
        List<PackMappings> packMappings = new ArrayList<>();

        try {
            for (VillagerPackLoader.VillagerPack pack : packs) {
                PackMappings mappings = new PackMappings(pack.getName());
                VillagerPackCodecs.forEachJson(pack.getRootPath().resolve("villagers/biome_mappings"), "biome mapping", json ->
                        readMappings(json, pack, mappings, biomes, villagerTypes));
                if (!mappings.entries.isEmpty()) {
                    packMappings.add(mappings);
                }
            }
        } finally {
            for (VillagerPackLoader.VillagerPack pack : packs) {
                pack.close();
            }
        }

        Set<ResourceKey<Biome>> allBiomes = new LinkedHashSet<>();
        for (PackMappings mappings : packMappings) {
            allBiomes.addAll(mappings.entries.keySet());
        }

        Map<ResourceKey<Biome>, BiomeMappingCache.Candidates> result = new HashMap<>();
        for (ResourceKey<Biome> biome : allBiomes) {
            List<BiomeMappingCache.WeightedType> types = new ArrayList<>();
            boolean replaced = false;

            for (PackMappings mappings : packMappings) {
                List<BiomeMappingCache.WeightedType> entries = mappings.entries.get(biome);
                if (entries == null) continue;

                for (BiomeMappingCache.WeightedType entry : entries) {
                    mergeInto(types, entry);
                }

                if (mappings.replacing.contains(biome)) {
                    replaced = true;
                    break;
                }
            }

            if (!types.isEmpty()) {
                result.put(biome, new BiomeMappingCache.Candidates(List.copyOf(types), !replaced));
            }
        }

        BiomeMappingCache.set(result);
        LOGGER.info("Registered villager type mappings for {} biome(s) from villagerpacks", result.size());
    }

    private static void mergeInto(List<BiomeMappingCache.WeightedType> types, BiomeMappingCache.WeightedType entry) {
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).type().equals(entry.type())) {
                types.set(i, new BiomeMappingCache.WeightedType(entry.type(), types.get(i).weight() + entry.weight()));
                return;
            }
        }
        types.add(entry);
    }

    private static void readMappings(JsonObject json, VillagerPackLoader.VillagerPack pack, PackMappings mappings,
                                     Registry<Biome> biomes, Registry<VillagerType> villagerTypes) {
        if (!json.has("biomes")) {
            LOGGER.warn("Biome mapping file in villagerpack '{}' has no 'biomes' field", pack.getName());
            return;
        }

        JsonElement biomesElement = json.get("biomes");

        if (biomesElement.isJsonObject()) {
            JsonObject legacy = biomesElement.getAsJsonObject();
            for (String biome : legacy.keySet()) {
                if (!legacy.get(biome).isJsonPrimitive()) continue;
                addEntry(mappings, pack, biome, legacy.get(biome).getAsString(), 1, true, biomes, villagerTypes);
            }
            return;
        }

        if (!biomesElement.isJsonArray()) {
            LOGGER.warn("'biomes' in a biome mapping file of villagerpack '{}' must be a list", pack.getName());
            return;
        }

        for (JsonElement element : biomesElement.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();

            String biome = entry.has("biome") ? entry.get("biome").getAsString() : null;
            String type = entry.has("villager_type") ? entry.get("villager_type").getAsString()
                    : entry.has("type") ? entry.get("type").getAsString() : null;

            if (biome == null || type == null) {
                LOGGER.warn("A biome mapping in villagerpack '{}' needs 'biome' and 'villager_type', skipping it", pack.getName());
                continue;
            }

            int weight = entry.has("weight") && entry.get("weight").isJsonPrimitive() ? entry.get("weight").getAsInt() : 1;
            boolean replace = entry.has("replace") && entry.get("replace").isJsonPrimitive() && entry.get("replace").getAsBoolean();

            if (weight < 1) {
                LOGGER.warn("Biome mapping '{}' -> '{}' in villagerpack '{}' has a weight below 1, skipping it", biome, type, pack.getName());
                continue;
            }

            addEntry(mappings, pack, biome, type, weight, replace, biomes, villagerTypes);
        }
    }

    private static void addEntry(PackMappings mappings, VillagerPackLoader.VillagerPack pack, String biome, String type, int weight,
                                 boolean replace, Registry<Biome> biomes, Registry<VillagerType> villagerTypes) {
        String typeValue = type.trim();
        Identifier typeId = Identifier.parse(typeValue.contains(":") ? typeValue : pack.getNamespace() + ":" + typeValue);
        ResourceKey<VillagerType> typeKey = ResourceKey.create(Registries.VILLAGER_TYPE, typeId);

        if (villagerTypes.get(typeKey).isEmpty()) {
            LOGGER.warn("Villager type {} used in a biome mapping of villagerpack '{}' doesn't exist, skipping it", typeId, pack.getName());
            return;
        }

        List<ResourceKey<Biome>> resolved = resolveBiomes(biome.trim(), biomes);
        if (resolved.isEmpty()) {
            LOGGER.warn("Biome or biome tag '{}' in villagerpack '{}' doesn't match any biome", biome, pack.getName());
            return;
        }

        for (ResourceKey<Biome> biomeKey : resolved) {
            mappings.entries.computeIfAbsent(biomeKey, k -> new ArrayList<>()).add(new BiomeMappingCache.WeightedType(typeKey, weight));
            if (replace) {
                mappings.replacing.add(biomeKey);
            }
        }
    }

    private static List<ResourceKey<Biome>> resolveBiomes(String value, Registry<Biome> biomes) {
        List<ResourceKey<Biome>> result = new ArrayList<>();

        if (value.startsWith("#")) {
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, Identifier.parse(value.substring(1)));
            biomes.get(tag).ifPresent(set -> {
                for (Holder<Biome> holder : (HolderSet<Biome>) set) {
                    holder.unwrapKey().ifPresent(result::add);
                }
            });
            return result;
        }

        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.parse(value));
        if (biomes.get(key).isPresent()) {
            result.add(key);
        }
        return result;
    }

    private static final class PackMappings {
        private final String packKey;
        private final Map<ResourceKey<Biome>, List<BiomeMappingCache.WeightedType>> entries = new LinkedHashMap<>();
        private final Set<ResourceKey<Biome>> replacing = new HashSet<>();

        private PackMappings(String packKey) {
            this.packKey = packKey;
        }
    }
}