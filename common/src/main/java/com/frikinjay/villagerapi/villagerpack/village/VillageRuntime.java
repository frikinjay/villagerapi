package com.frikinjay.villagerapi.villagerpack.village;

import com.frikinjay.villagerapi.mixin.SinglePoolElementAccessor;
import com.frikinjay.villagerapi.mixin.StructureSetAccessor;
import com.frikinjay.villagerapi.mixin.StructureTemplatePoolAccessor;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public final class VillageRuntime {

    private static final Identifier VILLAGES_STRUCTURE_SET = Identifier.withDefaultNamespace("villages");
    private static final ResourceKey<StructureProcessorList> EMPTY_PROCESSORS =
            ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace("empty"));

    private VillageRuntime() {}

    public static void onServerStarting(MinecraftServer server) {
        VillageGenerator.RuntimeData data = VillageGenerator.getRuntimeData();

        try {
            addToVillagesStructureSet(server, data);
        } catch (Exception e) {
            LOGGER.error("Failed to add villagerpack village types to {}", VILLAGES_STRUCTURE_SET, e);
        }

        try {
            addRuntimeHouses(server, data);
        } catch (Exception e) {
            LOGGER.error("Failed to add villagerpack houses to existing villages", e);
        }

        try {
            reportOverrides(server.getResourceManager(), data);
        } catch (Exception e) {
            LOGGER.debug("Could not check generated villages for overrides", e);
        }
    }

    private static void addToVillagesStructureSet(MinecraftServer server, VillageGenerator.RuntimeData data) {
        if (data.villageSetEntries().isEmpty()) return;

        Registry<StructureSet> structureSets = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        Registry<Structure> structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        StructureSet villages = structureSets.getValue(VILLAGES_STRUCTURE_SET);

        if (villages == null) {
            LOGGER.warn("Structure set {} doesn't exist, villagerpack village types won't generate", VILLAGES_STRUCTURE_SET);
            return;
        }

        List<StructureSet.StructureSelectionEntry> entries = new ArrayList<>(villages.structures());
        int added = 0;

        for (VillageGenerator.RuntimeData.VillageSetEntry entry : data.villageSetEntries()) {
            Optional<Holder.Reference<Structure>> structure = structures.get(ResourceKey.create(Registries.STRUCTURE, entry.structure()));
            if (structure.isEmpty()) {
                LOGGER.warn("Village type {} isn't registered as a structure, it won't generate. Check the log for errors loading it", entry.structure());
                continue;
            }

            boolean present = entries.stream().anyMatch(existing -> existing.structure().is(entry.structure()));
            if (present) {
                LOGGER.debug("Village type {} is already part of {}", entry.structure(), VILLAGES_STRUCTURE_SET);
                continue;
            }

            entries.add(StructureSet.entry(structure.get(), entry.weight()));
            added++;
        }

        if (added > 0) {
            ((StructureSetAccessor) (Object) villages).villagerapi$setStructures(List.copyOf(entries));
            LOGGER.info("Added {} villagerpack village type(s) to {}", added, VILLAGES_STRUCTURE_SET);
        }
    }

    private static void addRuntimeHouses(MinecraftServer server, VillageGenerator.RuntimeData data) {
        if (data.runtimeHouses().isEmpty()) return;

        Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        Registry<StructureProcessorList> processorLists = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
        Map<Identifier, String> poolPrefixes = new HashMap<>();
        int added = 0;

        for (VillageGenerator.RuntimeData.RuntimeHouse house : data.runtimeHouses()) {
            String prefix = poolPrefixes.computeIfAbsent(house.villageType(), id -> findPoolPrefix(server.getResourceManager(), id));
            if (prefix == null) {
                LOGGER.warn("Couldn't find the pools of village type {}, house {} was not added", house.villageType(), house.location());
                continue;
            }

            Identifier poolId = Identifier.parse(prefix + (house.zombie() ? "zombie/houses" : "houses"));
            StructureTemplatePool pool = pools.getValue(poolId);
            if (pool == null) {
                LOGGER.warn("Village type {} has no pool {}, house {} was not added", house.villageType(), poolId, house.location());
                continue;
            }

            StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;
            boolean present = accessor.villagerapi$getRawTemplates().stream()
                    .anyMatch(pair -> pair.getFirst() instanceof SinglePoolElement single && house.location().equals(templateLocation(single)));
            if (present) continue;

            Holder<StructureProcessorList> processors = resolveProcessors(house, accessor, processorLists);
            StructurePoolElement element = (house.legacy()
                    ? StructurePoolElement.legacy(house.location().toString(), processors)
                    : StructurePoolElement.single(house.location().toString(), processors))
                    .apply(StructureTemplatePool.Projection.RIGID);

            List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(accessor.villagerapi$getRawTemplates());
            raw.add(Pair.of(element, house.weight()));
            accessor.villagerapi$setRawTemplates(raw);
            for (int i = 0; i < house.weight(); i++) {
                accessor.villagerapi$getTemplates().add(element);
            }
            added++;
        }

        if (added > 0) {
            LOGGER.info("Added {} villagerpack house(s) to existing village pools", added);
        }
    }

    private static Holder<StructureProcessorList> resolveProcessors(VillageGenerator.RuntimeData.RuntimeHouse house,
                                                                   StructureTemplatePoolAccessor pool,
                                                                   Registry<StructureProcessorList> processorLists) {
        if (house.processors() != null) {
            ResourceKey<StructureProcessorList> key = ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.parse(house.processors()));
            Optional<Holder.Reference<StructureProcessorList>> holder = processorLists.get(key);
            if (holder.isPresent()) return holder.get();
            LOGGER.warn("Processor list {} for house {} doesn't exist, using the pool's default", house.processors(), house.location());
        }

        for (Pair<StructurePoolElement, Integer> pair : pool.villagerapi$getRawTemplates()) {
            if (pair.getFirst() instanceof SinglePoolElement single) {
                return ((SinglePoolElementAccessor) single).villagerapi$getProcessors();
            }
        }

        return processorLists.getOrThrow(EMPTY_PROCESSORS);
    }

    private static Identifier templateLocation(SinglePoolElement element) {
        try {
            return element.getTemplateLocation();
        } catch (Exception e) {
            return null;
        }
    }

    private static String findPoolPrefix(ResourceManager resourceManager, Identifier structureId) {
        Identifier file = Identifier.fromNamespaceAndPath(structureId.getNamespace(), "worldgen/structure/" + structureId.getPath() + ".json");
        Optional<Resource> resource = resourceManager.getResource(file);
        if (resource.isEmpty()) return null;

        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonObject() || !json.getAsJsonObject().has("start_pool")) return null;

            Identifier startPool = Identifier.parse(json.getAsJsonObject().get("start_pool").getAsString());
            int lastSlash = startPool.getPath().lastIndexOf('/');
            return lastSlash <= 0 ? null : startPool.getNamespace() + ":" + startPool.getPath().substring(0, lastSlash + 1);
        } catch (Exception e) {
            LOGGER.warn("Failed to read structure {}", file, e);
            return null;
        }
    }

    private static void reportOverrides(ResourceManager resourceManager, VillageGenerator.RuntimeData data) {
        Map<String, Map<String, List<Identifier>>> overriddenGenerated = new TreeMap<>();
        for (Map.Entry<Identifier, String> entry : data.generatedResources().entrySet()) {
            resourceManager.getResource(entry.getKey()).ifPresent(resource -> {
                String packId = resource.sourcePackId();
                if (!entry.getValue().equals(packId)) {
                    overriddenGenerated.computeIfAbsent(entry.getValue(), k -> new TreeMap<>())
                            .computeIfAbsent(packId, k -> new ArrayList<>()).add(entry.getKey());
                }
            });
        }

        overriddenGenerated.forEach((generatedPack, overriders) -> overriders.forEach((packId, ids) -> LOGGER.warn(
                "Pack '{}' overrides {} village file(s) generated in '{}', its versions are used instead (for example {})",
                packId, ids.size(), generatedPack, ids.get(0))));

        Map<Identifier, Map<String, Integer>> overriddenSources = new TreeMap<>();
        for (Map.Entry<Identifier, Identifier> entry : data.vanillaSources().entrySet()) {
            resourceManager.getResource(entry.getKey()).ifPresent(resource -> {
                String packId = resource.sourcePackId();
                if (!"vanilla".equals(packId)) {
                    overriddenSources.computeIfAbsent(entry.getValue(), k -> new TreeMap<>()).merge(packId, 1, Integer::sum);
                }
            });
        }

        overriddenSources.forEach((villageType, packs) -> packs.forEach((packId, count) -> LOGGER.warn(
                "Village type {} was copied from vanilla files, but pack '{}' changes {} of those files. Those changes are not part of {}",
                villageType, packId, count, villageType)));
    }
}
