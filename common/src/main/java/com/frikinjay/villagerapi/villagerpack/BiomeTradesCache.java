package com.frikinjay.villagerapi.villagerpack;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;

import java.util.HashMap;
import java.util.Map;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class BiomeTradesCache {

    // Map: Profession -> VillagerType -> Level -> Trades
    private static final Map<VillagerProfession, Map<VillagerType, Int2ObjectMap<VillagerTrades.ItemListing[]>>> BIOME_TRADES_CACHE = new HashMap<>();

    public static void registerBiomeTrades(VillagerProfession profession,
                                           VillagerType villagerType,
                                           Int2ObjectMap<VillagerTrades.ItemListing[]> trades) {
        BIOME_TRADES_CACHE
                .computeIfAbsent(profession, k -> new HashMap<>())
                .put(villagerType, trades);

        LOGGER.debug("Registered biome trades for profession {} in biome type {}",
                profession, villagerType);
    }

    public static Int2ObjectMap<VillagerTrades.ItemListing[]> getBiomeTrades(
            VillagerProfession profession,
            VillagerType villagerType) {
        Map<VillagerType, Int2ObjectMap<VillagerTrades.ItemListing[]>> professionTrades =
                BIOME_TRADES_CACHE.get(profession);

        if (professionTrades == null) {
            return null;
        }

        return professionTrades.get(villagerType);
    }

    public static boolean hasBiomeTrades(VillagerProfession profession) {
        return BIOME_TRADES_CACHE.containsKey(profession);
    }

    // Map to store replace flags: Profession -> VillagerType -> Level -> Replace
    private static final Map<VillagerProfession, Map<VillagerType, Map<Integer, Boolean>>> REPLACE_FLAGS = new HashMap<>();

    public static void registerReplaceFlag(VillagerProfession profession,
                                           VillagerType villagerType,
                                           int level,
                                           boolean replace) {
        REPLACE_FLAGS
                .computeIfAbsent(profession, k -> new HashMap<>())
                .computeIfAbsent(villagerType, k -> new HashMap<>())
                .put(level, replace);
    }

    private static boolean shouldReplace(VillagerProfession profession,
                                         VillagerType villagerType,
                                         int level) {
        Map<VillagerType, Map<Integer, Boolean>> professionFlags = REPLACE_FLAGS.get(profession);
        if (professionFlags == null) {
            return false;
        }

        Map<Integer, Boolean> typeFlags = professionFlags.get(villagerType);
        if (typeFlags == null) {
            return false;
        }

        return typeFlags.getOrDefault(level, false);
    }

    public static Int2ObjectMap<VillagerTrades.ItemListing[]> getMergedTrades(
            VillagerProfession profession,
            VillagerType villagerType) {

        Int2ObjectMap<VillagerTrades.ItemListing[]> baseTrades = VillagerTrades.TRADES.get(profession);
        if (baseTrades == null) {
            return null;
        }

        Int2ObjectMap<VillagerTrades.ItemListing[]> biomeTrades = getBiomeTrades(profession, villagerType);
        if (biomeTrades == null) {
            return baseTrades;
        }

        Int2ObjectMap<VillagerTrades.ItemListing[]> mergedTrades = new Int2ObjectOpenHashMap<>();

        baseTrades.int2ObjectEntrySet().forEach(entry ->
                mergedTrades.put(entry.getIntKey(), entry.getValue().clone())
        );

        biomeTrades.int2ObjectEntrySet().forEach(entry -> {
            int level = entry.getIntKey();
            VillagerTrades.ItemListing[] biomeListings = entry.getValue();

            if (shouldReplace(profession, villagerType, level)) {
                mergedTrades.put(level, biomeListings);
                LOGGER.debug("Replaced trades for profession {} biome {} level {} (replace mode)",
                        profession, villagerType, level);
            } else {
                if (mergedTrades.containsKey(level)) {
                    VillagerTrades.ItemListing[] baseListings = mergedTrades.get(level);
                    VillagerTrades.ItemListing[] combined =
                            new VillagerTrades.ItemListing[baseListings.length + biomeListings.length];
                    System.arraycopy(baseListings, 0, combined, 0, baseListings.length);
                    System.arraycopy(biomeListings, 0, combined, baseListings.length, biomeListings.length);
                    mergedTrades.put(level, combined);
                    LOGGER.debug("Merged trades for profession {} biome {} level {} (add mode)",
                            profession, villagerType, level);
                } else {
                    mergedTrades.put(level, biomeListings);
                }
            }
        });

        return mergedTrades;
    }

    public static void clear() {
        BIOME_TRADES_CACHE.clear();
        REPLACE_FLAGS.clear();
        LOGGER.debug("Cleared biome trades cache");
    }
}