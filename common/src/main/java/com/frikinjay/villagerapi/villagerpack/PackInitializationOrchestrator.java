package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.registry.*;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.Map;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

/**
 * Centralized orchestrator for pack initialization.
 * Eliminates duplicate initialization code across platform implementations.
 */
public class PackInitializationOrchestrator {

    private static VillagerPackLoader packLoader;
    private static Map<VillagerPackLoader.DataType, Map<String, JsonObject>> packData;

    /**
     * Phase 1: Early initialization - validation and loading
     */
    public static void initializeEarly() {
        Path villagerPacksDir = CommonPlatformHelper.getGameDirectory().resolve("villagerpacks");

        // Validate before loading
        VillagerPackValidator.validateAllPacks(villagerPacksDir);

        // Initialize loader
        packLoader = new VillagerPackLoader(CommonPlatformHelper.getGameDirectory());

        // Register creative tabs from configs (must be before pack data loading)
        VillagerPackRegistry.registerCreativeTabsFromPacks(packLoader);

        // Load pack data
        packData = packLoader.loadAllPacks();

        // Initialize empty registries
        VAPIWorkstations.init();
        VAPIPoiTypes.init();
        VAPITypes.init();
        VAPIMapDecorations.init();
        VAPIProfessions.init();
        VAPIGifts.init();
        VAPICreativeTabs.init();

        LOGGER.info("Early pack initialization complete");
    }

    /**
     * Phase 2: Registry registration - workstations, types, POIs, professions, etc.
     */
    public static void registerContent() {
        registerWorkstations();
        registerVillagerTypes();
        registerMapDecorationTypes();
        registerStructureTags();
        registerPoiTypes();
        registerProfessions();
        registerGifts();

        LOGGER.info("Content registration complete");
    }

    /**
     * Phase 3: Server starting - validation and biome mappings
     */
    public static void onServerStarting(MinecraftServer server) {
        VillagerPackRegistry.validateRegistrations();
        registerBiomeMappings(server);

        LOGGER.info("Server starting initialization complete");
    }

    /**
     * Phase 4: Server started - trades and gifts
     */
    public static void onServerStarted(MinecraftServer server, HolderLookup.Provider registryAccess) {
        registerTrades(registryAccess);
        registerBiomeTrades(registryAccess);
        VAPIGifts.applyAllGifts();

        LOGGER.info("VillagerAPI initialization complete!");
    }

    private static void registerWorkstations() {
        Map<String, JsonObject> data = packData.get(VillagerPackLoader.DataType.WORKSTATIONS);
        VillagerPackRegistry.registerWorkstations(data != null ? data : Map.of());
    }

    private static void registerVillagerTypes() {
        Map<String, JsonObject> data = packData.get(VillagerPackLoader.DataType.TYPES);
        VillagerPackRegistry.registerVillagerTypes(data != null ? data : Map.of());
    }

    private static void registerMapDecorationTypes() {
        Map<String, JsonObject> data = packData.get(VillagerPackLoader.DataType.STRUCTURE_TAGS);
        VillagerPackRegistry.registerMapDecorationTypes(data != null ? data : Map.of());
    }

    private static void registerStructureTags() {
        Map<String, JsonObject> data = packData.get(VillagerPackLoader.DataType.STRUCTURE_TAGS);
        VillagerPackRegistry.registerStructureTags(data != null ? data : Map.of());
    }

    private static void registerPoiTypes() {
        Map<String, JsonObject> data = packData.get(VillagerPackLoader.DataType.POI_TYPES);
        VillagerPackRegistry.registerPoiTypes(data != null ? data : Map.of());
    }

    private static void registerProfessions() {
        Map<String, JsonObject> data = packData.get(VillagerPackLoader.DataType.PROFESSIONS);
        VillagerPackRegistry.registerProfessions(data != null ? data : Map.of());
    }

    private static void registerGifts() {
        Map<String, JsonObject> data = packData.get(VillagerPackLoader.DataType.GIFTS);
        VillagerPackRegistry.registerGifts(data != null ? data : Map.of());
    }

    private static void registerBiomeMappings(MinecraftServer server) {
        Map<String, JsonObject> data = packData.get(VillagerPackLoader.DataType.BIOME_MAPPINGS);
        if (data != null && !data.isEmpty()) {
            VillagerPackBiomeMapper.registerBiomeMappings(data, server);
        }
    }

    private static void registerTrades(HolderLookup.Provider registryAccess) {
        Map<String, JsonObject> data = packData.get(VillagerPackLoader.DataType.TRADES);
        VillagerPackRegistry.registerTrades(
                data != null ? data : Map.of(),
                registryAccess
        );
    }

    private static void registerBiomeTrades(HolderLookup.Provider registryAccess) {
        Map<String, JsonObject> data = packData.get(VillagerPackLoader.DataType.BIOME_TRADES);
        VillagerPackRegistry.registerBiomeTrades(
                data != null ? data : Map.of(),
                registryAccess
        );
    }

    public static VillagerPackLoader getPackLoader() {
        return packLoader;
    }

    public static Map<VillagerPackLoader.DataType, Map<String, JsonObject>> getPackData() {
        return packData;
    }
}