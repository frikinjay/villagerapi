package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackValidator {

    public static class ValidationResult {
        private final boolean valid;
        private final boolean criticalFailure; // Prevents pack from loading
        private final List<String> errors;
        private final List<String> warnings;
        private final Map<String, Object> info;

        public ValidationResult(boolean valid, boolean criticalFailure, List<String> errors, List<String> warnings, Map<String, Object> info) {
            this.valid = valid;
            this.criticalFailure = criticalFailure;
            this.errors = errors;
            this.warnings = warnings;
            this.info = info;
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isCriticalFailure() {
            return criticalFailure;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public Map<String, Object> getInfo() {
            return info;
        }

        public void logResults(String packName) {
            if (criticalFailure) {
                LOGGER.error("=== CRITICAL VALIDATION FAILURE for pack '{}' - PACK WILL NOT LOAD ===", packName);
                errors.forEach(err -> LOGGER.error("  [CRITICAL] {}", err));
            } else if (!errors.isEmpty()) {
                LOGGER.error("Validation errors for pack '{}' ({} error(s)):", packName, errors.size());
                errors.forEach(err -> LOGGER.error("  [ERROR] {}", err));
            }

            if (!warnings.isEmpty()) {
                LOGGER.warn("Validation warnings for pack '{}' ({} warning(s)):", packName, warnings.size());
                warnings.forEach(warn -> LOGGER.warn("  [WARN] {}", warn));
            }

            if (valid || !criticalFailure) {
                LOGGER.info("Pack '{}' validation completed - Pack will load", packName);
                if (!info.isEmpty()) {
                    info.forEach((key, value) -> LOGGER.info("  - {}: {}", key, value));
                }
            }
        }
    }

    public static ValidationResult validatePack(Path packPath, String packName) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> info = new HashMap<>();
        boolean criticalFailure = false;

        try {
            // Use the NEW constructor that handles Mod IDs and Root Paths safely
            // fromMods is true if the name matches a Mod ID, otherwise we use the original logic
            VillagerPackLoader.VillagerPack pack = new VillagerPackLoader.VillagerPack(packPath, packName, !packName.contains("."));

            // Use the existing class method to check for config
            if (!pack.hasConfig()) {
                errors.add("CRITICAL: No villagerapi_config.json found - this is not a valid villagerpack");
                pack.close();
                return new ValidationResult(false, true, errors, warnings, info);
            }

            // Utilize existing helper methods for structure validation
            criticalFailure = !validatePackStructure(pack, errors, warnings, info);

            // Continue with existing modular validation methods
            validatePackMeta(pack, errors, warnings, info);
            validateVillagerData(pack, errors, warnings, info);
            validateDatapacks(pack, errors, warnings, info);
            validateResourcePacks(pack, errors, warnings, info);

            pack.close();

            boolean hasErrors = !errors.isEmpty();
            return new ValidationResult(!hasErrors, criticalFailure, errors, warnings, info);
        } catch (Exception e) {
            // We use the passed-in packName here to avoid another NullPointerException
            LOGGER.error("Validation crashed for pack {}: {}", packName, e.getMessage());
            errors.add("CRITICAL: Failed to load pack: " + e.getMessage());
            return new ValidationResult(false, true, errors, warnings, info);
        }
    }

    private static boolean validatePackStructure(VillagerPackLoader.VillagerPack pack,
                                                 List<String> errors,
                                                 List<String> warnings,
                                                 Map<String, Object> info) {
        Path root = pack.getRootPath();

        boolean hasVillagers = Files.exists(root.resolve("villagers"));
        boolean hasData = Files.exists(root.resolve("data"));
        boolean hasAssets = Files.exists(root.resolve("assets"));

        info.put("Has villagers folder", hasVillagers);
        info.put("Has data folder", hasData);
        info.put("Has assets folder", hasAssets);

        if (!hasVillagers && !hasData) {
            errors.add("CRITICAL: Pack has no villagers/ or data/ folders - nothing to load");
            return false; // Critical failure
        }

        if (!hasVillagers) {
            warnings.add("No villagers/ folder found - no custom villager content will be loaded");
        }

        if (!hasAssets) {
            warnings.add("No assets/ folder found - no custom textures/models will be loaded");
        }

        return true; // Not a critical failure
    }

    private static void validatePackMeta(VillagerPackLoader.VillagerPack pack,
                                         List<String> errors,
                                         List<String> warnings,
                                         Map<String, Object> info) {
        // config.json validation
        VillagerPackConfig config = pack.getConfig();
        info.put("Namespace", config.getNamespace());
        info.put("Display Name", config.getDisplayName());
        info.put("Version", config.getVersion());
        info.put("Author", config.getAuthor());

        if (!config.getDescription().isEmpty()) {
            info.put("Description", config.getDescription());
        }

        // pack.mcmeta validation (NON-CRITICAL)
        JsonObject meta = pack.getPackMeta();

        if (meta == null) {
            warnings.add("No pack.mcmeta found - datapacks and resource packs may not load properly");
            return;
        }

        if (meta.has("pack")) {
            JsonObject packInfo = meta.getAsJsonObject("pack");
            if (packInfo.has("pack_format")) {
                int format = packInfo.get("pack_format").getAsInt();
                info.put("Pack format", format);

                // MC 1.21.1 uses pack format 48 for data and 34 for resources
                if (format < 34) {
                    warnings.add("Pack format " + format + " is outdated for MC 1.21.1 (expected 34+ for resources, 48+ for data)");
                }
            } else {
                warnings.add("pack.mcmeta missing pack_format - may cause datapack/resource pack issues");
            }
        } else {
            warnings.add("pack.mcmeta missing 'pack' section");
        }
    }

    private static void validateVillagerData(VillagerPackLoader.VillagerPack pack,
                                             List<String> errors,
                                             List<String> warnings,
                                             Map<String, Object> info) {
        Path villagersPath = pack.getRootPath().resolve("villagers");
        if (!Files.exists(villagersPath)) {
            return;
        }

        int workstations = countJsonFiles(villagersPath.resolve("workstations"));
        int poiTypes = countJsonFiles(villagersPath.resolve("poi_types"));
        int professions = countJsonFiles(villagersPath.resolve("professions"));
        int types = countJsonFiles(villagersPath.resolve("types"));
        int trades = countJsonFiles(villagersPath.resolve("trades"));
        int gifts = countJsonFiles(villagersPath.resolve("gifts"));
        int structureTags = countJsonFiles(villagersPath.resolve("structure_tags"));
        int biomeMappings = countJsonFiles(villagersPath.resolve("biome_mappings"));
        int biomeTrades = countJsonFiles(villagersPath.resolve("biome_trades"));

        info.put("Workstations", workstations);
        info.put("POI Types", poiTypes);
        info.put("Professions", professions);
        info.put("Villager Types", types);
        info.put("Trades", trades);
        info.put("Gifts", gifts);
        info.put("Structure Tags", structureTags);
        info.put("Biome Mappings", biomeMappings);
        info.put("Biome Trades", biomeTrades);

        // Validate profession dependencies (CRITICAL)
        if (professions > 0) {
            validateProfessionDependencies(villagersPath, errors, warnings);
        }

        // Validate structure tag dependencies (SEMI-CRITICAL)
        if (structureTags > 0) {
            validateStructureTagDependencies(villagersPath, errors, warnings);
        }

        // Validate trade definitions (SEMI-CRITICAL)
        if (trades > 0) {
            validateTrades(villagersPath, errors, warnings);
        }
    }

    private static void validateProfessionDependencies(Path villagersPath,
                                                       List<String> errors,
                                                       List<String> warnings) {
        Path professionsPath = villagersPath.resolve("professions");
        Path poiTypesPath = villagersPath.resolve("poi_types");

        if (!Files.exists(professionsPath)) return;

        try (Stream<Path> files = Files.walk(professionsPath)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(file -> {
                        try {
                            JsonObject json = JsonParser.parseReader(
                                    new InputStreamReader(Files.newInputStream(file))
                            ).getAsJsonObject();

                            if (json.has("poi_type")) {
                                String poiType = json.get("poi_type").getAsString();

                                // Check if POI type exists in pack
                                Path poiFile = poiTypesPath.resolve(poiType + ".json");
                                if (!Files.exists(poiFile)) {
                                    // Check if it's a vanilla POI type
                                    if (!isVanillaPoiType(poiType)) {
                                        errors.add("Profession '" + file.getFileName() +
                                                "' references POI type '" + poiType +
                                                "' which doesn't exist in the pack or vanilla - profession will not work!");
                                    }
                                }
                            } else {
                                errors.add("Profession '" + file.getFileName() + "' missing required 'poi_type' field - profession will not work!");
                            }

                            if (!json.has("work_sound")) {
                                warnings.add("Profession '" + file.getFileName() + "' missing 'work_sound' field - will use default sound");
                            }

                            if (!json.has("namespace")) {
                                warnings.add("Profession '" + file.getFileName() + "' missing 'namespace' field - will use pack namespace");
                            }
                        } catch (Exception e) {
                            errors.add("Failed to validate profession file " + file.getFileName() + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            errors.add("Failed to read professions directory: " + e.getMessage());
        }
    }

    private static void validateStructureTagDependencies(Path villagersPath,
                                                         List<String> errors,
                                                         List<String> warnings) {
        Path structureTagsPath = villagersPath.resolve("structure_tags");

        if (!Files.exists(structureTagsPath)) return;

        try (Stream<Path> files = Files.walk(structureTagsPath)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(file -> {
                        try {
                            JsonObject json = JsonParser.parseReader(
                                    new InputStreamReader(Files.newInputStream(file))
                            ).getAsJsonObject();

                            if (!json.has("tag")) {
                                errors.add("Structure tag '" + file.getFileName() + "' missing required 'tag' field - map trades will fail!");
                            }
                            if (!json.has("map_decoration")) {
                                errors.add("Structure tag '" + file.getFileName() + "' missing required 'map_decoration' field - map trades will fail!");
                            }
                            if (!json.has("map_color")) {
                                errors.add("Structure tag '" + file.getFileName() + "' missing required 'map_color' field - map trades will fail!");
                            } else {
                                String color = json.get("map_color").getAsString();
                                if (!color.matches("^#[0-9A-Fa-f]{6}$")) {
                                    errors.add("Structure tag '" + file.getFileName() +
                                            "' has invalid map_color format (should be #RRGGBB): " + color);
                                }
                            }
                        } catch (Exception e) {
                            errors.add("Failed to validate structure tag file " + file.getFileName() + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            errors.add("Failed to read structure_tags directory: " + e.getMessage());
        }
    }

    private static void validateTrades(Path villagersPath,
                                       List<String> errors,
                                       List<String> warnings) {
        Path tradesPath = villagersPath.resolve("trades");

        if (!Files.exists(tradesPath)) return;

        try (Stream<Path> files = Files.walk(tradesPath)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(file -> {
                        try {
                            JsonObject json = JsonParser.parseReader(
                                    new InputStreamReader(Files.newInputStream(file))
                            ).getAsJsonObject();

                            if (!json.has("profession")) {
                                errors.add("Trade file '" + file.getFileName() + "' missing required 'profession' field - trades will not load!");
                                return;
                            }

                            if (!json.has("levels")) {
                                errors.add("Trade file '" + file.getFileName() + "' missing required 'levels' field - trades will not load!");
                                return;
                            }

                            JsonObject levels = json.getAsJsonObject("levels");
                            if (levels.size() == 0) {
                                warnings.add("Trade file '" + file.getFileName() + "' has no level definitions");
                            }
                        } catch (Exception e) {
                            errors.add("Failed to validate trade file " + file.getFileName() + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            errors.add("Failed to read trades directory: " + e.getMessage());
        }
    }

    private static void validateDatapacks(VillagerPackLoader.VillagerPack pack,
                                          List<String> errors,
                                          List<String> warnings,
                                          Map<String, Object> info) {
        Path dataPath = pack.getRootPath().resolve("data");
        if (!Files.exists(dataPath)) {
            return;
        }

        try (Stream<Path> namespaces = Files.list(dataPath)) {
            List<String> foundNamespaces = namespaces
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .toList();

            info.put("Data namespaces", String.join(", ", foundNamespaces));

            for (String namespace : foundNamespaces) {
                validateDataNamespace(dataPath.resolve(namespace), namespace, errors, warnings, info);
            }
        } catch (IOException e) {
            errors.add("Failed to read data directory: " + e.getMessage());
        }
    }

    private static void validateDataNamespace(Path namespacePath,
                                              String namespace,
                                              List<String> errors,
                                              List<String> warnings,
                                              Map<String, Object> info) {
        // Check for structure tags (CRITICAL for treasure map trades)
        Path structureTagsPath = namespacePath.resolve("tags/worldgen/structure");
        if (Files.exists(structureTagsPath)) {
            int tagCount = countJsonFiles(structureTagsPath);
            info.put("Structure tags in " + namespace, tagCount);

            // Validate structure tag files
            try (Stream<Path> files = Files.walk(structureTagsPath)) {
                files.filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            try {
                                JsonObject json = JsonParser.parseReader(
                                        new InputStreamReader(Files.newInputStream(file))
                                ).getAsJsonObject();

                                if (!json.has("values") || !json.get("values").isJsonArray()) {
                                    errors.add("Structure tag " + namespace + ":" +
                                            structureTagsPath.relativize(file).toString().replace(".json", "") +
                                            " missing or invalid 'values' array - treasure map trades will fail!");
                                } else {
                                    JsonElement values = json.get("values");
                                    if (values.getAsJsonArray().isEmpty()) {
                                        warnings.add("Structure tag " + namespace + ":" +
                                                structureTagsPath.relativize(file).toString().replace(".json", "") +
                                                " has empty values array - treasure map trades will not find structures");
                                    }
                                }
                            } catch (Exception e) {
                                errors.add("Failed to validate structure tag " + file.getFileName() + ": " + e.getMessage());
                            }
                        });
            } catch (IOException e) {
                errors.add("Failed to read structure tags: " + e.getMessage());
            }
        }
    }

    private static void validateResourcePacks(VillagerPackLoader.VillagerPack pack,
                                              List<String> errors,
                                              List<String> warnings,
                                              Map<String, Object> info) {
        Path assetsPath = pack.getRootPath().resolve("assets");
        if (!Files.exists(assetsPath)) {
            return;
        }

        try (Stream<Path> namespaces = Files.list(assetsPath)) {
            List<String> foundNamespaces = namespaces
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .toList();

            info.put("Asset namespaces", String.join(", ", foundNamespaces));

            // NON-CRITICAL: Check for common resource locations but don't fail
            for (String namespace : foundNamespaces) {
                validateResourceNamespace(assetsPath.resolve(namespace), namespace, warnings, info);
            }
        } catch (IOException e) {
            // NON-CRITICAL: Log as warning instead of error
            warnings.add("Failed to read assets directory: " + e.getMessage());
        }
    }

    private static void validateResourceNamespace(Path namespacePath,
                                                  String namespace,
                                                  List<String> warnings,
                                                  Map<String, Object> info) {
        // Check for common resource locations
        Path texturesPath = namespacePath.resolve("textures");
        Path modelsPath = namespacePath.resolve("models");
        Path langPath = namespacePath.resolve("lang");

        if (Files.exists(texturesPath)) {
            int textureCount = countFiles(texturesPath, ".png");
            if (textureCount > 0) {
                info.put("Textures in " + namespace, textureCount);
            }
        }

        if (Files.exists(modelsPath)) {
            int modelCount = countJsonFiles(modelsPath);
            if (modelCount > 0) {
                info.put("Models in " + namespace, modelCount);
            }
        }

        if (Files.exists(langPath)) {
            int langCount = countJsonFiles(langPath);
            if (langCount > 0) {
                info.put("Language files in " + namespace, langCount);
            }
        }

        // Only warn if assets exist but seem incomplete
        boolean hasTextures = Files.exists(texturesPath);
        boolean hasModels = Files.exists(modelsPath);

        if (hasTextures && !hasModels) {
            warnings.add("Resource pack has textures but no models in '" + namespace + "' - textures may not display");
        }
    }

    private static int countJsonFiles(Path path) {
        return countFiles(path, ".json");
    }

    private static int countFiles(Path path, String extension) {
        if (!Files.exists(path)) {
            return 0;
        }

        try (Stream<Path> files = Files.walk(path)) {
            return (int) files.filter(p -> p.toString().endsWith(extension)).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private static boolean isVanillaPoiType(String poiType) {
        // List of vanilla POI types
        Set<String> vanillaTypes = Set.of(
                "armorer", "butcher", "cartographer", "cleric", "farmer",
                "fisherman", "fletcher", "leatherworker", "librarian",
                "stone_mason", "mason", "shepherd", "toolsmith", "weaponsmith",
                "home", "meeting", "beehive", "bee_nest", "nether_portal",
                "lodestone"
        );

        // Handle namespaced IDs
        String typeName = poiType.contains(":") ?
                poiType.substring(poiType.indexOf(":") + 1) : poiType;

        return vanillaTypes.contains(typeName);
    }

    public static void validateAllPacks(Path villagerPacksDir) {
        LOGGER.info("=== Starting VillagerPack Validation ===");

        Map<Path, String> pathsToValidate = new LinkedHashMap<>();

        // External scanning
        if (Files.exists(villagerPacksDir)) {
            try (Stream<Path> paths = Files.list(villagerPacksDir)) {
                paths.filter(path -> Files.isDirectory(path) ||
                                (Files.isRegularFile(path) && (path.toString().endsWith(".zip") || path.toString().endsWith(".jar"))))
                        .forEach(p -> {
                            pathsToValidate.put(p, p.getFileName().toString());
                        });
            } catch (IOException e) {
                LOGGER.error("Failed to read villagerpacks directory: {}", e.getMessage());
            }
        }

        // Mod scanning
        CommonPlatformHelper.getAllModRootPaths().forEach((modId, rootPath) -> {
            if (Files.exists(rootPath.resolve("villagerapi_config.json"))) {
                pathsToValidate.put(rootPath, modId);
            }
        });

        int validPacks = 0;
        int invalidPacks = 0;
        int criticalFailures = 0;

        for (Map.Entry<Path, String> entry : pathsToValidate.entrySet()) {
            Path packPath = entry.getKey();
            String packName = entry.getValue();

            LOGGER.info("Validating pack: {}", packName);

            ValidationResult result = validatePack(packPath, packName);
            result.logResults(packName);

            if (result.isCriticalFailure()) {
                criticalFailures++;
                invalidPacks++;
            } else if (result.isValid()) {
                validPacks++;
            } else {
                invalidPacks++;
            }
            LOGGER.info("---");
        }

        LOGGER.info("=== Validation Complete ===");
        LOGGER.info("Total packs identified: {}, Valid: {}, Invalid: {}, Critical failures: {}",
                pathsToValidate.size(), validPacks, invalidPacks, criticalFailures);

        if (criticalFailures > 0) {
            LOGGER.error("{} pack(s) have critical failures and will not load correctly!", criticalFailures);
        }
    }
}