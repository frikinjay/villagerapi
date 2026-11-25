package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public final class VillagerPackTrades {

    /**
     * Trades without a reputation_discount still react to demand and reputation, like vanilla's own trades
     * and like Villager API on 1.21.x.
     */
    private static final float DEFAULT_REPUTATION_DISCOUNT = 0.05F;

    private static final String VILLAGER_VARIANT_PREDICATE = "minecraft:villager/variant";

    private static Map<String, Map<Identifier, byte[]>> filesByPack;

    private VillagerPackTrades() {}

    public static synchronized Map<Identifier, byte[]> getFilesFor(String packKey) {
        if (filesByPack == null) rebuild();
        return new HashMap<>(filesByPack.getOrDefault(packKey, Map.of()));
    }

    public static synchronized void rebuild() {
        Map<String, Map<Identifier, byte[]>> result = new HashMap<>();
        Map<Identifier, VillagerPackTradeSelector.SelectionPlan> plans = new HashMap<>();
        VillagerPackLoader loader = new VillagerPackLoader(CommonPlatformHelper.getGameDirectory());
        List<VillagerPackLoader.VillagerPack> packs = loader.getDiscoveredPacks();
        List<String> builtProfessions = new ArrayList<>();

        try {
            List<PackTrades> allPackTrades = new ArrayList<>();
            Set<String> packProfessionIds = new HashSet<>();

            for (VillagerPackLoader.VillagerPack pack : packs) {
                collectPackProfessionIds(pack, packProfessionIds);
                if (!canProvideServerData(pack)) continue;

                result.put(pack.getName(), new HashMap<>());
                allPackTrades.add(parsePack(pack));
            }

            Map<String, List<PackTrades>> byProfession = new LinkedHashMap<>();
            for (PackTrades packTrades : allPackTrades) {
                for (String professionId : packTrades.professions.keySet()) {
                    byProfession.computeIfAbsent(professionId, k -> new ArrayList<>()).add(packTrades);
                }
            }

            try (BaseTradeSetReader baseReader = new BaseTradeSetReader(packs)) {
                for (Map.Entry<String, List<PackTrades>> entry : byProfession.entrySet()) {
                    try {
                        resolveProfession(entry.getKey(), entry.getValue(), packProfessionIds, baseReader, result, plans);
                        builtProfessions.add(entry.getKey());
                    } catch (Exception e) {
                        LOGGER.error("Failed to build trades for profession '{}'", entry.getKey(), e);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to build villagerpack trades", e);
        } finally {
            for (VillagerPackLoader.VillagerPack pack : packs) {
                pack.close();
            }
        }

        filesByPack = result;
        VillagerPackTradeSelector.setPlans(plans);
        LOGGER.info("Built villagerpack trades for {} profession(s): {}", builtProfessions.size(), builtProfessions);
    }

    private static boolean canProvideServerData(VillagerPackLoader.VillagerPack pack) {
        return pack.hasConfig() && pack.getPackMeta() != null && pack.hasServerContent();
    }

    private static void collectPackProfessionIds(VillagerPackLoader.VillagerPack pack, Set<String> ids) {
        Path dir = pack.getRootPath().resolve("villagers/professions");
        if (!Files.exists(dir)) return;

        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(file -> {
                String relative = dir.relativize(file).toString().replace("\\", "/");
                String name = relative.substring(0, relative.length() - 5).replace("/", "_");
                String namespace = pack.getNamespace();

                try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element.isJsonObject() && element.getAsJsonObject().has("namespace")) {
                        namespace = element.getAsJsonObject().get("namespace").getAsString();
                    }
                } catch (Exception ignored) {
                }

                ids.add(namespace + ":" + name);
            });
        } catch (IOException e) {
            LOGGER.error("Failed to read professions of villagerpack '{}'", pack.getName(), e);
        }
    }

    private static PackTrades parsePack(VillagerPackLoader.VillagerPack pack) {
        PackTrades packTrades = new PackTrades(pack.getName(), pack.getNamespace());
        Path root = pack.getRootPath();
        Map<String, String> tagNamespaceMap = VillagerPackCodecs.loadTagNamespaceMap(pack.getNamespace(), root);

        VillagerPackCodecs.forEachJson(root.resolve("villagers/trades"), "trade", json ->
                parseBaseTrades(packTrades, json, tagNamespaceMap));

        VillagerPackCodecs.forEachJson(root.resolve("villagers/biome_trades"), "biome trade", json ->
                parseBiomeTrades(packTrades, json, tagNamespaceMap));

        return packTrades;
    }

    private static void parseBaseTrades(PackTrades packTrades, JsonObject json, Map<String, String> tagNamespaceMap) {
        if (!json.has("profession") || !json.has("levels") || !json.get("levels").isJsonObject()) return;

        String professionId = VillagerPackCodecs.resolveProfessionId(json.get("profession").getAsString(), packTrades.namespace);
        ProfessionTrades profession = packTrades.profession(professionId);
        JsonObject levels = json.getAsJsonObject("levels");

        for (String levelKey : levels.keySet()) {
            Integer level = parseLevel(levelKey, professionId, packTrades.packKey);
            if (level == null) continue;

            String context = "level " + level + " of '" + professionId + "' in villagerpack '" + packTrades.packKey + "'";
            JsonElement levelElement = levels.get(levelKey);
            JsonArray trades = null;
            boolean replace = false;
            Integer tradeListAmount = null;
            Integer maxSize = null;
            boolean maxReplace = false;

            if (levelElement.isJsonArray()) {
                trades = levelElement.getAsJsonArray();
            } else if (levelElement.isJsonObject()) {
                JsonObject levelObject = levelElement.getAsJsonObject();
                if (levelObject.has("trades") && levelObject.get("trades").isJsonArray()) {
                    trades = levelObject.getAsJsonArray("trades");
                }
                replace = readBoolean(levelObject, "replace");
                tradeListAmount = readInt(levelObject, "trade_list_amount", context);

                if (levelObject.has("trade_list_max")) {
                    JsonElement maxElement = levelObject.get("trade_list_max");
                    if (maxElement.isJsonObject()) {
                        JsonObject maxObject = maxElement.getAsJsonObject();
                        Integer size = readInt(maxObject, "size", context + " trade_list_max");
                        if (size == null || size <= 0) {
                            LOGGER.warn("trade_list_max for {} needs a positive 'size', ignoring it", context);
                        } else {
                            maxSize = size;
                            maxReplace = readBoolean(maxObject, "replace");
                        }
                    } else {
                        LOGGER.warn("trade_list_max for {} must be an object with 'size' and 'replace', ignoring it", context);
                    }
                }

                if (trades == null && !replace && tradeListAmount == null && maxSize == null) {
                    LOGGER.warn("{} has no trades, skipping", context);
                    continue;
                }
            } else {
                continue;
            }

            LevelTrades levelTrades = profession.level(level);
            if (replace) levelTrades.replace = true;
            if (tradeListAmount != null && levelTrades.tradeListAmount == null) levelTrades.tradeListAmount = tradeListAmount;
            if (maxSize != null && levelTrades.maxSize == null) {
                levelTrades.maxSize = maxSize;
                levelTrades.maxReplace = maxReplace;
            }

            if (trades != null) {
                addTrades(packTrades, levelTrades, trades, null, tagNamespaceMap, context);
            }
        }
    }

    private static void parseBiomeTrades(PackTrades packTrades, JsonObject json, Map<String, String> tagNamespaceMap) {
        if (!json.has("profession") || !json.has("biome_overrides") || !json.get("biome_overrides").isJsonObject()) return;

        String professionId = VillagerPackCodecs.resolveProfessionId(json.get("profession").getAsString(), packTrades.namespace);
        JsonObject biomeOverrides = json.getAsJsonObject("biome_overrides");

        for (String biomeTypeKey : biomeOverrides.keySet()) {
            JsonElement biomeElement = biomeOverrides.get(biomeTypeKey);
            if (!biomeElement.isJsonObject()) continue;

            JsonElement levelsElement = biomeElement.getAsJsonObject().get("levels");
            if (levelsElement == null || !levelsElement.isJsonObject()) continue;

            List<String> variants = parseVariants(biomeTypeKey, packTrades.namespace);
            if (variants.isEmpty()) {
                LOGGER.warn("Biome trade key '{}' for profession '{}' in villagerpack '{}' has no villager types, skipping",
                        biomeTypeKey, professionId, packTrades.packKey);
                continue;
            }

            ProfessionTrades profession = packTrades.profession(professionId);
            JsonObject levels = levelsElement.getAsJsonObject();

            for (String levelKey : levels.keySet()) {
                Integer level = parseLevel(levelKey, professionId, packTrades.packKey);
                if (level == null) continue;

                String context = "biome trades '" + biomeTypeKey + "' level " + level + " of '" + professionId + "' in villagerpack '" + packTrades.packKey + "'";
                JsonElement levelElement = levels.get(levelKey);
                JsonArray trades = null;
                boolean replace = false;
                Integer tradeListAmount = null;

                if (levelElement.isJsonArray()) {
                    trades = levelElement.getAsJsonArray();
                } else if (levelElement.isJsonObject()) {
                    JsonObject levelObject = levelElement.getAsJsonObject();
                    if (levelObject.has("trades") && levelObject.get("trades").isJsonArray()) {
                        trades = levelObject.getAsJsonArray("trades");
                    }
                    replace = readBoolean(levelObject, "replace");
                    tradeListAmount = readInt(levelObject, "trade_list_amount", context);
                }

                if (trades == null && !replace && tradeListAmount == null) continue;

                LevelTrades levelTrades = profession.level(level);
                if (replace) levelTrades.replacedVariants.addAll(variants);
                if (tradeListAmount != null && levelTrades.tradeListAmount == null) levelTrades.tradeListAmount = tradeListAmount;

                if (trades != null) {
                    addTrades(packTrades, levelTrades, trades, variants, tagNamespaceMap, context);
                }
            }
        }
    }

    private static void addTrades(PackTrades packTrades, LevelTrades levelTrades, JsonArray trades, List<String> variants, Map<String, String> tagNamespaceMap, String context) {
        for (JsonElement tradeElement : trades) {
            if (!tradeElement.isJsonObject()) continue;

            JsonObject source = tradeElement.getAsJsonObject();
            Integer weight = readInt(source, "trade_list_weight", "a trade in " + context);
            JsonObject prepared = prepareTrade(packTrades.namespace, source, tagNamespaceMap);
            levelTrades.trades.add(new PendingTrade(prepared, variants, weight == null ? 1 : weight));
        }
    }

    private static void resolveProfession(String professionId, List<PackTrades> chain, Set<String> packProfessionIds, BaseTradeSetReader baseReader, Map<String, Map<Identifier, byte[]>> result, Map<Identifier, VillagerPackTradeSelector.SelectionPlan> plans) {
        String[] professionParts = professionId.split(":", 2);
        String profNamespace = professionParts[0];
        String profName = professionParts[1];
        PackTrades emitter = chain.get(0);
        Map<Identifier, byte[]> emitterFiles = result.get(emitter.packKey);
        boolean isPackProfession = packProfessionIds.contains(professionId);

        Map<Integer, Identifier> registeredTradeSets = resolveRegisteredTradeSets(professionId);
        if (registeredTradeSets == null) {
            LOGGER.warn("Profession '{}' is not registered, trade sets for it will use the default key convention", professionId);
        }

        Set<Integer> levels = new TreeSet<>();
        for (PackTrades packTrades : chain) {
            levels.addAll(packTrades.professions.get(professionId).levels.keySet());
        }
        if (isPackProfession && registeredTradeSets != null) {
            levels.addAll(registeredTradeSets.keySet());
        }

        Map<String, Integer> summary = new LinkedHashMap<>();

        for (int level : levels) {
            List<PackLevel> included = new ArrayList<>();
            boolean replaced = false;

            for (PackTrades packTrades : chain) {
                LevelTrades levelTrades = packTrades.professions.get(professionId).levels.get(level);
                if (levelTrades == null) continue;
                included.add(new PackLevel(packTrades, levelTrades));
                if (levelTrades.replace) {
                    replaced = true;
                    break;
                }
            }

            Identifier tradeSetKey;
            if (registeredTradeSets != null && registeredTradeSets.containsKey(level)) {
                tradeSetKey = registeredTradeSets.get(level);
            } else {
                if (registeredTradeSets != null) {
                    LOGGER.warn("Profession '{}' has no trade set for level {}, trades defined for that level will not be offered", professionId, level);
                }
                tradeSetKey = Identifier.fromNamespaceAndPath(profNamespace, profName + "/level_" + level);
            }

            JsonObject base = null;
            if (!replaced) {
                base = baseReader.read(tradeSetKey);
                if (base == null && !isPackProfession) {
                    LOGGER.error("Could not find the existing trade set '{}' for profession '{}' level {}, so villagerpack trades cannot be merged into it and this level was left unchanged. Use \"replace\": true on this level to replace it instead",
                            tradeSetKey, professionId, level);
                    continue;
                }
            }

            List<JsonElement> baseEntries = new ArrayList<>();
            JsonElement baseAmount = null;
            if (base != null) {
                if (!collectBaseEntries(base, baseEntries)) {
                    LOGGER.error("The existing trade set '{}' for profession '{}' level {} uses a format that cannot be merged into, this level was left unchanged. Use \"replace\": true on this level to replace it instead",
                            tradeSetKey, professionId, level);
                    continue;
                }
                baseAmount = base.get("amount");
            }

            Set<String> excludedVariants = new LinkedHashSet<>();
            for (PackLevel packLevel : included) {
                excludedVariants.addAll(packLevel.levelTrades().replacedVariants);
            }
            if (!excludedVariants.isEmpty() && base != null) {
                LOGGER.warn("Biome trade replace for {} at level {} of '{}' cannot remove the original trades that are merged into this level, those will still be offered to these villager types",
                        excludedVariants, level, professionId);
            }

            String levelPath = "villagerapi/" + profNamespace + "/" + profName + "/level_" + level;
            List<VillagerPackTradeSelector.GroupPlan> groupPlans = new ArrayList<>();
            List<String> fallbackTradeIds = new ArrayList<>();
            int fallbackAmount = 0;

            if (base != null) {
                Identifier originalKey = Identifier.fromNamespaceAndPath(emitter.namespace, levelPath + "/original");
                emitterFiles.put(tradeSetFile(originalKey), base.deepCopy().toString().getBytes(StandardCharsets.UTF_8));
                groupPlans.add(new VillagerPackTradeSelector.GroupPlan(originalKey, Map.of()));
            }

            List<PackLevel> groupOrder = new ArrayList<>(included);
            Collections.reverse(groupOrder);

            for (PackLevel packLevel : groupOrder) {
                PackTrades packTrades = packLevel.packTrades();
                LevelTrades levelTrades = packLevel.levelTrades();

                if (levelTrades.tradeListAmount != null && levelTrades.tradeListAmount <= 0) {
                    LOGGER.debug("Villagerpack '{}' offers no trades at level {} of '{}' (trade_list_amount {})",
                            packTrades.packKey, level, professionId, levelTrades.tradeListAmount);
                    continue;
                }

                Map<Identifier, byte[]> packFiles = result.get(packTrades.packKey);
                String tradeFolder = profNamespace.equals(packTrades.namespace) ? profName : profNamespace + "/" + profName;
                List<String> groupTradeIds = new ArrayList<>();
                Map<Identifier, Integer> weights = new HashMap<>();

                for (int i = 0; i < levelTrades.trades.size(); i++) {
                    PendingTrade pending = levelTrades.trades.get(i);
                    if (pending.weight() <= 0) continue;

                    JsonObject tradeJson = pending.json().deepCopy();
                    applyMerchantRestrictions(tradeJson, pending.variants(), excludedVariants);

                    String variantSuffix = pending.variants() != null
                            ? "_" + sanitizePathPart(String.join("_", pending.variants()))
                            : "";
                    String tradeName = profName + variantSuffix + "_trade_" + level + "_" + (i + 1);
                    String tradePath = tradeFolder + "/" + level + "/" + tradeName;
                    Identifier tradeId = Identifier.fromNamespaceAndPath(packTrades.namespace, tradePath);

                    packFiles.put(Identifier.fromNamespaceAndPath(packTrades.namespace, "villager_trade/" + tradePath + ".json"),
                            tradeJson.toString().getBytes(StandardCharsets.UTF_8));
                    groupTradeIds.add(tradeId.toString());
                    weights.put(tradeId, pending.weight());
                }

                if (groupTradeIds.isEmpty()) continue;

                int groupAmount = levelTrades.tradeListAmount == null
                        ? groupTradeIds.size()
                        : Math.min(levelTrades.tradeListAmount, groupTradeIds.size());

                String groupPath = levelPath + "/" + sanitizePathPart(packTrades.namespace);
                Identifier groupKey = Identifier.fromNamespaceAndPath(emitter.namespace, groupPath);

                emitterFiles.put(tagFile(groupKey), tagJson(groupTradeIds, List.of()));
                emitterFiles.put(tradeSetFile(groupKey), tradeSetJson(groupKey, new JsonPrimitive(groupAmount)));

                groupPlans.add(new VillagerPackTradeSelector.GroupPlan(groupKey, Map.copyOf(weights)));
                fallbackTradeIds.addAll(groupTradeIds);
                fallbackAmount += groupAmount;
            }

            Integer maxTrades = resolveMaxTrades(included);

            JsonElement masterAmount;
            if (base == null) {
                masterAmount = new JsonPrimitive(fallbackAmount);
            } else if (baseAmount != null && baseAmount.isJsonPrimitive() && baseAmount.getAsJsonPrimitive().isNumber()) {
                masterAmount = new JsonPrimitive((int) Math.round(baseAmount.getAsDouble()) + fallbackAmount);
            } else if (baseAmount != null) {
                masterAmount = baseAmount.deepCopy();
            } else {
                masterAmount = new JsonPrimitive(fallbackAmount);
            }
            if (maxTrades != null && masterAmount.isJsonPrimitive() && masterAmount.getAsJsonPrimitive().isNumber()) {
                masterAmount = new JsonPrimitive(Math.min(masterAmount.getAsInt(), maxTrades));
            }

            Identifier masterTagKey = Identifier.fromNamespaceAndPath(emitter.namespace, levelPath);
            emitterFiles.put(tagFile(masterTagKey), tagJson(fallbackTradeIds, baseEntries));

            JsonObject masterSet = new JsonObject();
            masterSet.addProperty("trades", "#" + masterTagKey);
            masterSet.add("amount", masterAmount);
            masterSet.addProperty("random_sequence", tradeSetKey.getNamespace() + ":trade_set/" + tradeSetKey.getPath());
            emitterFiles.put(tradeSetFile(tradeSetKey), masterSet.toString().getBytes(StandardCharsets.UTF_8));

            if (!included.isEmpty()) {
                plans.put(tradeSetKey, new VillagerPackTradeSelector.SelectionPlan(List.copyOf(groupPlans), maxTrades));

                String action = replaced ? "Replaced" : (base != null ? "Merged into" : "Defined");
                LOGGER.debug("{} level {} of '{}' with {} trade(s) from villagerpacks{}",
                        action, level, professionId, fallbackTradeIds.size(),
                        maxTrades != null ? " (max " + maxTrades + " offered)" : "");
                summary.merge(action, 1, Integer::sum);
            }
        }

        LOGGER.info("Villagerpack trades for '{}': {}", professionId, describeSummary(summary));
    }

    private static String describeSummary(Map<String, Integer> summary) {
        List<String> parts = new ArrayList<>();
        for (String action : List.of("Defined", "Merged into", "Replaced")) {
            Integer count = summary.get(action);
            if (count != null) {
                parts.add(action.toLowerCase(Locale.ROOT) + " " + count + " level" + (count == 1 ? "" : "s"));
            }
        }
        return parts.isEmpty() ? "no levels changed" : String.join(", ", parts);
    }

    private static Integer resolveMaxTrades(List<PackLevel> included) {
        Integer max = null;
        for (PackLevel packLevel : included) {
            LevelTrades levelTrades = packLevel.levelTrades();
            if (levelTrades.maxSize == null) continue;
            max = (max == null ? 0 : max) + levelTrades.maxSize;
            if (levelTrades.maxReplace) break;
        }
        return max;
    }

    private static Identifier tradeSetFile(Identifier key) {
        return Identifier.fromNamespaceAndPath(key.getNamespace(), "trade_set/" + key.getPath() + ".json");
    }

    private static Identifier tagFile(Identifier key) {
        return Identifier.fromNamespaceAndPath(key.getNamespace(), "tags/villager_trade/" + key.getPath() + ".json");
    }

    private static byte[] tagJson(List<String> tradeIds, List<JsonElement> extraEntries) {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", true);
        JsonArray values = new JsonArray();
        tradeIds.forEach(values::add);
        extraEntries.forEach(values::add);
        tag.add("values", values);
        return tag.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] tradeSetJson(Identifier key, JsonElement amount) {
        JsonObject tradeSet = new JsonObject();
        tradeSet.addProperty("trades", "#" + key);
        tradeSet.add("amount", amount);
        tradeSet.addProperty("random_sequence", key.getNamespace() + ":trade_set/" + key.getPath());
        return tradeSet.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static boolean collectBaseEntries(JsonObject base, List<JsonElement> entries) {
        JsonElement trades = base.get("trades");
        if (trades == null) return false;

        if (trades.isJsonPrimitive()) {
            entries.add(trades.deepCopy());
            return true;
        }

        if (trades.isJsonArray()) {
            for (JsonElement element : trades.getAsJsonArray()) {
                if (!element.isJsonPrimitive()) return false;
                entries.add(element.deepCopy());
            }
            return true;
        }

        return false;
    }

    private static boolean readBoolean(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }

    private static Integer readInt(JsonObject object, String key, String context) {
        if (!object.has(key)) return null;
        JsonElement element = object.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return (int) Math.round(element.getAsDouble());
        }
        LOGGER.warn("'{}' in {} must be a number, ignoring it", key, context);
        return null;
    }

    private static Integer parseLevel(String levelKey, String professionId, String packKey) {
        try {
            return Integer.parseInt(levelKey.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid trade level '{}' for profession '{}' in villagerpack '{}', skipping", levelKey, professionId, packKey);
            return null;
        }
    }

    private static List<String> parseVariants(String biomeTypeKey, String packNamespace) {
        Set<String> variants = new LinkedHashSet<>();
        for (String part : biomeTypeKey.split(",")) {
            String variant = part.trim();
            if (variant.isEmpty()) continue;
            variants.add(variant.contains(":") ? variant : packNamespace + ":" + variant);
        }
        return new ArrayList<>(variants);
    }

    static JsonObject prepareTrade(String packNamespace, JsonObject source, Map<String, String> tagNamespaceMap) {
        JsonObject tradeJson = source.deepCopy();
        tradeJson.remove("trade_list_weight");
        if (!tradeJson.has("reputation_discount")) {
            tradeJson.addProperty("reputation_discount", DEFAULT_REPUTATION_DISCOUNT);
        }
        VillagerPackCodecs.applyItemModifierTransforms(packNamespace, tradeJson, tagNamespaceMap);
        if (tradeJson.has("merchant_predicate")) {
            normalizeCondition(tradeJson.get("merchant_predicate"));
        }
        return tradeJson;
    }

    private static void normalizeCondition(JsonElement element) {
        if (element == null) return;

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                normalizeCondition(child);
            }
            return;
        }

        if (!element.isJsonObject()) return;
        JsonObject condition = element.getAsJsonObject();

        if (condition.has("condition") && !condition.has("type")) {
            condition.addProperty("type", condition.remove("condition").getAsString());
        }

        if (condition.has("predicate") && condition.get("predicate").isJsonObject()) {
            JsonObject predicate = condition.getAsJsonObject("predicate");
            if (predicate.has("predicates") && !predicate.has("minecraft:predicates")) {
                predicate.add("minecraft:predicates", predicate.remove("predicates"));
            }
        }

        if (condition.has("term")) {
            normalizeCondition(condition.get("term"));
        }

        if (condition.has("terms")) {
            normalizeCondition(condition.get("terms"));
        }
    }

    private static void applyMerchantRestrictions(JsonObject tradeJson, List<String> variants, Set<String> excludedVariants) {
        List<JsonElement> conditions = new ArrayList<>();

        if (tradeJson.has("merchant_predicate")) {
            conditions.add(tradeJson.remove("merchant_predicate"));
        }

        if (variants != null) {
            conditions.add(variantCondition(variants));
        } else if (!excludedVariants.isEmpty()) {
            JsonObject inverted = new JsonObject();
            inverted.addProperty("type", "minecraft:inverted");
            inverted.add("term", variantCondition(new ArrayList<>(excludedVariants)));
            conditions.add(inverted);
        }

        if (conditions.isEmpty()) return;

        if (conditions.size() == 1) {
            tradeJson.add("merchant_predicate", conditions.get(0));
            return;
        }

        JsonObject allOf = new JsonObject();
        allOf.addProperty("type", "minecraft:all_of");
        JsonArray terms = new JsonArray();
        conditions.forEach(terms::add);
        allOf.add("terms", terms);
        tradeJson.add("merchant_predicate", allOf);
    }

    private static JsonObject variantCondition(List<String> variants) {
        JsonObject predicates = new JsonObject();
        if (variants.size() == 1) {
            predicates.addProperty(VILLAGER_VARIANT_PREDICATE, variants.get(0));
        } else {
            JsonArray variantArray = new JsonArray();
            variants.forEach(variantArray::add);
            predicates.add(VILLAGER_VARIANT_PREDICATE, variantArray);
        }

        JsonObject predicate = new JsonObject();
        predicate.add("minecraft:predicates", predicates);

        JsonObject condition = new JsonObject();
        condition.addProperty("type", "minecraft:entity_properties");
        condition.addProperty("entity", "this");
        condition.add("predicate", predicate);
        return condition;
    }

    private static String sanitizePathPart(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_")
                .replaceAll("_+", "_");
    }

    private static Map<Integer, Identifier> resolveRegisteredTradeSets(String professionId) {
        try {
            Identifier id = Identifier.parse(professionId);
            VillagerProfession profession = VillagerAPI.unwrapHolder(BuiltInRegistries.VILLAGER_PROFESSION.get(id));
            if (profession == null) return null;

            Map<Integer, Identifier> result = new TreeMap<>();
            for (Int2ObjectMap.Entry<ResourceKey<TradeSet>> entry : profession.tradeSetsByLevel().int2ObjectEntrySet()) {
                result.put(entry.getIntKey(), entry.getValue().identifier());
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to resolve trade sets for profession '{}'", professionId, e);
            return null;
        }
    }

    private static final class BaseTradeSetReader implements AutoCloseable {
        private final List<VillagerPackLoader.VillagerPack> packs;
        private final List<Path> modRoots = new ArrayList<>();
        private final List<FileSystem> ownedFileSystems = new ArrayList<>();
        private final Map<Identifier, Optional<JsonObject>> cache = new HashMap<>();

        private BaseTradeSetReader(List<VillagerPackLoader.VillagerPack> packs) {
            this.packs = packs;

            Map<String, Path> roots = new TreeMap<>(CommonPlatformHelper.getAllModRootPaths());
            Path minecraftRoot = roots.remove("minecraft");
            for (Path root : roots.values()) {
                addRoot(root);
            }
            if (minecraftRoot != null) {
                addRoot(minecraftRoot);
            }
        }

        private void addRoot(Path root) {
            try {
                if (Files.isDirectory(root)) {
                    modRoots.add(root);
                    return;
                }

                String fileName = root.getFileName() != null ? root.getFileName().toString() : "";
                if (Files.isRegularFile(root) && (fileName.endsWith(".jar") || fileName.endsWith(".zip"))) {
                    URI uri = URI.create("jar:" + root.toUri());
                    FileSystem fs;
                    try {
                        fs = FileSystems.getFileSystem(uri);
                    } catch (FileSystemNotFoundException e) {
                        fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                        ownedFileSystems.add(fs);
                    }
                    modRoots.add(fs.getPath("/"));
                }
            } catch (Exception e) {
                LOGGER.debug("Could not open mod root {} for trade set lookup", root, e);
            }
        }

        private JsonObject read(Identifier tradeSetKey) {
            return cache.computeIfAbsent(tradeSetKey, key -> Optional.ofNullable(load(key))).orElse(null);
        }

        private JsonObject load(Identifier key) {
            String relative = "data/" + key.getNamespace() + "/trade_set/" + key.getPath() + ".json";

            for (VillagerPackLoader.VillagerPack pack : packs) {
                JsonObject json = readJson(pack.getRootPath().resolve(relative));
                if (json != null) return json;
            }

            for (Path root : modRoots) {
                JsonObject json = readJson(root.resolve(relative));
                if (json != null) return json;
            }

            for (ClassLoader loader : new ClassLoader[] {
                    VillagerPackTrades.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader(),
                    ClassLoader.getSystemClassLoader()
            }) {
                if (loader == null) continue;
                try (InputStream in = loader.getResourceAsStream(relative)) {
                    if (in == null) continue;
                    JsonElement element = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    if (element.isJsonObject()) return element.getAsJsonObject();
                } catch (Exception e) {
                    LOGGER.debug("Failed to read {} from classpath", relative, e);
                }
            }

            return null;
        }

        private static JsonObject readJson(Path path) {
            try {
                if (!Files.exists(path)) return null;
                try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    return element.isJsonObject() ? element.getAsJsonObject() : null;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to read trade set {}", path, e);
                return null;
            }
        }

        @Override
        public void close() {
            for (FileSystem fs : ownedFileSystems) {
                try {
                    fs.close();
                } catch (IOException e) {
                    LOGGER.debug("Failed to close filesystem", e);
                }
            }
        }
    }

    private static final class PackTrades {
        private final String packKey;
        private final String namespace;
        private final Map<String, ProfessionTrades> professions = new LinkedHashMap<>();

        private PackTrades(String packKey, String namespace) {
            this.packKey = packKey;
            this.namespace = namespace;
        }

        private ProfessionTrades profession(String professionId) {
            return professions.computeIfAbsent(professionId, k -> new ProfessionTrades());
        }
    }

    private static final class ProfessionTrades {
        private final TreeMap<Integer, LevelTrades> levels = new TreeMap<>();

        private LevelTrades level(int level) {
            return levels.computeIfAbsent(level, k -> new LevelTrades());
        }
    }

    private static final class LevelTrades {
        private boolean replace;
        private Integer tradeListAmount;
        private Integer maxSize;
        private boolean maxReplace;
        private final List<PendingTrade> trades = new ArrayList<>();
        private final Set<String> replacedVariants = new LinkedHashSet<>();
    }

    private record PendingTrade(JsonObject json, List<String> variants, int weight) {}

    private record PackLevel(PackTrades packTrades, LevelTrades levelTrades) {}
}