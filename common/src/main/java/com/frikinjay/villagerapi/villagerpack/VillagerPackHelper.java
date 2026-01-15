package com.frikinjay.villagerapi.villagerpack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VillagerPackHelper {

    public static Pack createPack(Path packPath, String packName, PackType packType) {
        PackLocationInfo locationInfo = new PackLocationInfo(
                "villagerpack:" + packName,
                Component.literal("Villager Pack: " + packName),
                PackSource.BUILT_IN,
                java.util.Optional.empty()
        );

        return Pack.readMetaAndCreate(
                locationInfo,
                new Pack.ResourcesSupplier() {
                    private VillagerPackLoader.VillagerPack villagerPack;

                    @Override
                    public @NotNull PackResources openPrimary(PackLocationInfo location) {
                        try {
                            villagerPack = new VillagerPackLoader.VillagerPack(packPath);
                            return new VillagerPackAsDataAndResPacks(villagerPack.getRootPath(), packName);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to open villager pack: " + packName, e);
                        }
                    }

                    @Override
                    public @NotNull PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                        try {
                            if (villagerPack == null) {
                                villagerPack = new VillagerPackLoader.VillagerPack(packPath);
                            }
                            return new VillagerPackAsDataAndResPacks(villagerPack.getRootPath(), packName);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to open villager pack: " + packName, e);
                        }
                    }
                },
                packType,
                new PackSelectionConfig(true, Pack.Position.TOP, false)
        );
    }

    public static boolean hasDataOrAssets(Path packPath) {
        return hasData(packPath) || hasAssets(packPath);
    }

    public static boolean hasData(Path packPath) {
        if (Files.isDirectory(packPath)) {
            return Files.exists(packPath.resolve("data"));
        } else if (packPath.toString().endsWith(".zip")) {
            try {
                VillagerPackLoader.VillagerPack pack = new VillagerPackLoader.VillagerPack(packPath);
                boolean result = pack.hasData();
                // No closing we let the filesystem be reused or cleaned up later
                return result;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    public static boolean hasAssets(Path packPath) {
        if (Files.isDirectory(packPath)) {
            return Files.exists(packPath.resolve("assets"));
        } else if (packPath.toString().endsWith(".zip")) {
            try {
                VillagerPackLoader.VillagerPack pack = new VillagerPackLoader.VillagerPack(packPath);
                boolean result = pack.hasAssets();
                // No closing here too we let the filesystem be reused or cleaned up later
                return result;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }
}