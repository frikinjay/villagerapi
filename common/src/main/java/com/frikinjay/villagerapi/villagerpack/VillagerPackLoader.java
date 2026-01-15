package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackLoader {

    private final Path villagerPacksDir;

    public VillagerPackLoader(Path gameDirectory) {
        this.villagerPacksDir = gameDirectory.resolve("villagerpacks");
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(villagerPacksDir)) {
                Files.createDirectories(villagerPacksDir);
                LOGGER.info("Created villagerpacks directory at: {}", villagerPacksDir);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create villagerpacks directory", e);
        }
    }

    public Map<DataType, Map<String, JsonObject>> loadAllPacks() {
        Map<DataType, Map<String, JsonObject>> allData = new EnumMap<>(DataType.class);
        for (DataType type : DataType.values()) {
            allData.put(type, new LinkedHashMap<>());
        }

        try {
            List<VillagerPack> packs = discoverPacks();

            int fromVillagerPacks = 0;
            int fromMods = 0;

            for (VillagerPack pack : packs) {
                if (pack.isFromModsDirectory()) {
                    fromMods++;
                } else {
                    fromVillagerPacks++;
                }
            }

            LOGGER.info("Found {} villager pack(s): {} from villagerpacks/, {} from mods/",
                    packs.size(), fromVillagerPacks, fromMods);

            try {
                for (VillagerPack pack : packs) {
                    if (pack.isValid()) {
                        loadPackData(pack, allData);
                    } else {
                        LOGGER.warn("Skipping invalid pack: {}", pack.getName());
                    }
                }
            } finally {
                // Close all packs when done
                for (VillagerPack pack : packs) {
                    pack.close();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load villager packs", e);
        }

        return allData;
    }

    private List<VillagerPack> discoverPacks() throws Exception {
        List<VillagerPack> discovered = new ArrayList<>();

        if (Files.exists(villagerPacksDir)) {
            try (Stream<Path> paths = Files.list(villagerPacksDir)) {
                paths.filter(path -> Files.isDirectory(path) ||
                                (Files.isRegularFile(path) && (path.toString().endsWith(".zip") || path.toString().endsWith(".jar"))))
                        .forEach(path -> {
                            try {
                                VillagerPack pack = new VillagerPack(path);
                                if (pack.hasConfig()) {
                                    discovered.add(pack);
                                } else {
                                    pack.close();
                                }
                            } catch (IOException e) {
                                LOGGER.debug("Failed to load external pack: {} ({})", path.getFileName(), e.getMessage());
                            }
                        });
            }
        }

        CommonPlatformHelper.getAllModRootPaths().forEach((modId, rootPath) -> {
            try {
                VillagerPack pack = new VillagerPack(rootPath, modId, true);

                if (pack.hasConfig()) {
                    discovered.add(pack);
                } else {

                }
            } catch (IOException e) {
                LOGGER.debug("Mod {} does not contain villagerpack configuration.", modId);
            }
        });

        return discovered.stream()
                .sorted(Comparator.comparing(VillagerPack::getName))
                .collect(Collectors.toList());
    }

    private void loadPackData(VillagerPack pack, Map<DataType, Map<String, JsonObject>> allData) {
        String packSource = pack.isFromModsDirectory() ? "mod JAR" : "villagerpack";
        LOGGER.info("Loading {} '{}' (namespace: {})", packSource, pack.getDisplayName(), pack.getNamespace());

        // Store the current pack namespace for workstation registration
        String currentPackNamespace = pack.getNamespace();

        for (DataType type : DataType.values()) {
            Path typeDir = pack.getRootPath().resolve("villagers").resolve(type.getPath());

            if (!Files.exists(typeDir)) {
                continue;
            }

            try (Stream<Path> files = Files.walk(typeDir)) {
                files.filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            try {
                                String fileName = typeDir.relativize(file).toString()
                                        .replace(".json", "")
                                        .replace(File.separator, "/");

                                JsonObject json = JsonParser.parseReader(
                                        new InputStreamReader(Files.newInputStream(file))
                                ).getAsJsonObject();

                                // Add pack namespace to workstation data for creative tab tracking
                                if (type == DataType.WORKSTATIONS) {
                                    json.addProperty("_pack_namespace", currentPackNamespace);
                                }

                                allData.get(type).put(fileName, json);
                                LOGGER.debug("Loaded {} from {} {}: {}", type.name(), packSource, pack.getName(), fileName);
                            } catch (Exception e) {
                                LOGGER.error("Failed to load file: {}", file, e);
                            }
                        });
            } catch (Exception e) {
                LOGGER.error("Failed to read {} directory in pack {}", type.getPath(), pack.getName(), e);
            }
        }
    }

    public enum DataType {
        STRUCTURE_TAGS("structure_tags"),
        WORKSTATIONS("workstations"),
        POI_TYPES("poi_types"),
        TYPES("types"),
        PROFESSIONS("professions"),
        TRADES("trades"),
        GIFTS("gifts"),
        BIOME_TRADES("biome_trades"),
        HERO_GIFTS("hero_of_the_village"),
        BIOME_MAPPINGS("biome_mappings");

        private final String path;

        DataType(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

    public List<VillagerPack> getDiscoveredPacks() {
        try {
            return discoverPacks();
        } catch (Exception e) {
            LOGGER.error("Failed to discover packs", e);
            return Collections.emptyList();
        }
    }

    public static class VillagerPack implements AutoCloseable {
        private final Path sourcePath;
        private final String name;
        private final FileSystem zipFileSystem;
        private final Path rootPath;
        private final boolean ownsFileSystem;
        private final boolean fromModsDirectory;
        private JsonObject packMeta;
        private VillagerPackConfig config;


        public VillagerPack(Path rootPath, String name, boolean fromMods) throws IOException {
            this.sourcePath = rootPath;
            this.rootPath = rootPath;
            this.name = name;
            this.fromModsDirectory = fromMods;
            this.zipFileSystem = null;
            this.ownsFileSystem = false;

            loadPackMeta();
            loadConfig();
        }

        public VillagerPack(Path path) throws IOException {
            this.sourcePath = path;

            // Check if this pack is from the mods directory
            //this.fromModsDirectory = path.toString().contains("mods" + File.separator) ||
            //        path.toString().contains("mods/");
            this.fromModsDirectory = false;

            if (Files.isDirectory(path)) {
                // Directory pack
                this.name = path.getFileName().toString();
                this.zipFileSystem = null;
                this.rootPath = path;
                this.ownsFileSystem = false;
            } else if (path.toString().endsWith(".zip") || path.toString().endsWith(".jar")) {
                // ZIP pack
                this.name = path.getFileName().toString().substring(0, path.getFileName().toString().length() - 4);
                URI uri = URI.create("jar:" + path.toUri());

                // Try to get existing filesystem first, create new one if it doesn't exist
                FileSystem fs;
                boolean created = false;
                try {
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                    created = true;
                }

                this.zipFileSystem = fs;
                this.rootPath = zipFileSystem.getPath("/");
                this.ownsFileSystem = created;
            } else {
                throw new IOException("Unsupported pack format: " + path);
            }

            loadPackMeta();
            loadConfig();
        }

        private void loadPackMeta() {
            Path metaFile = rootPath.resolve("pack.mcmeta");
            if (Files.exists(metaFile)) {
                try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(metaFile))) {
                    packMeta = JsonParser.parseReader(reader).getAsJsonObject();
                } catch (Exception e) {
                    LOGGER.warn("Failed to load pack.mcmeta for pack: {}", name, e);
                }
            }
        }

        private void loadConfig() {
            this.config = VillagerPackConfig.load(rootPath, name);
        }

        public boolean hasConfig() {
            Path configPath = rootPath.resolve("villagerapi_config.json");
            return Files.exists(configPath);
        }

        public boolean isValid() {
            // A pack is valid if it has villagerapi_config.json AND (pack.mcmeta OR villagers folder)
            return hasConfig() && (packMeta != null || Files.exists(rootPath.resolve("villagers")));
        }

        public boolean hasAssets() {
            return Files.exists(rootPath.resolve("assets"));
        }

        public boolean hasData() {
            return Files.exists(rootPath.resolve("data"));
        }

        public Path getPath() {
            return sourcePath;
        }

        public Path getRootPath() {
            return rootPath;
        }

        public String getName() {
            return name;
        }

        public String getNamespace() {
            return config.getNamespace();
        }

        public String getDisplayName() {
            return config.getDisplayName();
        }

        public boolean isFromModsDirectory() {
            return fromModsDirectory;
        }

        public VillagerPackConfig getConfig() {
            return config;
        }

        public JsonObject getPackMeta() {
            return packMeta;
        }

        public Path getIconPath() {
            Path iconPath = rootPath.resolve("pack.png");
            return Files.exists(iconPath) ? iconPath : null;
        }

        public boolean isZipPack() {
            return zipFileSystem != null;
        }

        @Override
        public void close() {
            if (zipFileSystem != null && ownsFileSystem) {
                try {
                    zipFileSystem.close();
                } catch (IOException e) {
                    LOGGER.error("Failed to close zip filesystem for pack: {}", name, e);
                }
            }
        }
    }
}