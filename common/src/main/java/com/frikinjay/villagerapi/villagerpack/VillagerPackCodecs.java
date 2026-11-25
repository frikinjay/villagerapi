package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.VillagerAPI;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackCodecs {

    public static ProfessionData parseProfession(JsonObject json) {
        String poiType = json.get("poi_type").getAsString();
        String workSound = json.get("work_sound").getAsString();
        String namespace = json.get("namespace").getAsString();
        return new ProfessionData(poiType, Identifier.parse(workSound), namespace);
    }

    public static PoiTypeData parsePoiType(JsonObject json) {
        String block = json.get("block").getAsString();
        int tickets = json.has("tickets") ? json.get("tickets").getAsInt() : 1;
        String namespace = json.has("namespace") ? json.get("namespace").getAsString() : VillagerAPI.MOD_ID;
        return new PoiTypeData(Identifier.parse(block), tickets, namespace);
    }

    public static VillagerTypeData parseVillagerType(JsonObject json) {
        String name = json.get("name").getAsString();
        String namespace = json.has("namespace") ? json.get("namespace").getAsString() : VillagerAPI.MOD_ID;
        return new VillagerTypeData(name, namespace);
    }

    public static StructureTagData parseStructureTag(JsonObject json) {
        String name = json.get("tag").getAsString();
        String decoration = json.get("map_decoration").getAsString();
        String map_color = json.get("map_color").getAsString();
        String namespace = json.has("namespace") ? json.get("namespace").getAsString() : VillagerAPI.MOD_ID;
        return new StructureTagData(name, decoration, map_color, namespace);
    }

    public static WorkstationData parseWorkstation(JsonObject json) {
        String name = json.get("name").getAsString();
        String namespace = json.has("namespace") ? json.get("namespace").getAsString() : VillagerAPI.MOD_ID;
        return new WorkstationData(name, namespace);
    }

    public static GiftData parseGift(JsonObject json) {
        String profession = json.get("profession").getAsString();
        String lootTable = json.get("loot_table").getAsString();
        return new GiftData(profession, Identifier.parse(lootTable));
    }

    public static String resolveProfessionId(String profession, String packNamespace) {
        String trimmed = profession.trim();
        return trimmed.contains(":") ? trimmed : packNamespace + ":" + trimmed;
    }

    static void forEachJson(Path dir, String label, Consumer<JsonObject> action) {
        if (!Files.exists(dir)) return;

        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .forEach(file -> {
                        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                            JsonElement element = JsonParser.parseReader(reader);
                            if (element.isJsonObject()) {
                                action.accept(element.getAsJsonObject());
                            } else {
                                LOGGER.warn("Skipping {} file that is not a JSON object: {}", label, file);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse {} file: {}", label, file, e);
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Failed to walk {} directory: {}", label, dir, e);
        }
    }

    public static Map<Identifier, byte[]> generateVirtualFiles(String packKey, String packNamespace, Path rootPath) {
        return VillagerPackTrades.getFilesFor(packKey);
    }

    static Map<String, String> loadTagNamespaceMap(String packNamespace, Path rootPath) {
        Map<String, String> tagNamespaceMap = new HashMap<>();
        forEachJson(rootPath.resolve("villagers/structure_tags"), "structure tag", json -> {
            if (!json.has("tag")) return;

            String rawTag = json.get("tag").getAsString();
            String tagPath = rawTag.contains(":") ? rawTag.split(":", 2)[1] : rawTag;
            String namespace;
            if (rawTag.contains(":")) {
                namespace = rawTag.split(":", 2)[0];
            } else if (json.has("namespace")) {
                namespace = json.get("namespace").getAsString();
            } else {
                namespace = packNamespace;
            }

            tagNamespaceMap.put(tagPath, namespace);
        });
        return tagNamespaceMap;
    }

    static void applyItemModifierTransforms(String packNamespace, JsonObject tradeJson, Map<String, String> tagNamespaceMap) {
        if (!tradeJson.has("given_item_modifiers")) return;

        JsonElement modifiersElement = tradeJson.remove("given_item_modifiers");
        if (!modifiersElement.isJsonArray()) return;

        JsonArray modifiers = modifiersElement.getAsJsonArray();
        String derivedMapId = null;

        for (JsonElement element : modifiers) {
            if (!element.isJsonObject()) continue;
            JsonObject mod = element.getAsJsonObject();

            if (mod.has("function")) {
                String func = mod.remove("function").getAsString();
                mod.addProperty("type", func);

                if (func.equals("minecraft:exploration_map")) {
                    String targetNamespace = packNamespace;

                    if (mod.has("destination")) {
                        String dest = mod.get("destination").getAsString();
                        if (dest.startsWith("#")) dest = dest.substring(1);

                        String destPath = dest.contains(":") ? dest.split(":", 2)[1] : dest;
                        String destNamespace = dest.contains(":") ? dest.split(":", 2)[0] : null;

                        if (tagNamespaceMap.containsKey(destPath)) {
                            targetNamespace = tagNamespaceMap.get(destPath);
                        } else if (destNamespace != null) {
                            targetNamespace = destNamespace;
                        }

                        mod.addProperty("destination", "#" + targetNamespace + ":" + destPath);

                        String itemName = destPath;
                        if (itemName.startsWith("on_")) itemName = itemName.substring(3);
                        if (itemName.endsWith("_explorer_maps")) itemName = itemName.substring(0, itemName.length() - 14);
                        else if (itemName.endsWith("_maps")) itemName = itemName.substring(0, itemName.length() - 5);
                        itemName = itemName + "_map";

                        if (!targetNamespace.equals("minecraft")) {
                            derivedMapId = targetNamespace + ":" + itemName;
                        }
                    }

                    if (mod.has("decoration")) {
                        String decorationId = mod.remove("decoration").getAsString();
                        if (!decorationId.contains(":")) {
                            decorationId = targetNamespace + ":" + decorationId;
                        }
                        mod.addProperty("decoration", decorationId);
                    }
                }
            }

            if (mod.has("on_fail") && mod.get("on_fail").isJsonObject() && mod.getAsJsonObject("on_fail").has("function")) {
                JsonObject onFail = mod.getAsJsonObject("on_fail");
                onFail.addProperty("type", onFail.remove("function").getAsString());
            }

            if (mod.has("item_filter") && mod.get("item_filter").isJsonObject()) {
                JsonObject filter = mod.getAsJsonObject("item_filter");
                if (filter.has("items")) {
                    filter.remove("items");
                }
            }
        }

        tradeJson.add("given_item_modifier", modifiers);

        if (derivedMapId != null && tradeJson.has("gives") && tradeJson.get("gives").isJsonObject()) {
            tradeJson.getAsJsonObject("gives").addProperty("id", derivedMapId);
        }
    }

    public static Map<Identifier, byte[]> generateVirtualAssetFiles(String packNamespace, Path rootPath) {
        Map<Identifier, byte[]> virtualFiles = new HashMap<>();

        forEachJson(rootPath.resolve("villagers/structure_tags"), "structure tag", json -> {
            if (json.has("tag") && json.has("map_decoration")) {
                generateMapItemTexture(json, packNamespace, rootPath, virtualFiles);
            }
        });

        return virtualFiles;
    }

    private static void generateMapItemTexture(JsonObject json, String packNamespace, Path rootPath, Map<Identifier, byte[]> virtualFiles) {
        try {
            String rawTag = json.get("tag").getAsString();
            String rawDecoration = json.get("map_decoration").getAsString();
            String mapColor = json.has("map_color") ? json.get("map_color").getAsString() : null;
            String defaultNamespace = json.has("namespace") ? json.get("namespace").getAsString() : packNamespace;

            String itemNamespace = defaultNamespace;
            String tag = rawTag;
            if (rawTag.contains(":")) {
                String[] split = rawTag.split(":", 2);
                itemNamespace = split[0];
                tag = split[1];
            }
            String itemName = tag;
            if (itemName.startsWith("on_")) itemName = itemName.substring(3);
            if (itemName.endsWith("_explorer_maps")) itemName = itemName.substring(0, itemName.length() - 14);
            else if (itemName.endsWith("_maps")) itemName = itemName.substring(0, itemName.length() - 5);
            itemName = itemName + "_map";

            String decorationNamespace = defaultNamespace;
            String decorationName = rawDecoration;
            if (rawDecoration.contains(":")) {
                String[] split = rawDecoration.split(":", 2);
                decorationNamespace = split[0];
                decorationName = split[1];
            }

            byte[] decorationPng = readTexture(rootPath, decorationNamespace, "textures/map/decorations/" + decorationName + ".png");

            byte[] composite;
            if (decorationPng != null) {
                composite = VillagerPackTextureGenerator.generateMapItemTexture(decorationPng, mapColor);
            } else {
                LOGGER.warn("Could not find decoration texture '{}:{}' for map '{}:{}' - using a tinted blank map icon instead",
                        decorationNamespace, decorationName, itemNamespace, itemName);
                composite = VillagerPackTextureGenerator.getTintedMapBasePng(mapColor);
            }
            if (composite == null) return;

            virtualFiles.put(Identifier.fromNamespaceAndPath(itemNamespace, "textures/item/" + itemName + ".png"), composite);

            String modelJson = "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + itemNamespace + ":item/" + itemName + "\"}}";
            virtualFiles.put(Identifier.fromNamespaceAndPath(itemNamespace, "models/item/" + itemName + ".json"),
                    modelJson.getBytes(StandardCharsets.UTF_8));

            String itemDefJson = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + itemNamespace + ":item/" + itemName + "\"}}";
            virtualFiles.put(Identifier.fromNamespaceAndPath(itemNamespace, "items/" + itemName + ".json"),
                    itemDefJson.getBytes(StandardCharsets.UTF_8));

            LOGGER.info("Generated composite item texture for map '{}:{}' using decoration '{}:{}' (tint {})",
                    itemNamespace, itemName, decorationNamespace, decorationName, mapColor);
        } catch (Exception e) {
            LOGGER.error("Failed to generate map item texture from structure tag json", e);
        }
    }

    private static byte[] readTexture(Path rootPath, String namespace, String assetPath) {
        Path direct = rootPath.resolve("assets").resolve(namespace).resolve(assetPath);
        if (Files.exists(direct)) {
            try {
                return Files.readAllBytes(direct);
            } catch (IOException e) {
                LOGGER.error("Failed to read texture {}", direct, e);
            }
        }
        String classpathPath = "/assets/" + namespace + "/" + assetPath;
        try (InputStream in = VillagerPackCodecs.class.getResourceAsStream(classpathPath)) {
            if (in != null) return in.readAllBytes();
        } catch (IOException e) {
            LOGGER.error("Failed to read texture {} from classpath", classpathPath, e);
        }
        return null;
    }

    public record ProfessionData(String poiType, Identifier workSound, String namespace) {}
    public record PoiTypeData(Identifier block, int tickets, String namespace) {}
    public record VillagerTypeData(String name, String namespace) {}
    public record StructureTagData(String name, String decoration, String map_color, String namespace) {}
    public record WorkstationData(String name, String namespace) {}
    public record GiftData(String profession, Identifier lootTable) {}
}