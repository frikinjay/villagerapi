package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class VillagerPackResourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("VillagerAPI");

    public enum PackFilter {
        HAS_ASSETS(VillagerPackLoader.VillagerPack::hasAssets, "assets"),
        HAS_DATA(VillagerPackLoader.VillagerPack::hasData, "data"),
        HAS_SERVER_CONTENT(VillagerPackLoader.VillagerPack::hasServerContent, "data or villagers"),
        HAS_CONFIG(VillagerPackLoader.VillagerPack::hasConfig, "config");

        private final Predicate<VillagerPackLoader.VillagerPack> predicate;
        private final String description;

        PackFilter(Predicate<VillagerPackLoader.VillagerPack> predicate, String description) {
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

    public static DiscoveryResult discoverAndProcessPacks(
            List<PackFilter> requiredFilters,
            Consumer<VillagerPackLoader.VillagerPack> packConsumer) {

        Path villagerPacksDir = CommonPlatformHelper.getGameDirectory().resolve("villagerpacks");
        List<VillagerPackLoader.VillagerPack> packs = VillagerPackLoader.discoverAll(villagerPacksDir);
        List<Path> validPacks = new ArrayList<>();
        int skipped = 0;

        try {
            for (VillagerPackLoader.VillagerPack pack : packs) {
                if (!checkFilters(pack, requiredFilters)) {
                    skipped++;
                    continue;
                }

                validPacks.add(pack.getPath());
                try {
                    packConsumer.accept(pack);
                } catch (Exception e) {
                    LOGGER.error("Failed to process villagerpack: {} ({})", pack.getName(), pack.getSourceDescription(), e);
                }
            }
        } finally {
            for (VillagerPackLoader.VillagerPack pack : packs) {
                pack.close();
            }
        }

        return new DiscoveryResult(validPacks, skipped);
    }

    private static boolean checkFilters(VillagerPackLoader.VillagerPack pack, List<PackFilter> requiredFilters) {
        for (PackFilter filter : requiredFilters) {
            if (!filter.test(pack)) {
                return false;
            }
        }
        return true;
    }

    public static DiscoveryResult discoverResourcePacks(Consumer<VillagerPackLoader.VillagerPack> packConsumer) {
        return discoverAndProcessPacks(List.of(PackFilter.HAS_CONFIG, PackFilter.HAS_ASSETS), packConsumer);
    }

    public static DiscoveryResult discoverDataPacks(Consumer<VillagerPackLoader.VillagerPack> packConsumer) {
        return discoverAndProcessPacks(List.of(PackFilter.HAS_CONFIG, PackFilter.HAS_SERVER_CONTENT), packConsumer);
    }

    public static DiscoveryResult discoverAllPacks(Consumer<VillagerPackLoader.VillagerPack> packConsumer) {
        return discoverAndProcessPacks(List.of(PackFilter.HAS_CONFIG), packConsumer);
    }
}