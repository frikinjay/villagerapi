package com.frikinjay.villagerapi.villagerpack.village;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.villagerpack.VillagerPackLoader;
import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public final class VillageGenerator {

    private static final String OUTPUT_DIRECTORY = ".villagerapi/generated";
    private static final String LEGACY_OUTPUT_DIRECTORY = ".villagerapi/generated_villages";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_POOL_WEIGHT = 150;
    private static final String REPURPOSED_STRUCTURES = "repurposed_structures";

    private static volatile RuntimeData runtimeData = RuntimeData.EMPTY;

    private VillageGenerator() {}

    public static RuntimeData getRuntimeData() {
        return runtimeData;
    }

    public static String generatedPackId(String packKey) {
        return "villagerpack:" + packKey + "/generated";
    }

    public static synchronized Map<String, Path> rebuild() {
        Path gameDirectory = CommonPlatformHelper.getGameDirectory();
        Path output = gameDirectory.resolve(OUTPUT_DIRECTORY);
        GeneratedFiles files = new GeneratedFiles();
        RuntimeData.Builder runtime = new RuntimeData.Builder();

        VillagerPackLoader loader = new VillagerPackLoader(gameDirectory);
        List<VillagerPackLoader.VillagerPack> packs = loader.getDiscoveredPacks();

        try (VillageSourceReader reader = new VillageSourceReader(packs)) {
            Map<Identifier, VillageTypeDef> types = collectVillageTypes(packs);
            List<StructureAdditionDef> additions = collectStructureAdditions(packs);

            Map<Identifier, List<StructureAdditionDef>> additionsByTarget = new HashMap<>();
            for (StructureAdditionDef addition : additions) {
                additionsByTarget.computeIfAbsent(addition.target(), k -> new ArrayList<>()).add(addition);
            }

            Map<String, List<Identifier>> villagesByPack = new LinkedHashMap<>();
            for (VillageTypeDef def : types.values()) {
                try {
                    if (generateVillageType(def, additionsByTarget.getOrDefault(def.id(), List.of()), reader, files, runtime)) {
                        villagesByPack.computeIfAbsent(def.packKey(), k -> new ArrayList<>()).add(def.id());
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to generate village type {} from villagerpack '{}'", def.id(), def.packKey(), e);
                }
            }

            RepurposedStructuresCompat repurposedStructures = new RepurposedStructuresCompat();
            for (StructureAdditionDef addition : additions) {
                if (types.containsKey(addition.target())) continue;
                try {
                    if (REPURPOSED_STRUCTURES.equals(addition.target().getNamespace())) {
                        addRepurposedStructuresHouses(addition, reader, files, runtime, repurposedStructures);
                    } else {
                        addRuntimeHouses(addition, files, runtime);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to prepare village houses from '{}' in villagerpack '{}'", addition.folderName(), addition.packKey(), e);
                }
            }
            repurposedStructures.write(files, runtime);

            villagesByPack.forEach((packKey, villages) -> {
                JsonObject villageTag = new JsonObject();
                villageTag.addProperty("replace", false);
                JsonArray values = new JsonArray();
                villages.forEach(id -> values.add(id.toString()));
                villageTag.add("values", values);
                files.put(packKey, "data/minecraft/tags/worldgen/structure/village.json", json(villageTag), runtime);
            });
        } catch (Exception e) {
            LOGGER.error("Failed to generate villagerpack villages", e);
        } finally {
            for (VillagerPackLoader.VillagerPack pack : packs) {
                pack.close();
            }
        }

        runtimeData = runtime.build();

        Map<String, Path> generatedPacks = new LinkedHashMap<>();
        try {
            deleteDirectory(output);
            deleteDirectory(gameDirectory.resolve(LEGACY_OUTPUT_DIRECTORY));

            for (Map.Entry<String, Map<String, byte[]>> pack : files.byPack().entrySet()) {
                Path packDirectory = output.resolve(pack.getKey());
                Map<String, byte[]> packFiles = new LinkedHashMap<>(pack.getValue());
                packFiles.put("pack.mcmeta", json(packMeta(pack.getKey())));

                for (Map.Entry<String, byte[]> entry : packFiles.entrySet()) {
                    Path target = packDirectory.resolve(entry.getKey());
                    Files.createDirectories(target.getParent());
                    Files.write(target, entry.getValue());
                }

                generatedPacks.put(pack.getKey(), packDirectory);
                LOGGER.info("Generated {} village file(s) for villagerpack '{}'", pack.getValue().size(), pack.getKey());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write generated villages to {}", output, e);
        }

        return generatedPacks;
    }

    private static boolean generateVillageType(VillageTypeDef def, List<StructureAdditionDef> additions, VillageSourceReader reader,
                                               GeneratedFiles files, RuntimeData.Builder runtime) throws IOException {
        Identifier sourceStructureFile = resourceId(def.copyFrom(), "worldgen/structure/", ".json");
        VillageSourceReader.Source structureSource = reader.read(sourceStructureFile);
        if (structureSource == null) {
            LOGGER.error("Village type {} copies from {}, but that structure couldn't be found", def.id(), def.copyFrom());
            return false;
        }
        trackSource(runtime, structureSource, sourceStructureFile, def.id());

        JsonObject structureJson = JsonParser.parseString(new String(structureSource.bytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        if (!structureJson.has("start_pool") || !structureJson.get("start_pool").isJsonPrimitive()) {
            LOGGER.error("Village type {} copies from {}, which is not a jigsaw structure with a start_pool", def.id(), def.copyFrom());
            return false;
        }

        Identifier startPool = Identifier.parse(structureJson.get("start_pool").getAsString());
        int lastSlash = startPool.getPath().lastIndexOf('/');
        if (lastSlash <= 0) {
            LOGGER.error("Village type {} can't be copied: the start pool {} of {} is not inside a folder", def.id(), startPool, def.copyFrom());
            return false;
        }

        String shortName = def.name().startsWith("village_") ? def.name().substring("village_".length()) : def.name();
        Rewriter rewriter = new Rewriter(startPool.getNamespace(), startPool.getPath().substring(0, lastSlash + 1),
                def.id().getNamespace(), "village/" + shortName + "/");

        Set<String> unusedOverrides = listOverrides(def.overridesDir());
        Set<Identifier> copiedTemplates = new HashSet<>();
        Map<Identifier, JsonObject> pools = new LinkedHashMap<>();
        Deque<Identifier> queue = new ArrayDeque<>();
        Set<Identifier> queued = new HashSet<>();
        enqueue(queue, queued, startPool, rewriter);

        List<PreparedHouse> houses = new ArrayList<>();
        for (StructureAdditionDef addition : additions) {
            for (HouseDef house : addition.allHouses()) {
                if (house.file() == null) {
                    houses.add(new PreparedHouse(house, addition.location(house)));
                    continue;
                }
                byte[] bytes = Files.readAllBytes(house.file());
                CompoundTag template = VillageNbt.read(bytes);
                for (String pool : VillageNbt.rewriteJigsawPools(template, rewriter::rewrite)) {
                    enqueue(queue, queued, parse(pool), rewriter);
                }
                Identifier location = addition.location(house);
                files.put(addition.packKey(), dataPath(location, "structure/", ".nbt"), VillageNbt.write(template), runtime);
                houses.add(new PreparedHouse(house, location));
            }
        }

        while (!queue.isEmpty()) {
            Identifier poolId = queue.poll();
            Identifier poolFile = resourceId(poolId, "worldgen/template_pool/", ".json");
            VillageSourceReader.Source poolSource = reader.read(poolFile);
            if (poolSource == null) {
                LOGGER.warn("Village type {}: pool {} referenced by {} couldn't be found, it will be left out", def.id(), poolId, def.copyFrom());
                continue;
            }
            trackSource(runtime, poolSource, poolFile, def.id());

            JsonObject pool = JsonParser.parseString(new String(poolSource.bytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            pools.put(poolId, pool);

            if (pool.has("fallback") && pool.get("fallback").isJsonPrimitive()) {
                enqueue(queue, queued, parse(pool.get("fallback").getAsString()), rewriter);
            }

            for (Identifier location : elementLocations(pool)) {
                if (!rewriter.owns(location) || !copiedTemplates.add(location)) continue;

                String relative = location.getPath().substring(rewriter.sourceDir().length());
                byte[] bytes = readOverride(def.overridesDir(), relative);
                if (bytes != null) {
                    unusedOverrides.remove(relative);
                } else {
                    Identifier templateFile = resourceId(location, "structure/", ".nbt");
                    VillageSourceReader.Source templateSource = reader.read(templateFile);
                    if (templateSource == null) {
                        LOGGER.warn("Village type {}: structure {} couldn't be found, it will be missing from the village", def.id(), location);
                        continue;
                    }
                    trackSource(runtime, templateSource, templateFile, def.id());
                    bytes = templateSource.bytes();
                }

                CompoundTag template = VillageNbt.read(bytes);
                for (String referenced : VillageNbt.rewriteJigsawPools(template, rewriter::rewrite)) {
                    enqueue(queue, queued, parse(referenced), rewriter);
                }
                files.put(def.packKey(), dataPath(rewriter.rewrite(location), "structure/", ".nbt"), VillageNbt.write(template), runtime);
            }
        }

        addHousesToPools(def, houses, rewriter, pools);

        for (Map.Entry<Identifier, JsonObject> entry : pools.entrySet()) {
            JsonObject rewritten = entry.getValue().deepCopy();
            if (rewritten.has("fallback") && rewritten.get("fallback").isJsonPrimitive()) {
                rewritten.addProperty("fallback", rewriter.rewrite(rewritten.get("fallback").getAsString()));
            }
            if (rewritten.has("elements") && rewritten.get("elements").isJsonArray()) {
                for (JsonElement weighted : rewritten.getAsJsonArray("elements")) {
                    if (weighted.isJsonObject() && weighted.getAsJsonObject().has("element")) {
                        rewriteElement(weighted.getAsJsonObject().get("element"), rewriter);
                    }
                }
            }
            files.put(def.packKey(), dataPath(rewriter.rewrite(entry.getKey()), "worldgen/template_pool/", ".json"), json(rewritten), runtime);
        }

        JsonObject structure = structureJson.deepCopy();
        structure.addProperty("start_pool", rewriter.rewrite(startPool).toString());
        structure.addProperty("biomes", "#" + def.id().getNamespace() + ":has_structure/" + def.name());
        files.put(def.packKey(), dataPath(def.id(), "worldgen/structure/", ".json"), json(structure), runtime);

        JsonObject biomeTag = new JsonObject();
        biomeTag.addProperty("replace", false);
        JsonArray biomeValues = new JsonArray();
        for (String biome : def.biomes()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", biome);
            entry.addProperty("required", false);
            biomeValues.add(entry);
        }
        biomeTag.add("values", biomeValues);
        files.put(def.packKey(), "data/" + def.id().getNamespace() + "/tags/worldgen/biome/has_structure/" + def.name() + ".json", json(biomeTag), runtime);

        for (String unused : unusedOverrides) {
            LOGGER.warn("Village type {}: override '{}' doesn't match any structure in {}, it is not used", def.id(), unused, def.copyFrom());
        }

        runtime.villageSetEntries.add(new RuntimeData.VillageSetEntry(def.id(), def.weight()));

        LOGGER.info("Generated village type {} from {} ({} pools, {} structures, {} added houses)",
                def.id(), def.copyFrom(), pools.size(), copiedTemplates.size(), houses.size());
        return true;
    }

    private static void addHousesToPools(VillageTypeDef def, List<PreparedHouse> houses, Rewriter rewriter, Map<Identifier, JsonObject> pools) {
        Identifier housesPool = Identifier.fromNamespaceAndPath(rewriter.sourceNamespace(), rewriter.sourceDir() + "houses");
        Identifier zombieHousesPool = Identifier.fromNamespaceAndPath(rewriter.sourceNamespace(), rewriter.sourceDir() + "zombie/houses");

        for (PreparedHouse prepared : houses) {
            Identifier poolId = prepared.house().zombie() ? zombieHousesPool : housesPool;
            JsonObject pool = pools.get(poolId);
            if (pool == null) {
                LOGGER.warn("Village type {} has no {} pool, house '{}' was not added",
                        def.id(), prepared.house().zombie() ? "zombie houses" : "houses", prepared.house().name());
                continue;
            }

            JsonArray elements = pool.has("elements") && pool.get("elements").isJsonArray() ? pool.getAsJsonArray("elements") : new JsonArray();
            JsonElement processors = prepared.house().processors() != null
                    ? new JsonPrimitive(prepared.house().processors())
                    : firstProcessors(elements);

            JsonObject element = new JsonObject();
            element.addProperty("element_type", prepared.house().legacy() ? "minecraft:legacy_single_pool_element" : "minecraft:single_pool_element");
            element.addProperty("location", prepared.location().toString());
            element.add("processors", processors);
            element.addProperty("projection", "rigid");

            JsonObject weighted = new JsonObject();
            weighted.add("element", element);
            weighted.addProperty("weight", prepared.house().weight());
            elements.add(weighted);
            pool.add("elements", elements);
        }
    }

    private static JsonElement firstProcessors(JsonArray elements) {
        for (JsonElement weighted : elements) {
            if (!weighted.isJsonObject()) continue;
            JsonElement element = weighted.getAsJsonObject().get("element");
            if (element != null && element.isJsonObject() && element.getAsJsonObject().has("processors")) {
                return element.getAsJsonObject().get("processors").deepCopy();
            }
        }
        JsonObject empty = new JsonObject();
        empty.add("processors", new JsonArray());
        return empty;
    }

    private static void addRuntimeHouses(StructureAdditionDef addition, GeneratedFiles files, RuntimeData.Builder runtime) throws IOException {
        for (HouseDef house : addition.allHouses()) {
            Identifier location = addition.location(house);
            if (house.file() != null) {
                files.put(addition.packKey(), dataPath(location, "structure/", ".nbt"), Files.readAllBytes(house.file()), runtime);
            }
            runtime.runtimeHouses.add(new RuntimeData.RuntimeHouse(addition.target(), house.zombie(), location, house.weight(), house.processors(), house.legacy()));
        }
    }

    private static void addRepurposedStructuresHouses(StructureAdditionDef addition, VillageSourceReader reader, GeneratedFiles files,
                                                      RuntimeData.Builder runtime, RepurposedStructuresCompat compat) throws IOException {
        String prefix = repurposedStructuresPoolPrefix(addition.target(), reader);

        for (HouseDef house : addition.allHouses()) {
            Identifier location = addition.location(house);
            if (house.file() != null) {
                files.put(addition.packKey(), dataPath(location, "structure/", ".nbt"), Files.readAllBytes(house.file()), runtime);
            }

            Identifier poolId = Identifier.parse(prefix + (house.zombie() ? "zombie/houses" : "houses"));
            RepurposedStructuresCompat.PoolAddition pool = compat.pool(addition.packKey(), addition.namespace(), poolId, () -> {
                JsonObject existing = reader.readJson(resourceId(poolId, "worldgen/template_pool/", ".json"));
                String fallback = existing != null && existing.has("fallback") && existing.get("fallback").isJsonPrimitive()
                        ? existing.get("fallback").getAsString()
                        : prefix + "terminators";
                JsonElement processors = existing != null && existing.has("elements") && existing.get("elements").isJsonArray()
                        ? firstProcessors(existing.getAsJsonArray("elements"))
                        : new JsonPrimitive("minecraft:empty");
                return new RepurposedStructuresCompat.PoolAddition(fallback, processors, new JsonArray());
            });

            JsonObject element = new JsonObject();
            element.addProperty("location", location.toString());
            element.add("processors", house.processors() != null ? new JsonPrimitive(house.processors()) : pool.defaultProcessors().deepCopy());
            element.addProperty("projection", "rigid");
            element.addProperty("element_type", house.legacy() ? "minecraft:legacy_single_pool_element" : "minecraft:single_pool_element");

            JsonObject weighted = new JsonObject();
            weighted.addProperty("weight", house.weight());
            weighted.add("element", element);
            pool.elements().add(weighted);

            if (house.maxCount() != null) {
                JsonObject count = new JsonObject();
                count.addProperty("nbt_piece_name", location.toString());
                count.addProperty("never_spawn_more_than_this_many", house.maxCount());
                compat.spawnCounts(addition.packKey(), addition.namespace(), addition.target()).add(count);
            }
        }

        LOGGER.info("Prepared Repurposed Structures compat for {} house(s) from '{}' in villagerpack '{}' targeting {}",
                addition.allHouses().size(), addition.folderName(), addition.packKey(), addition.target());
    }

    private static String repurposedStructuresPoolPrefix(Identifier structure, VillageSourceReader reader) {
        JsonObject structureJson = reader.readJson(resourceId(structure, "worldgen/structure/", ".json"));
        if (structureJson != null && structureJson.has("start_pool") && structureJson.get("start_pool").isJsonPrimitive()) {
            Identifier startPool = parse(structureJson.get("start_pool").getAsString());
            if (startPool != null) {
                int lastSlash = startPool.getPath().lastIndexOf('/');
                if (lastSlash > 0) {
                    return startPool.getNamespace() + ":" + startPool.getPath().substring(0, lastSlash + 1);
                }
            }
        }

        String name = structure.getPath().startsWith("village_") ? structure.getPath().substring("village_".length()) : structure.getPath();
        return structure.getNamespace() + ":villages/" + name + "/";
    }

    private static Map<Identifier, VillageTypeDef> collectVillageTypes(List<VillagerPackLoader.VillagerPack> packs) {
        Map<Identifier, VillageTypeDef> types = new LinkedHashMap<>();

        for (VillagerPackLoader.VillagerPack pack : packs) {
            for (Path file : jsonFiles(pack.getRootPath().resolve("villagers/village_types"))) {
                JsonObject json = readJson(file);
                if (json == null) continue;

                String context = "village type " + file.getFileName() + " in villagerpack '" + pack.getName() + "'";
                String namespace = string(json, "namespace", pack.getNamespace());
                String name = string(json, "name", null);
                String copyFrom = string(json, "copy_from", null);

                if (name == null || copyFrom == null) {
                    LOGGER.error("{} needs 'name' and 'copy_from', skipping it", context);
                    continue;
                }

                List<String> biomes = new ArrayList<>();
                if (json.has("biomes") && json.get("biomes").isJsonArray()) {
                    for (JsonElement biome : json.getAsJsonArray("biomes")) {
                        if (biome.isJsonPrimitive()) biomes.add(biome.getAsString().trim());
                    }
                }
                if (biomes.isEmpty()) {
                    LOGGER.error("{} has no 'biomes', so it could never generate. Skipping it", context);
                    continue;
                }

                Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
                VillageTypeDef def = new VillageTypeDef(pack.getName(), id, name, withDefaultNamespace(copyFrom, "minecraft"), biomes,
                        clamp(intValue(json, "weight", 1), 1, Integer.MAX_VALUE), file.getParent().resolve(name));

                VillageTypeDef existing = types.putIfAbsent(id, def);
                if (existing != null) {
                    LOGGER.warn("Village type {} from villagerpack '{}' is ignored, villagerpack '{}' already defines it", id, pack.getName(), existing.packKey());
                }
            }
        }

        return types;
    }

    private static List<StructureAdditionDef> collectStructureAdditions(List<VillagerPackLoader.VillagerPack> packs) {
        List<StructureAdditionDef> additions = new ArrayList<>();

        for (VillagerPackLoader.VillagerPack pack : packs) {
            for (Path file : jsonFiles(pack.getRootPath().resolve("villagers/village_structures"))) {
                JsonObject json = readJson(file);
                if (json == null) continue;

                String context = "village structures " + file.getFileName() + " in villagerpack '" + pack.getName() + "'";
                String target = string(json, "village_type", null);
                if (target == null) {
                    LOGGER.error("{} needs 'village_type', skipping it", context);
                    continue;
                }

                Path folder = file.getParent();
                String namespace = string(json, "namespace", pack.getNamespace());
                List<HouseDef> houses = readHouses(json, "houses", folder.resolve("houses"), false, context);
                List<HouseDef> zombieHouses = readHouses(json, "zombie_houses", folder.resolve("zombie_houses"), true, context);

                if (houses.isEmpty() && zombieHouses.isEmpty()) {
                    LOGGER.warn("{} doesn't add any houses", context);
                    continue;
                }

                additions.add(new StructureAdditionDef(pack.getName(), namespace, folder.getFileName().toString(),
                        withDefaultNamespace(target, pack.getNamespace()), houses, zombieHouses));
            }
        }

        return additions;
    }

    private static List<HouseDef> readHouses(JsonObject json, String key, Path directory, boolean zombie, String context) {
        List<HouseDef> houses = new ArrayList<>();
        Set<String> listed = new HashSet<>();

        if (json.has(key) && json.get(key).isJsonObject()) {
            JsonObject entries = json.getAsJsonObject(key);
            for (String name : entries.keySet()) {
                listed.add(name);
                JsonObject settings = entries.get(name).isJsonObject() ? entries.getAsJsonObject(name) : new JsonObject();

                String locationValue = string(settings, "location", null);
                Identifier location = null;
                Path file = null;

                if (locationValue != null) {
                    location = parse(locationValue);
                    if (location == null) {
                        LOGGER.error("{}: '{}' has an invalid location '{}'", context, name, locationValue);
                        continue;
                    }
                } else {
                    file = directory.resolve(name + ".nbt");
                    if (!Files.exists(file)) {
                        LOGGER.error("{}: '{}' is listed in '{}' but {} doesn't exist", context, name, key, file);
                        continue;
                    }
                }

                int weight = intValue(settings, "weight", 1);
                if (weight < 1 || weight > MAX_POOL_WEIGHT) {
                    LOGGER.warn("{}: weight {} for '{}' must be between 1 and {}, clamping it", context, weight, name, MAX_POOL_WEIGHT);
                    weight = clamp(weight, 1, MAX_POOL_WEIGHT);
                }

                String elementType = string(settings, "element_type", "legacy");
                boolean legacy = !(elementType.equals("single") || elementType.equals("minecraft:single_pool_element"));
                Integer maxCount = settings.has("max_count") && settings.get("max_count").isJsonPrimitive()
                        ? Math.max(0, intValue(settings, "max_count", 0))
                        : null;

                houses.add(new HouseDef(name, file, location, weight, string(settings, "processors", null), zombie, legacy, maxCount));
            }
        }

        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(p -> p.toString().endsWith(".nbt")).forEach(p -> {
                    String name = p.getFileName().toString();
                    name = name.substring(0, name.length() - 4);
                    if (!listed.contains(name)) {
                        LOGGER.warn("{}: {} is not listed in '{}', so it isn't added", context, p.getFileName(), key);
                    }
                });
            } catch (IOException ignored) {
            }
        }

        return houses;
    }

    private static List<Identifier> elementLocations(JsonObject pool) {
        List<Identifier> locations = new ArrayList<>();
        if (!pool.has("elements") || !pool.get("elements").isJsonArray()) return locations;

        for (JsonElement weighted : pool.getAsJsonArray("elements")) {
            if (weighted.isJsonObject() && weighted.getAsJsonObject().has("element")) {
                collectLocations(weighted.getAsJsonObject().get("element"), locations);
            }
        }
        return locations;
    }

    private static void collectLocations(JsonElement element, List<Identifier> output) {
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();

        if (object.has("location") && object.get("location").isJsonPrimitive()) {
            Identifier location = parse(object.get("location").getAsString());
            if (location != null) output.add(location);
        }

        if (object.has("elements") && object.get("elements").isJsonArray()) {
            for (JsonElement child : object.getAsJsonArray("elements")) {
                collectLocations(child, output);
            }
        }
    }

    private static void rewriteElement(JsonElement element, Rewriter rewriter) {
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();

        if (object.has("location") && object.get("location").isJsonPrimitive()) {
            object.addProperty("location", rewriter.rewrite(object.get("location").getAsString()));
        }

        if (object.has("elements") && object.get("elements").isJsonArray()) {
            for (JsonElement child : object.getAsJsonArray("elements")) {
                rewriteElement(child, rewriter);
            }
        }
    }

    private static void enqueue(Deque<Identifier> queue, Set<Identifier> queued, Identifier pool, Rewriter rewriter) {
        if (pool != null && rewriter.owns(pool) && queued.add(pool)) {
            queue.add(pool);
        }
    }

    private static void trackSource(RuntimeData.Builder runtime, VillageSourceReader.Source source, Identifier resource, Identifier villageType) {
        if (VillageSourceReader.VANILLA_ORIGIN.equals(source.origin())) {
            runtime.vanillaSources.putIfAbsent(resource, villageType);
        }
    }

    private static Set<String> listOverrides(Path directory) {
        Set<String> overrides = new TreeSet<>();
        if (!Files.isDirectory(directory)) return overrides;

        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(p -> p.toString().endsWith(".nbt")).forEach(p -> {
                String relative = directory.relativize(p).toString().replace("\\", "/");
                overrides.add(relative.substring(0, relative.length() - 4));
            });
        } catch (IOException e) {
            LOGGER.warn("Failed to list village overrides in {}", directory, e);
        }
        return overrides;
    }

    private static byte[] readOverride(Path directory, String relative) throws IOException {
        Path file = directory.resolve(relative + ".nbt");
        return Files.exists(file) ? Files.readAllBytes(file) : null;
    }

    private static List<Path> jsonFiles(Path directory) {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            LOGGER.warn("Failed to list {}", directory, e);
            return List.of();
        }
    }

    private static JsonObject readJson(Path file) {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element.isJsonObject()) return element.getAsJsonObject();
            LOGGER.warn("Skipping {}, it is not a JSON object", file);
        } catch (Exception e) {
            LOGGER.error("Failed to parse {}", file, e);
        }
        return null;
    }

    private static Identifier resourceId(Identifier id, String prefix, String suffix) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), prefix + id.getPath() + suffix);
    }

    private static String dataPath(Identifier id, String prefix, String suffix) {
        return "data/" + id.getNamespace() + "/" + prefix + id.getPath() + suffix;
    }

    private static Identifier generatedResource(String path) {
        if (!path.startsWith("data/") || path.contains("/tags/")) return null;
        String rest = path.substring("data/".length());
        int slash = rest.indexOf('/');
        return slash <= 0 ? null : Identifier.fromNamespaceAndPath(rest.substring(0, slash), rest.substring(slash + 1));
    }

    private static Identifier parse(String raw) {
        try {
            return Identifier.parse(raw.startsWith("#") ? raw.substring(1) : raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static Identifier withDefaultNamespace(String raw, String namespace) {
        String value = raw.trim();
        return value.contains(":") ? Identifier.parse(value) : Identifier.fromNamespaceAndPath(namespace, value);
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString().trim() : fallback;
    }

    private static int intValue(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isNumber()
                ? (int) Math.round(json.get(key).getAsDouble())
                : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static byte[] json(JsonObject object) {
        return GSON.toJson(object).getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject packMeta(String packKey) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Villages generated by VillagerAPI for villagerpack " + packKey);
        pack.addProperty("pack_format", 48);
        pack.addProperty("min_format", 38);
        pack.addProperty("max_format", 1000);
        JsonArray supported = new JsonArray();
        supported.add(38);
        supported.add(1000);
        pack.add("supported_formats", supported);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    static final class GeneratedFiles {
        private final Map<String, Map<String, byte[]>> byPack = new LinkedHashMap<>();

        void put(String packKey, String path, byte[] bytes, RuntimeData.Builder runtime) {
            byPack.computeIfAbsent(packKey, k -> new LinkedHashMap<>()).put(path, bytes);
            Identifier resource = generatedResource(path);
            if (resource != null) {
                runtime.generatedResources.put(resource, generatedPackId(packKey));
            }
        }

        Map<String, Map<String, byte[]>> byPack() {
            return byPack;
        }
    }

    record Rewriter(String sourceNamespace, String sourceDir, String targetNamespace, String targetDir) {

        boolean owns(Identifier id) {
            return id.getNamespace().equals(sourceNamespace) && id.getPath().startsWith(sourceDir);
        }

        Identifier rewrite(Identifier id) {
            return owns(id) ? Identifier.fromNamespaceAndPath(targetNamespace, targetDir + id.getPath().substring(sourceDir.length())) : id;
        }

        String rewrite(String raw) {
            Identifier id = parse(raw);
            return id == null ? raw : rewrite(id).toString();
        }
    }

    record VillageTypeDef(String packKey, Identifier id, String name, Identifier copyFrom, List<String> biomes, int weight, Path overridesDir) {}

    record HouseDef(String name, Path file, Identifier location, int weight, String processors, boolean zombie, boolean legacy, Integer maxCount) {}

    static final class RepurposedStructuresCompat {
        private final Map<String, Map<String, Map<Identifier, PoolAddition>>> pools = new LinkedHashMap<>();
        private final Map<String, Map<String, Map<Identifier, JsonArray>>> spawnCounts = new LinkedHashMap<>();

        PoolAddition pool(String packKey, String namespace, Identifier poolId, java.util.function.Supplier<PoolAddition> factory) {
            return pools.computeIfAbsent(packKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(namespace, k -> new LinkedHashMap<>())
                    .computeIfAbsent(poolId, k -> factory.get());
        }

        JsonArray spawnCounts(String packKey, String namespace, Identifier structure) {
            return spawnCounts.computeIfAbsent(packKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(namespace, k -> new LinkedHashMap<>())
                    .computeIfAbsent(structure, k -> new JsonArray());
        }

        void write(GeneratedFiles files, RuntimeData.Builder runtime) {
            pools.forEach((packKey, byNamespace) -> byNamespace.forEach((namespace, byPool) -> byPool.forEach((poolId, pool) -> {
                JsonObject json = new JsonObject();
                json.addProperty("target_pool", poolId.toString());
                json.addProperty("fallback", pool.fallback());
                json.add("elements", pool.elements());
                files.put(packKey, "data/" + namespace + "/rs_pool_additions/" + poolId.getPath() + ".json", json(json), runtime);
            })));

            spawnCounts.forEach((packKey, byNamespace) -> byNamespace.forEach((namespace, byStructure) -> byStructure.forEach((structure, counts) -> {
                JsonObject json = new JsonObject();
                json.addProperty("target_structure", structure.toString());
                json.add("pieces_spawn_counts", counts);
                files.put(packKey, "data/" + namespace + "/rs_pieces_spawn_counts/" + structure.getPath() + ".json", json(json), runtime);
            })));
        }

        record PoolAddition(String fallback, JsonElement defaultProcessors, JsonArray elements) {}
    }

    record PreparedHouse(HouseDef house, Identifier location) {}

    record StructureAdditionDef(String packKey, String namespace, String folderName, Identifier target, List<HouseDef> houses, List<HouseDef> zombieHouses) {

        List<HouseDef> allHouses() {
            List<HouseDef> all = new ArrayList<>(houses);
            all.addAll(zombieHouses);
            return all;
        }

        Identifier location(HouseDef house) {
            if (house.location() != null) {
                return house.location();
            }
            return Identifier.fromNamespaceAndPath(namespace, "village_structures/" + folderName + "/"
                    + (house.zombie() ? "zombie_houses/" : "houses/") + house.name());
        }
    }

    public record RuntimeData(List<VillageSetEntry> villageSetEntries, List<RuntimeHouse> runtimeHouses,
                              Map<Identifier, String> generatedResources, Map<Identifier, Identifier> vanillaSources) {

        static final RuntimeData EMPTY = new RuntimeData(List.of(), List.of(), Map.of(), Map.of());

        public record VillageSetEntry(Identifier structure, int weight) {}

        public record RuntimeHouse(Identifier villageType, boolean zombie, Identifier location, int weight, String processors, boolean legacy) {}

        static final class Builder {
            final List<VillageSetEntry> villageSetEntries = new ArrayList<>();
            final List<RuntimeHouse> runtimeHouses = new ArrayList<>();
            final Map<Identifier, String> generatedResources = new LinkedHashMap<>();
            final Map<Identifier, Identifier> vanillaSources = new LinkedHashMap<>();

            RuntimeData build() {
                return new RuntimeData(List.copyOf(villageSetEntries), List.copyOf(runtimeHouses),
                        Map.copyOf(generatedResources), Map.copyOf(vanillaSources));
            }
        }
    }
}
