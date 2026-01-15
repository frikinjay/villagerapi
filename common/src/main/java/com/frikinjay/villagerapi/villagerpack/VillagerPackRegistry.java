package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.registry.*;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;

import java.util.Map;
import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackRegistry {

    public static void registerCreativeTabsFromPacks(VillagerPackLoader loader) {
        if (loader == null) return;

        try {
            for (VillagerPackLoader.VillagerPack pack : loader.getDiscoveredPacks()) {
                if (pack.hasConfig()) {
                    VillagerPackConfig config = pack.getConfig();
                    VAPICreativeTabs.registerTab(config);
                }
                pack.close();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to register creative tabs from packs", e);
        }
    }

    public static void registerWorkstations(Map<String, JsonObject> data) {
        if (data.isEmpty()) return;

        LOGGER.info("Registering {} villager workstations from packs", data.size());
        data.forEach((name, json) -> VAPIWorkstations.registerFromPack(name, json));
    }

    public static void registerVillagerTypes(Map<String, JsonObject> data) {
        if (data.isEmpty()) return;

        LOGGER.info("Registering {} villager types from packs", data.size());
        data.forEach((name, json) -> VAPITypes.registerFromPack(name, json));
    }

    public static void registerMapDecorationTypes(Map<String, JsonObject> data) {
        if (data.isEmpty()) return;

        LOGGER.info("Registering {} map decoration types from packs", data.size());
        data.forEach((name, json) -> VAPIMapDecorations.registerFromPack(name, json));
    }

    public static void registerStructureTags(Map<String, JsonObject> data) {
        if (data.isEmpty()) return;

        LOGGER.info("Creating {} structure tags from packs", data.size());
        data.forEach((name, json) -> VAPIStructureTags.registerFromPack(name, json));
    }

    public static void registerPoiTypes(Map<String, JsonObject> data) {
        if (data.isEmpty()) return;

        LOGGER.info("Registering {} POI types from packs", data.size());
        data.forEach((name, json) -> VAPIPoiTypes.registerFromPack(name, json));
    }

    public static void registerProfessions(Map<String, JsonObject> data) {
        if (data.isEmpty()) return;

        LOGGER.info("Registering {} professions from packs", data.size());
        data.forEach((name, json) -> VAPIProfessions.registerFromPack(name, json));
    }

    public static void validateRegistrations() {
        LOGGER.info("Validating POI types and professions...");
        VAPIPoiTypes.validatePoiTypes();
        VAPIProfessions.validateProfessions();
    }

    public static void registerTrades(Map<String, JsonObject> data) {
        registerTrades(data, null);
    }

    public static void registerTrades(Map<String, JsonObject> data, HolderLookup.Provider registryLookup) {
        if (data.isEmpty()) return;

        LOGGER.info("Registering trades for {} professions from packs", data.size());

        VillagerPackCodecs.setRegistryLookup(registryLookup);

        data.forEach((name, json) -> {
            try {
                VillagerPackCodecs.TradesData tradesData = VillagerPackCodecs.parseTrades(json);

                Supplier<VillagerProfession> professionSupplier = VAPIProfessions.getDynamicProfession(tradesData.profession());

                ResourceKey<VillagerProfession> professionKey;

                if (professionSupplier != null) {
                    // For dynamic professions, get the key from the registry
                    VillagerProfession prof = professionSupplier.get();
                    professionKey = BuiltInRegistries.VILLAGER_PROFESSION.getResourceKey(prof).orElse(null);
                } else {
                    // For vanilla/other mod professions, get from holder
                    Identifier profId = Identifier.parse(tradesData.profession());
                    professionKey = BuiltInRegistries.VILLAGER_PROFESSION.get(profId)
                            .flatMap(Holder.Reference::unwrapKey)
                            .orElse(null);
                }

                if (professionKey == null) {
                    LOGGER.warn("Profession {} not found for trades {}", tradesData.profession(), name);
                    return;
                }

                VillagerTrades.TRADES.put(professionKey, tradesData.trades());
                LOGGER.info("Registered trades from pack: {}", name);
            } catch (Exception e) {
                LOGGER.error("Failed to register trades: {}", name, e);
            }
        });

        VillagerPackCodecs.setRegistryLookup(null);
    }

    public static void registerGifts(Map<String, JsonObject> data) {
        if (data.isEmpty()) return;

        LOGGER.info("Staging {} gifts from packs", data.size());
        data.forEach((name, json) -> VAPIGifts.registerFromPack(name, json));
    }

    public static void registerBiomeTrades(Map<String, JsonObject> data) {
        registerBiomeTrades(data, null);
    }

    public static void registerBiomeTrades(Map<String, JsonObject> data, HolderLookup.Provider registryLookup) {
        if (data.isEmpty()) return;

        LOGGER.info("Registering biome-specific trades for {} professions from packs", data.size());

        VillagerPackCodecs.setRegistryLookup(registryLookup);

        data.forEach((name, json) -> {
            try {
                VillagerPackCodecs.BiomeTradesData biomeTradesData = VillagerPackCodecs.parseBiomeTrades(json);

                Supplier<VillagerProfession> profession = VAPIProfessions.getDynamicProfession(biomeTradesData.profession());
                VillagerProfession prof = profession != null ? profession.get() :
                        VillagerAPI.unwrapHolder(BuiltInRegistries.VILLAGER_PROFESSION.get(Identifier.parse(biomeTradesData.profession())));

                if (prof == null) {
                    LOGGER.warn("Profession {} not found for biome trades {}", biomeTradesData.profession(), name);
                    return;
                }

                biomeTradesData.biomeTradeMap().forEach((villagerType, biomeTrades) -> {
                    BiomeTradesCache.registerBiomeTrades(prof, villagerType, biomeTrades);
                    LOGGER.info("Registered biome trades from pack for {} in biome type {}",
                            biomeTradesData.profession(), BuiltInRegistries.VILLAGER_TYPE.getKey(villagerType));
                });

                biomeTradesData.replaceFlags().forEach((villagerType, levelFlags) -> {
                    levelFlags.forEach((level, replace) -> {
                        BiomeTradesCache.registerReplaceFlag(prof, villagerType, level, replace);
                        LOGGER.debug("Set replace flag for {} biome {} level {} to {}",
                                biomeTradesData.profession(),
                                BuiltInRegistries.VILLAGER_TYPE.getKey(villagerType),
                                level, replace);
                    });
                });

                Int2ObjectMap<VillagerTrades.ItemListing[]> baseTrades = VillagerTrades.TRADES.get(prof);
                if (baseTrades == null) {
                    LOGGER.warn("No base trades for profession {}, cannot apply biome trades", biomeTradesData.profession());
                    return;
                }

                biomeTradesData.biomeTradeMap().forEach((villagerType, biomeTrades) -> {
                    Int2ObjectMap<VillagerTrades.ItemListing[]> mergedTrades = new Int2ObjectOpenHashMap<>();

                    baseTrades.int2ObjectEntrySet().forEach(entry ->
                            mergedTrades.put(entry.getIntKey(), entry.getValue().clone())
                    );

                    biomeTrades.int2ObjectEntrySet().forEach(entry -> {
                        int level = entry.getIntKey();
                        VillagerTrades.ItemListing[] biomeListings = entry.getValue();

                        if (mergedTrades.containsKey(level)) {
                            VillagerTrades.ItemListing[] baseListings = mergedTrades.get(level);
                            VillagerTrades.ItemListing[] combined =
                                    new VillagerTrades.ItemListing[baseListings.length + biomeListings.length];
                            System.arraycopy(baseListings, 0, combined, 0, baseListings.length);
                            System.arraycopy(biomeListings, 0, combined, baseListings.length, biomeListings.length);
                            mergedTrades.put(level, combined);
                        } else {
                            mergedTrades.put(level, biomeListings);
                        }
                    });

                    LOGGER.info("Applied biome trades from pack for {} in biome type {}",
                            biomeTradesData.profession(), BuiltInRegistries.VILLAGER_TYPE.getKey(villagerType));
                });
            } catch (Exception e) {
                LOGGER.error("Failed to register biome trades: {}", name, e);
            }
        });

        VillagerPackCodecs.setRegistryLookup(null);
    }
}