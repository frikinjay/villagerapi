package com.frikinjay.villagerapi.fabric.villagerpack;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.villagerpack.VillagerPackResourceManager;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

/**
 * Fabric pack registration - simplified using VillagerPackResourceManager
 */
public class VillagerPackRegistrationFabric {

    private static final List<Path> dataPackPaths = new ArrayList<>();

    public static void registerPacks() {
        registerResourcePacks();
        collectDataPacks();
        LOGGER.info("Registered villagerpack repository sources");
    }

    private static void registerResourcePacks() {
        VillagerPackResourceManager.DiscoveryResult result =
                VillagerPackResourceManager.discoverResourcePacks(pack -> {
                    try {
                        String namespace = pack.getNamespace();
                        String displayName = pack.getDisplayName();

                        Identifier packId = Identifier.fromNamespaceAndPath(
                                namespace,
                                "villagerpack"
                        );

                        ResourceManagerHelper.registerBuiltinResourcePack(
                                packId,
                                FabricLoader.getInstance()
                                        .getModContainer(VillagerAPI.MOD_ID)
                                        .orElseThrow(() -> new RuntimeException("VillagerAPI mod container not found")),
                                Component.literal("VillagerPack: " + displayName),
                                ResourcePackActivationType.DEFAULT_ENABLED
                        );

                        LOGGER.info("Registered villagerpack '{}' as resource pack ({})",
                                displayName, packId);

                    } catch (Exception e) {
                        LOGGER.error("Failed to register villagerpack '{}' as resource pack",
                                pack.getName(), e);
                    }
                });

        if (result.getSkippedCount() > 0) {
            LOGGER.debug("Skipped {} pack(s) for resource registration (missing assets or config)",
                    result.getSkippedCount());
        }
    }

    private static void collectDataPacks() {
        VillagerPackResourceManager.DiscoveryResult result =
                VillagerPackResourceManager.discoverDataPacks(pack -> {
                    dataPackPaths.add(pack.getPath());
                    LOGGER.info("Collected villagerpack '{}' for datapack injection (namespace: {})",
                            pack.getDisplayName(), pack.getNamespace());
                });

        if (result.getSkippedCount() > 0) {
            LOGGER.debug("Skipped {} pack(s) for datapack collection (missing data or config)",
                    result.getSkippedCount());
        }
    }

    public static List<Path> getDataPackPaths() {
        return dataPackPaths;
    }
}