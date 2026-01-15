package com.frikinjay.villagerapi.fabric.mixin;

import com.frikinjay.villagerapi.fabric.villagerpack.VillagerPackRegistrationFabric;
import com.frikinjay.villagerapi.villagerpack.VillagerPackLoader;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {

    @Inject(
            method = "discoverAvailable",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableMap;copyOf(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;"
            )
    )
    private void addExtraPacks(CallbackInfoReturnable<Map<String, Pack>> cir,
                               @Local Map<String, Pack> map) {
        for (Path packPath : VillagerPackRegistrationFabric.getDataPackPaths()) {
            String packName = getPackName(packPath);
            String packId = "villagerpacks/" + packName.toLowerCase().replace(" ", "_");

            try {
                Pack pack = createDataPack(packPath, packId, packName);
                if (pack != null) {
                    map.put(packId, pack);
                    LOGGER.info("Injected villagerpack as datapack: {}", packName);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to inject villagerpack as datapack: {}", packName, e);
            }
        }
    }

    @Unique
    private Pack createDataPack(Path packPath, String packId, String displayName) {
        VillagerPackLoader.VillagerPack villagerPack = null;
        try {
            villagerPack = new VillagerPackLoader.VillagerPack(packPath);
            Path rootPath = villagerPack.getRootPath();

            PackLocationInfo locationInfo = new PackLocationInfo(
                    packId,
                    Component.literal(displayName),
                    PackSource.BUILT_IN,
                    java.util.Optional.empty()
            );

            PackSelectionConfig selectionConfig = new PackSelectionConfig(
                    true,
                    Pack.Position.TOP,
                    false
            );

            return Pack.readMetaAndCreate(
                    locationInfo,
                    new PathPackResources.PathResourcesSupplier(rootPath),
                    PackType.SERVER_DATA,
                    selectionConfig
            );
        } catch (IOException e) {
            LOGGER.error("Failed to create datapack from villagerpack", e);
            if (villagerPack != null) {
                villagerPack.close();
            }
            return null;
        }
    }

    @Unique
    private String getPackName(Path packPath) {
        String fileName = packPath.getFileName().toString();
        return fileName.endsWith(".zip") ? fileName.replace(".zip", "") : fileName;
    }
}