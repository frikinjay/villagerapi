package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Manages discovery and filtering of villagerpacks for resource/data pack registration.
 * Eliminates duplicate pack discovery code across platform implementations.
 */
public class VillagerPackResourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("VillagerAPI");

    /**
     * Filters for packs based on content type
     */
    public enum PackFilter {
        HAS_ASSETS(VillagerPackLoader.VillagerPack::hasAssets, "assets"),
        HAS_DATA(VillagerPackLoader.VillagerPack::hasData, "data"),
        HAS_CONFIG(VillagerPackLoader.VillagerPack::hasConfig, "config");

        private final java.util.function.Predicate<VillagerPackLoader.VillagerPack> predicate;
        private final String description;

        PackFilter(java.util.function.Predicate<VillagerPackLoader.VillagerPack> predicate, String description) {
            this.predicate = predicate;
            this.description = description;
        }

        public boolean test(VillagerPackLoader.VillagerPack pack) {
            return predicate.test(pack);
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Result of pack discovery
     */
    public static class DiscoveryResult {
        private final List<Path> packPaths;
        private final int skippedCount;

        public DiscoveryResult(List<Path> packPaths, int skippedCount) {
            this.packPaths = packPaths;
            this.skippedCount = skippedCount;
        }

        public List<Path> getPackPaths() {
            return packPaths;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public int getFoundCount() {
            return packPaths.size();
        }
    }

    /**
     * Discovers and filters villagerpacks from the villagerpacks directory
     *
     * @param requiredFilters Filters that must ALL pass (AND logic)
     * @param packConsumer Consumer called for each valid pack
     * @return Discovery result with counts
     */
    public static DiscoveryResult discoverAndProcessPacks(
            List<PackFilter> requiredFilters,
            Consumer<VillagerPackLoader.VillagerPack> packConsumer) {

        Path villagerPacksDir = CommonPlatformHelper.getGameDirectory().resolve("villagerpacks");

        if (!Files.exists(villagerPacksDir)) {
            LOGGER.debug("No villagerpacks directory found at: {}", villagerPacksDir);
            return new DiscoveryResult(List.of(), 0);
        }

        List<Path> validPacks = new ArrayList<>();
        int skipped = 0;

        try (Stream<Path> packs = Files.list(villagerPacksDir)) {
            List<Path> allPacks = packs
                    .filter(path -> Files.isDirectory(path) ||
                            (Files.isRegularFile(path) && path.toString().endsWith(".zip")))
                    .toList();

            for (Path packPath : allPacks) {
                try (VillagerPackLoader.VillagerPack pack = new VillagerPackLoader.VillagerPack(packPath)) {

                    // Check all required filters
                    boolean passesAllFilters = true;
                    for (PackFilter filter : requiredFilters) {
                        if (!filter.test(pack)) {
                            LOGGER.debug("Skipping '{}' - missing {}",
                                    pack.getName(), filter.getDescription());
                            passesAllFilters = false;
                            break;
                        }
                    }

                    if (passesAllFilters) {
                        validPacks.add(packPath);
                        packConsumer.accept(pack);
                    } else {
                        skipped++;
                    }

                } catch (IOException e) {
                    LOGGER.error("Failed to process villagerpack: {}", packPath.getFileName(), e);
                    skipped++;
                }
            }

        } catch (IOException e) {
            LOGGER.error("Failed to list villagerpacks directory", e);
        }

        return new DiscoveryResult(validPacks, skipped);
    }

    /**
     * Discovers packs for resource pack registration (must have config AND assets)
     */
    public static DiscoveryResult discoverResourcePacks(
            Consumer<VillagerPackLoader.VillagerPack> packConsumer) {

        return discoverAndProcessPacks(
                List.of(PackFilter.HAS_CONFIG, PackFilter.HAS_ASSETS),
                packConsumer
        );
    }

    /**
     * Discovers packs for data pack registration (must have config AND data)
     */
    public static DiscoveryResult discoverDataPacks(
            Consumer<VillagerPackLoader.VillagerPack> packConsumer) {

        return discoverAndProcessPacks(
                List.of(PackFilter.HAS_CONFIG, PackFilter.HAS_DATA),
                packConsumer
        );
    }

    /**
     * Discovers all valid villagerpacks (must have config)
     */
    public static DiscoveryResult discoverAllPacks(
            Consumer<VillagerPackLoader.VillagerPack> packConsumer) {

        return discoverAndProcessPacks(
                List.of(PackFilter.HAS_CONFIG),
                packConsumer
        );
    }
}