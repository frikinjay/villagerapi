package com.frikinjay.villagerapi.villagerpack;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class BiomeMappingCache {

    private static final String VANILLA_NAMESPACE = "minecraft";

    private static volatile Map<ResourceKey<Biome>, Candidates> mappings = Map.of();

    private BiomeMappingCache() {}

    public static void set(Map<ResourceKey<Biome>, Candidates> newMappings) {
        mappings = Map.copyOf(newMappings);
    }

    public static void clear() {
        mappings = Map.of();
    }

    public static int size() {
        return mappings.size();
    }

    public static Map<ResourceKey<Biome>, Candidates> getAllMappings() {
        return mappings;
    }

    public static boolean hasMapping(Holder<Biome> biome) {
        return candidatesFor(biome) != null;
    }

    public static ResourceKey<VillagerType> pick(Holder<Biome> biome, ResourceKey<VillagerType> original) {
        Candidates candidates = candidatesFor(biome);
        if (candidates == null) return original;

        List<WeightedType> options = new ArrayList<>(candidates.types());
        if (candidates.includeOriginal() && original != null && !isVanilla(original)
                && options.stream().noneMatch(option -> option.type().equals(original))) {
            options.add(new WeightedType(original, 1));
        }

        if (options.size() == 1) return options.get(0).type();

        int total = 0;
        for (WeightedType option : options) {
            total += option.weight();
        }

        int roll = ThreadLocalRandom.current().nextInt(total);
        for (WeightedType option : options) {
            roll -= option.weight();
            if (roll < 0) return option.type();
        }
        return options.get(options.size() - 1).type();
    }

    private static boolean isVanilla(ResourceKey<VillagerType> type) {
        return VANILLA_NAMESPACE.equals(type.identifier().getNamespace());
    }

    private static Candidates candidatesFor(Holder<Biome> biome) {
        Optional<ResourceKey<Biome>> biomeKey = biome.unwrapKey();
        if (biomeKey.isEmpty()) return null;

        Candidates candidates = mappings.get(biomeKey.get());
        return candidates == null || candidates.types().isEmpty() ? null : candidates;
    }

    public record WeightedType(ResourceKey<VillagerType> type, int weight) {}

    public record Candidates(List<WeightedType> types, boolean includeOriginal) {}
}