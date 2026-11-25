package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.registry.*;
import com.google.gson.JsonObject;
import java.util.Map;
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

    public static void registerMaps(Map<String, JsonObject> data) {
        if (data.isEmpty()) return;
        LOGGER.info("Registering {} map items from packs", data.size());
        data.forEach((name, json) -> VAPIMaps.registerFromPack(name, json));
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

    public static void registerGifts(Map<String, JsonObject> data) {
        if (data.isEmpty()) return;
        LOGGER.info("Staging {} gifts from packs", data.size());
        data.forEach((name, json) -> VAPIGifts.registerFromPack(name, json));
    }
}