package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public final class VillagerPackTradeOwnership {

    private static Map<String, String> owners;

    private VillagerPackTradeOwnership() {}

    public static synchronized void rebuild() {
        Map<String, String> result = new HashMap<>();
        VillagerPackLoader loader = new VillagerPackLoader(CommonPlatformHelper.getGameDirectory());
        List<VillagerPackLoader.VillagerPack> packs = loader.getDiscoveredPacks();

        try {
            for (VillagerPackLoader.VillagerPack pack : packs) {
                if (!canProvideServerData(pack)) continue;

                for (String professionId : collectProfessions(pack)) {
                    String existing = result.putIfAbsent(professionId, pack.getName());
                    if (existing != null && !existing.equals(pack.getName())) {
                        LOGGER.info("Trades for '{}' from villagerpack '{}' are overridden by higher priority villagerpack '{}'",
                                professionId, pack.getName(), existing);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to resolve villagerpack trade ownership", e);
        } finally {
            for (VillagerPackLoader.VillagerPack pack : packs) {
                pack.close();
            }
        }

        owners = result;
        LOGGER.info("Resolved trade ownership for {} profession(s) across villagerpacks", result.size());
    }

    public static synchronized boolean isOwner(String packKey, String professionId) {
        if (owners == null) rebuild();
        String owner = owners.get(professionId);
        return owner == null || owner.equals(packKey);
    }

    public static synchronized Map<String, String> getOwners() {
        if (owners == null) rebuild();
        return new HashMap<>(owners);
    }

    private static boolean canProvideServerData(VillagerPackLoader.VillagerPack pack) {
        return pack.hasConfig() && pack.getPackMeta() != null && pack.hasServerContent();
    }

    private static Set<String> collectProfessions(VillagerPackLoader.VillagerPack pack) {
        Set<String> professions = new LinkedHashSet<>();
        String packNamespace = pack.getNamespace();
        Path root = pack.getRootPath();

        VillagerPackCodecs.forEachJson(root.resolve("villagers/trades"), "trade", json -> {
            if (json.has("profession") && json.has("levels") && json.get("levels").isJsonObject()) {
                professions.add(VillagerPackCodecs.resolveProfessionId(json.get("profession").getAsString(), packNamespace));
            }
        });

        VillagerPackCodecs.forEachJson(root.resolve("villagers/biome_trades"), "biome trade", json -> {
            if (!json.has("profession") || !json.has("biome_overrides") || !json.get("biome_overrides").isJsonObject()) return;

            JsonObject overrides = json.getAsJsonObject("biome_overrides");
            for (String key : overrides.keySet()) {
                JsonElement biomeData = overrides.get(key);
                if (biomeData.isJsonObject() && biomeData.getAsJsonObject().has("levels")) {
                    professions.add(VillagerPackCodecs.resolveProfessionId(json.get("profession").getAsString(), packNamespace));
                    return;
                }
            }
        });

        return professions;
    }

    static boolean hasVillagersDirectory(Path root) {
        return Files.exists(root.resolve("villagers"));
    }
}