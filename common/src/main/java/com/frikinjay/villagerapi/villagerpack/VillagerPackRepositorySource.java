package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.villagerpack.village.VillageGenerator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackRepositorySource implements RepositorySource {

    private final PackType packType;

    public VillagerPackRepositorySource(PackType packType) {
        this.packType = packType;
    }

    @Override
    public void loadPacks(@NonNull Consumer<Pack> consumer) {
        if (this.packType != PackType.SERVER_DATA && this.packType != PackType.CLIENT_RESOURCES) {
            return;
        }

        Map<String, Path> generatedVillages = Map.of();
        if (this.packType == PackType.SERVER_DATA) {
            VillagerPackTrades.rebuild();
            try {
                generatedVillages = VillageGenerator.rebuild();
            } catch (Exception e) {
                LOGGER.error("Failed to generate villagerpack villages", e);
            }
        }

        List<Pack> created = new ArrayList<>();

        Consumer<VillagerPackLoader.VillagerPack> collector = pack -> {
            try {
                Pack createdPack = VillagerPackHelper.createPack(pack.getPath(), pack.getName(), pack.getDisplayName(), this.packType);
                if (createdPack != null) {
                    created.add(createdPack);
                } else {
                    LOGGER.warn("Villagerpack '{}' could not be registered as a {} pack, check that it has a valid pack.mcmeta",
                            pack.getName(), this.packType == PackType.SERVER_DATA ? "data" : "resource");
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load {} pack from villagerpack: {}",
                        this.packType == PackType.SERVER_DATA ? "data" : "resource", pack.getName(), e);
            }
        };

        if (this.packType == PackType.SERVER_DATA) {
            VillagerPackResourceManager.discoverDataPacks(collector);
        } else {
            VillagerPackResourceManager.discoverResourcePacks(collector);
        }

        generatedVillages.forEach((packKey, directory) -> {
            Pack generated = createGeneratedVillagesPack(packKey, directory);
            if (generated != null) {
                consumer.accept(generated);
            }
        });

        for (int i = created.size() - 1; i >= 0; i--) {
            consumer.accept(created.get(i));
        }
    }

    private static Pack createGeneratedVillagesPack(String packKey, Path directory) {
        try {
            PackLocationInfo location = new PackLocationInfo(
                    VillageGenerator.generatedPackId(packKey),
                    Component.literal("Villager Pack: " + packKey + " (generated villages)"),
                    PackSource.DEFAULT,
                    Optional.empty()
            );

            Pack pack = Pack.readMetaAndCreate(
                    location,
                    new PathPackResources.PathResourcesSupplier(directory),
                    PackType.SERVER_DATA,
                    new PackSelectionConfig(true, Pack.Position.BOTTOM, false)
            );

            if (pack == null) {
                LOGGER.error("Generated villages pack for villagerpack '{}' at {} could not be created", packKey, directory);
            }
            return pack;
        } catch (Exception e) {
            LOGGER.error("Failed to register generated villages pack for villagerpack '{}'", packKey, e);
            return null;
        }
    }
}
