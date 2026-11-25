package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.registry.*;
import com.frikinjay.villagerapi.villagerpack.village.VillageRuntime;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.Map;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class PackInitializationOrchestrator {

    private static VillagerPackLoader packLoader;
    private static Map<VillagerPackLoader.DataType, Map<String, JsonObject>> packData;

    public static void initializeEarly() {
        Path villagerPacksDir = CommonPlatformHelper.getGameDirectory().resolve("villagerpacks");

        VillagerPackValidator.validateAllPacks(villagerPacksDir);

        packLoader = new VillagerPackLoader(CommonPlatformHelper.getGameDirectory());

        VillagerPackRegistry.registerCreativeTabsFromPacks(packLoader);

        packData = packLoader.loadAllPacks();

        VAPIWorkstations.init();
        VAPIPoiTypes.init();
        VAPITypes.init();
        VAPIMapDecorations.init();
        VAPIMaps.init();
        VAPIProfessions.init();
        VAPIGifts.init();
        VAPICreativeTabs.init();

        LOGGER.info("Early pack initialization complete");
    }

    public static void registerContent() {
        registerStructureTags();
        registerWorkstations();
        registerVillagerTypes();
        registerMapDecorationTypes();
        registerMaps();
        registerPoiTypes();
        registerProfessions();
        registerGifts();
    }

    public static void onServerStarting(MinecraftServer server) {
        runStep("validate registrations", VillagerPackRegistry::validateRegistrations);
        runStep("register biome mappings", () -> VillagerPackBiomeMapper.registerBiomeMappings(server));
        runStep("set up villagerpack villages", () -> VillageRuntime.onServerStarting(server));
    }

    public static void onServerStarted(MinecraftServer server, HolderLookup.Provider registryAccess) {
        runStep("apply hero gifts", VAPIGifts::applyAllGifts);
        runStep("read villagerpack wandering trader trades", () -> VillagerPackWanderingTrades.rebuild(server.registryAccess()));
        LOGGER.info("VillagerAPI initialization complete!");
    }

    private static void runStep(String name, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            LOGGER.error("VillagerAPI failed to {}", name, e);
        }
    }

    private static Map<String, JsonObject> data(VillagerPackLoader.DataType type) {
        if (packData == null) return Map.of();
        Map<String, JsonObject> data = packData.get(type);
        return data != null ? data : Map.of();
    }

    private static void registerWorkstations() {
        VillagerPackRegistry.registerWorkstations(data(VillagerPackLoader.DataType.WORKSTATIONS));
    }

    private static void registerMaps() {
        VillagerPackRegistry.registerMaps(data(VillagerPackLoader.DataType.STRUCTURE_TAGS));
    }

    private static void registerVillagerTypes() {
        VillagerPackRegistry.registerVillagerTypes(data(VillagerPackLoader.DataType.TYPES));
    }

    private static void registerMapDecorationTypes() {
        VillagerPackRegistry.registerMapDecorationTypes(data(VillagerPackLoader.DataType.STRUCTURE_TAGS));
    }

    private static void registerStructureTags() {
        VillagerPackRegistry.registerStructureTags(data(VillagerPackLoader.DataType.STRUCTURE_TAGS));
    }

    private static void registerPoiTypes() {
        VillagerPackRegistry.registerPoiTypes(data(VillagerPackLoader.DataType.POI_TYPES));
    }

    private static void registerProfessions() {
        VillagerPackRegistry.registerProfessions(data(VillagerPackLoader.DataType.PROFESSIONS));
    }

    private static void registerGifts() {
        VillagerPackRegistry.registerGifts(data(VillagerPackLoader.DataType.GIFTS));
    }

    public static VillagerPackLoader getPackLoader() {
        return packLoader;
    }

    public static Map<VillagerPackLoader.DataType, Map<String, JsonObject>> getPackData() {
        return packData;
    }
}
