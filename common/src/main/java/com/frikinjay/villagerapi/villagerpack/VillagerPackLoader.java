package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackLoader {

    public static final String RESOURCE_PACKS_DIRECTORY = "resourcepacks";

    private static final Set<String> REPORTED_DUPLICATES = new HashSet<>();

    private static final Set<DataType> NAMESPACED_TYPES = EnumSet.of(
            DataType.STRUCTURE_TAGS,
            DataType.WORKSTATIONS,
            DataType.POI_TYPES,
            DataType.TYPES,
            DataType.PROFESSIONS
    );

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

        List<VillagerPack> packs = Collections.emptyList();
        try {
            packs = discoverAll(villagerPacksDir);

            int fromFolders = 0;
            int fromMods = 0;

            for (VillagerPack pack : packs) {
                if (pack.isFromModsDirectory()) {
                    fromMods++;
                } else {
                    fromFolders++;
                }
            }

            LOGGER.info("Found {} villager pack(s): {} from villagerpacks/ and resourcepacks/, {} from mods (manual ordering: {})",
                    packs.size(), fromFolders, fromMods, VillagerPackOrderConfig.isManualOrderingEnabled());

            for (int i = 0; i < packs.size(); i++) {
                VillagerPack pack = packs.get(i);
                LOGGER.info("  [{}] {} ({})", i + 1, pack.getName(), pack.getSourceDescription());
            }

            for (VillagerPack pack : packs) {
                if (pack.isValid()) {
                    loadPackData(pack, allData);
                } else {
                    LOGGER.warn("Skipping invalid pack: {} ({})", pack.getName(), pack.getSourceDescription());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load villager packs", e);
        } finally {
            for (VillagerPack pack : packs) {
                pack.close();
            }
        }

        return allData;
    }

    public static List<Path> getPackDirectories(Path villagerPacksDir) {
        List<Path> directories = new ArrayList<>();
        directories.add(villagerPacksDir);

        Path gameDirectory = villagerPacksDir.toAbsolutePath().getParent();
        if (gameDirectory != null) {
            directories.add(gameDirectory.resolve(RESOURCE_PACKS_DIRECTORY));
        }

        return directories;
    }

    public static List<VillagerPack> discoverAll(Path villagerPacksDir) {
        List<VillagerPack> opened = new ArrayList<>();

        try {
            for (Path directory : getPackDirectories(villagerPacksDir)) {
                scanDirectory(directory, directory.equals(villagerPacksDir), opened);
            }

            Map<String, Path> modRoots = new TreeMap<>(CommonPlatformHelper.getAllModRootPaths());
            for (Map.Entry<String, Path> entry : modRoots.entrySet()) {
                try {
                    VillagerPack pack = new VillagerPack(entry.getValue(), entry.getKey(), true);
                    if (pack.hasConfig()) {
                        opened.add(pack);
                    } else {
                        pack.close();
                    }
                } catch (IOException e) {
                    LOGGER.debug("Mod {} does not contain villagerpack configuration.", entry.getKey());
                }
            }

            Map<String, VillagerPack> uniqueByNamespace = new LinkedHashMap<>();
            for (VillagerPack pack : opened) {
                VillagerPack existing = uniqueByNamespace.putIfAbsent(pack.getName(), pack);
                if (existing != null) {
                    reportDuplicate(pack, existing);
                    pack.close();
                }
            }

            List<VillagerPack> unique = new ArrayList<>(uniqueByNamespace.values());
            List<String> names = unique.stream().map(VillagerPack::getName).collect(Collectors.toList());

            VillagerPackOrderConfig.sync(names);
            return VillagerPackOrderConfig.sort(unique, VillagerPack::getName);
        } catch (Exception e) {
            LOGGER.error("Failed to discover villagerpacks", e);
            for (VillagerPack pack : opened) {
                pack.close();
            }
            return new ArrayList<>();
        }
    }

    private static void scanDirectory(Path directory, boolean dedicatedPackDirectory, List<VillagerPack> opened) {
        if (!Files.isDirectory(directory)) return;

        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> candidates = paths
                    .filter(VillagerPackLoader::isPackCandidate)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            for (Path path : candidates) {
                try {
                    VillagerPack pack = new VillagerPack(path);
                    if (pack.hasConfig()) {
                        opened.add(pack);
                    } else {
                        pack.close();
                    }
                } catch (IOException e) {
                    if (dedicatedPackDirectory) {
                        LOGGER.error("Failed to open villagerpack '{}' in {}/", path.getFileName(), directory.getFileName(), e);
                    } else {
                        LOGGER.debug("Skipping '{}' in {}/, it could not be opened as a pack", path.getFileName(), directory.getFileName());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list {} directory", directory, e);
        }
    }

    private static void reportDuplicate(VillagerPack ignored, VillagerPack kept) {
        String key = ignored.getName() + "|" + ignored.getSourceDescription() + "|" + kept.getSourceDescription();
        synchronized (REPORTED_DUPLICATES) {
            if (!REPORTED_DUPLICATES.add(key)) return;
        }
        LOGGER.error("Villagerpack {} is ignored: its namespace '{}' is already used by {}. Each villagerpack must use a unique namespace in its villagerapi_config.json",
                ignored.getSourceDescription(), ignored.getName(), kept.getSourceDescription());
    }

    static boolean isPackCandidate(Path path) {
        if (Files.isDirectory(path)) return true;
        if (!Files.isRegularFile(path)) return false;
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".zip") || fileName.endsWith(".jar");
    }

    private void loadPackData(VillagerPack pack, Map<DataType, Map<String, JsonObject>> allData) {
        String packSource = pack.isFromModsDirectory() ? "mod JAR" : "villagerpack";
        LOGGER.info("Loading {} '{}' (namespace: {})", packSource, pack.getDisplayName(), pack.getNamespace());

        String currentPackNamespace = pack.getNamespace();

        for (DataType type : DataType.values()) {
            Path typeDir = pack.getRootPath().resolve("villagers").resolve(type.getPath());

            if (!Files.exists(typeDir)) {
                continue;
            }

            try (Stream<Path> files = Files.walk(typeDir)) {
                files.filter(p -> p.toString().endsWith(".json"))
                        .sorted()
                        .forEach(file -> {
                            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                                String fileName = typeDir.relativize(file).toString()
                                        .replace(".json", "")
                                        .replace(File.separator, "/")
                                        .replace("\\", "/");

                                JsonElement element = JsonParser.parseReader(reader);
                                if (!element.isJsonObject()) {
                                    LOGGER.warn("Skipping {} file that is not a JSON object: {}", type.name(), file);
                                    return;
                                }
                                JsonObject json = element.getAsJsonObject();

                                if (NAMESPACED_TYPES.contains(type) && !json.has("namespace")) {
                                    json.addProperty("namespace", currentPackNamespace);
                                }

                                if (type == DataType.WORKSTATIONS) {
                                    json.addProperty("_pack_namespace", currentPackNamespace);
                                }

                                JsonObject existing = allData.get(type).putIfAbsent(fileName, json);
                                if (existing != null) {
                                    if (type != DataType.TRADES && type != DataType.BIOME_TRADES) {
                                        LOGGER.warn("{} '{}' from pack '{}' ignored, a higher priority pack already provides it",
                                                type.name(), fileName, pack.getName());
                                    }
                                    return;
                                }

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
        return discoverAll(villagerPacksDir);
    }

    public static class VillagerPack implements AutoCloseable {
        private final Path sourcePath;
        private final String sourceName;
        private final String name;
        private final FileSystem zipFileSystem;
        private final Path rootPath;
        private final boolean ownsFileSystem;
        private final boolean fromModsDirectory;
        private final VillagerPackConfig config;
        private JsonObject packMeta;

        public VillagerPack(Path path, String sourceName, boolean fromMods) throws IOException {
            this.sourcePath = path;
            this.sourceName = sourceName;
            this.fromModsDirectory = fromMods;

            if (Files.isRegularFile(path) && (path.toString().endsWith(".zip") || path.toString().endsWith(".jar"))) {
                URI uri = URI.create("jar:" + path.toUri());

                FileSystem fs;
                boolean created = false;
                try {
                    fs = FileSystems.getFileSystem(uri);
                    LOGGER.debug("Reusing existing filesystem for pack: {}", sourceName);
                } catch (FileSystemNotFoundException e) {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                    created = true;
                    LOGGER.debug("Created new filesystem for pack: {}", sourceName);
                }

                this.zipFileSystem = fs;
                this.rootPath = fs.getPath("/");
                this.ownsFileSystem = created;
            } else {
                this.rootPath = path;
                this.zipFileSystem = null;
                this.ownsFileSystem = false;
            }

            this.config = VillagerPackConfig.load(rootPath, sourceName);
            this.name = config.getNamespace();

            loadPackMeta();
        }

        public VillagerPack(Path path) throws IOException {
            this(requireSupported(path), stripExtension(path), false);
        }

        private static Path requireSupported(Path path) throws IOException {
            if (Files.isDirectory(path)) return path;
            String fileName = path.getFileName().toString();
            if (fileName.endsWith(".zip") || fileName.endsWith(".jar")) return path;
            throw new IOException("Unsupported pack format: " + path);
        }

        private static String stripExtension(Path path) {
            String fileName = path.getFileName().toString();
            if (!Files.isDirectory(path) && (fileName.endsWith(".zip") || fileName.endsWith(".jar"))) {
                return fileName.substring(0, fileName.length() - 4);
            }
            return fileName;
        }

        private void loadPackMeta() {
            Path metaFile = rootPath.resolve("pack.mcmeta");
            if (Files.exists(metaFile)) {
                try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(metaFile), StandardCharsets.UTF_8)) {
                    packMeta = JsonParser.parseReader(reader).getAsJsonObject();
                } catch (Exception e) {
                    LOGGER.warn("Failed to load pack.mcmeta for pack: {} ({})", name, getSourceDescription(), e);
                }
            }
        }

        public boolean hasConfig() {
            return Files.exists(rootPath.resolve("villagerapi_config.json"));
        }

        public boolean isValid() {
            return hasConfig() && (packMeta != null || Files.exists(rootPath.resolve("villagers")));
        }

        public boolean hasAssets() {
            return Files.exists(rootPath.resolve("assets"));
        }

        public boolean hasData() {
            return Files.exists(rootPath.resolve("data"));
        }

        public boolean hasServerContent() {
            return hasData() || Files.exists(rootPath.resolve("villagers"));
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

        public String getSourceName() {
            return sourceName;
        }

        public String getSourceDescription() {
            if (fromModsDirectory) {
                return "mod '" + sourceName + "'";
            }
            Path fileName = sourcePath.getFileName();
            Path parent = sourcePath.toAbsolutePath().getParent();
            String folder = parent != null && parent.getFileName() != null ? parent.getFileName().toString() : "villagerpacks";
            return "'" + (fileName != null ? fileName.toString() : sourceName) + "' in " + folder + "/";
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
            if (zipFileSystem != null && ownsFileSystem && zipFileSystem.isOpen()) {
                try {
                    zipFileSystem.close();
                    LOGGER.debug("Closed filesystem for pack: {} ({})", name, getSourceDescription());
                } catch (IOException e) {
                    LOGGER.error("Failed to close zip filesystem for pack: {} ({})", name, getSourceDescription(), e);
                }
            }
        }
    }
}