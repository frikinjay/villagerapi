package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.registry.VAPIMapDecorations;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackCodecs {

    private static HolderLookup.Provider registryLookup = null;

    public static void setRegistryLookup(HolderLookup.Provider lookup) {
        registryLookup = lookup;
    }

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

    public static TradesData parseTrades(JsonObject json) {
        String profession = json.get("profession").getAsString();
        JsonObject levelsObj = json.getAsJsonObject("levels");

        Int2ObjectMap<VillagerTrades.ItemListing[]> trades = new Int2ObjectOpenHashMap<>();

        for (String levelStr : levelsObj.keySet()) {
            int level = Integer.parseInt(levelStr);
            JsonArray tradesArray = levelsObj.getAsJsonArray(levelStr);

            List<VillagerTrades.ItemListing> listings = new ArrayList<>();
            for (JsonElement tradeElement : tradesArray) {
                JsonObject tradeObj = tradeElement.getAsJsonObject();
                VillagerTrades.ItemListing listing = parseTrade(tradeObj);
                if (listing != null) {
                    listings.add(listing);
                }
            }

            trades.put(level, listings.toArray(new VillagerTrades.ItemListing[0]));
        }

        return new TradesData(profession, trades);
    }

    public static BiomeTradesData parseBiomeTrades(JsonObject json) {
        String profession = json.get("profession").getAsString();
        JsonObject biomeOverrides = json.getAsJsonObject("biome_overrides");

        Map<VillagerType, Int2ObjectMap<VillagerTrades.ItemListing[]>> biomeTradeMap = new HashMap<>();
        Map<VillagerType, Map<Integer, Boolean>> replaceFlags = new HashMap<>();

        for (String biomeTypeKey : biomeOverrides.keySet()) {
            VillagerType villagerType = VillagerAPI.unwrapHolder(BuiltInRegistries.VILLAGER_TYPE.get(
                    Identifier.parse(biomeTypeKey)
            ));

            if (villagerType == null) {
                LOGGER.warn("Unknown villager type: {}", biomeTypeKey);
                continue;
            }

            JsonObject biomeData = biomeOverrides.getAsJsonObject(biomeTypeKey);
            JsonObject levelsObj = biomeData.getAsJsonObject("levels");

            Int2ObjectMap<VillagerTrades.ItemListing[]> trades = new Int2ObjectOpenHashMap<>();
            Map<Integer, Boolean> levelReplaceFlags = new HashMap<>();

            for (String levelStr : levelsObj.keySet()) {
                int level = Integer.parseInt(levelStr);
                JsonElement levelElement = levelsObj.get(levelStr);

                boolean replace = false;
                JsonArray tradesArray;

                if (levelElement.isJsonArray()) {
                    tradesArray = levelElement.getAsJsonArray();
                } else if (levelElement.isJsonObject()) {
                    JsonObject levelObj = levelElement.getAsJsonObject();
                    replace = levelObj.has("replace") && levelObj.get("replace").getAsBoolean();
                    tradesArray = levelObj.getAsJsonArray("trades");
                } else {
                    LOGGER.warn("Invalid level data format for level {} in biome {}", levelStr, biomeTypeKey);
                    continue;
                }

                List<VillagerTrades.ItemListing> listings = new ArrayList<>();
                for (JsonElement tradeElement : tradesArray) {
                    JsonObject tradeObj = tradeElement.getAsJsonObject();
                    VillagerTrades.ItemListing listing = parseTrade(tradeObj);
                    if (listing != null) {
                        listings.add(listing);
                    }
                }

                trades.put(level, listings.toArray(new VillagerTrades.ItemListing[0]));
                levelReplaceFlags.put(level, replace);
            }

            biomeTradeMap.put(villagerType, trades);
            replaceFlags.put(villagerType, levelReplaceFlags);
        }

        return new BiomeTradesData(profession, biomeTradeMap, replaceFlags);
    }

    private static VillagerTrades.ItemListing parseTrade(JsonObject json) {
        try {
            JsonElement sellElement = json.get("sell");
            if (sellElement != null && sellElement.isJsonObject()) {
                JsonObject sellObj = sellElement.getAsJsonObject();
                String itemType = sellObj.has("item_type") ? sellObj.get("item_type").getAsString() : "simple";

                if ("treasure_map".equals(itemType)) {
                    return parseTreasureMapTrade(json, sellObj);
                } else if ("enchanted_book".equals(itemType)) {
                    return parseEnchantBookWithTagTrade(json, sellObj);
                } else if ("suspicious_stew".equals(itemType)) {
                    return parseSuspiciousStewWithEffectsTrade(json, sellObj);
                }
            }

            VillagerTrades.ItemListing vanillaTrade = tryParseVanillaTrade(json);
            if (vanillaTrade != null) {
                return vanillaTrade;
            }

            return parseCustomTrade(json);
        } catch (Exception e) {
            LOGGER.error("Failed to parse trade: {}", json, e);
            return null;
        }
    }

    private static VillagerTrades.ItemListing tryParseVanillaTrade(JsonObject json) {
        if (hasComponents(json.get("buy_a")) ||
                (json.has("buy_b") && hasComponents(json.get("buy_b"))) ||
                hasComponents(json.get("sell"))) {
            return null;
        }

        if (isSimpleEmeraldToItem(json)) return parseItemsForEmeralds(json);
        if (isSimpleItemToEmerald(json)) return parseEmeraldForItems(json);
        if (isItemAndEmeraldToItem(json)) return parseItemsAndEmeraldsToItems(json);
        if (isEnchantedItemTrade(json)) return parseEnchantedItemForEmeralds(json);
        if (isDyedArmorTrade(json)) return parseDyedArmorForEmeralds(json);

        return null;
    }

    private static boolean isSimpleEmeraldToItem(JsonObject json) {
        if (!json.has("buy_a") || !json.has("sell") || json.has("buy_b")) return false;
        if (hasComponents(json.get("buy_a")) || hasComponents(json.get("sell"))) return false;
        return "minecraft:emerald".equals(getItemId(json.get("buy_a")));
    }

    private static boolean isSimpleItemToEmerald(JsonObject json) {
        if (!json.has("buy_a") || !json.has("sell") || json.has("buy_b")) return false;
        if (hasComponents(json.get("buy_a")) || hasComponents(json.get("sell"))) return false;
        return "minecraft:emerald".equals(getItemId(json.get("sell")));
    }

    private static boolean isItemAndEmeraldToItem(JsonObject json) {
        if (!json.has("buy_a") || !json.has("buy_b") || !json.has("sell")) return false;
        return "minecraft:emerald".equals(getItemId(json.get("buy_b"))) &&
                !hasComponents(json.get("buy_a")) && !hasComponents(json.get("sell"));
    }

    private static boolean isEnchantedItemTrade(JsonObject json) {
        if (hasComponents(json.get("sell")) || !isSimpleEmeraldToItem(json)) return false;
        String sell = getItemId(json.get("sell"));
        return sell.contains("_sword") || sell.contains("_axe") || sell.contains("_pickaxe") ||
                sell.contains("_shovel") || sell.contains("_hoe") || sell.contains("_helmet") ||
                sell.contains("_chestplate") || sell.contains("_leggings") || sell.contains("_boots") ||
                sell.contains("_bow") || sell.contains("trident");
    }

    private static boolean isDyedArmorTrade(JsonObject json) {
        if (!isSimpleEmeraldToItem(json)) return false;
        String sell = getItemId(json.get("sell"));
        return sell.contains("leather_") &&
                (sell.endsWith("_helmet") || sell.endsWith("_chestplate") ||
                        sell.endsWith("_leggings") || sell.endsWith("_boots"));
    }

    private static boolean hasComponents(JsonElement element) {
        return element != null && element.isJsonObject() && element.getAsJsonObject().has("components");
    }

    private static VillagerTrades.ItemListing parseItemsForEmeralds(JsonObject json) {
        int emeraldCost = getCount(json.get("buy_a"));
        String itemId = getItemId(json.get("sell"));
        int itemCount = getCount(json.get("sell"));
        int maxUses = json.has("max_uses") ? json.get("max_uses").getAsInt() : 12;
        int xp = json.has("xp") ? json.get("xp").getAsInt() : 1;

        Item item = getItem(itemId);
        if (item == null) return null;

        return new VillagerTrades.ItemsForEmeralds(item, emeraldCost, itemCount, maxUses, xp);
    }

    private static VillagerTrades.ItemListing parseEmeraldForItems(JsonObject json) {
        String itemId = getItemId(json.get("buy_a"));
        int itemCount = getCount(json.get("buy_a"));
        int emeraldAmount = getCount(json.get("sell"));
        int maxUses = json.has("max_uses") ? json.get("max_uses").getAsInt() : 16;
        int xp = json.has("xp") ? json.get("xp").getAsInt() : 2;

        Item item = getItem(itemId);
        if (item == null) return null;

        return new VillagerTrades.EmeraldForItems(item, itemCount, maxUses, xp, emeraldAmount);
    }

    private static VillagerTrades.ItemListing parseItemsAndEmeraldsToItems(JsonObject json) {
        String buyAId = getItemId(json.get("buy_a"));
        int buyACount = getCount(json.get("buy_a"));
        int emeraldCost = getCount(json.get("buy_b"));
        String sellId = getItemId(json.get("sell"));
        int sellCount = getCount(json.get("sell"));
        int maxUses = json.has("max_uses") ? json.get("max_uses").getAsInt() : 12;
        int xp = json.has("xp") ? json.get("xp").getAsInt() : 10;
        float priceMultiplier = json.has("price_multiplier") ?
                json.get("price_multiplier").getAsFloat() : 0.05F;

        Item buyItem = getItem(buyAId);
        Item sellItem = getItem(sellId);
        if (buyItem == null || sellItem == null) return null;

        return new VillagerTrades.ItemsAndEmeraldsToItems(
                buyItem, buyACount, emeraldCost, sellItem, sellCount, maxUses, xp, priceMultiplier
        );
    }

    private static VillagerTrades.ItemListing parseEnchantedItemForEmeralds(JsonObject json) {
        String itemId = getItemId(json.get("sell"));
        int emeraldCost = getCount(json.get("buy_a"));
        int maxUses = json.has("max_uses") ? json.get("max_uses").getAsInt() : 3;
        int xp = json.has("xp") ? json.get("xp").getAsInt() : 15;

        Item item = getItem(itemId);
        if (item == null) return null;

        return new VillagerTrades.EnchantedItemForEmeralds(item, emeraldCost, maxUses, xp);
    }

    private static VillagerTrades.ItemListing parseDyedArmorForEmeralds(JsonObject json) {
        String itemId = getItemId(json.get("sell"));
        int emeraldCost = getCount(json.get("buy_a"));
        int maxUses = json.has("max_uses") ? json.get("max_uses").getAsInt() : 12;
        int xp = json.has("xp") ? json.get("xp").getAsInt() : 1;

        Item item = getItem(itemId);
        if (item == null) return null;

        return new VillagerTrades.DyedArmorForEmeralds(item, emeraldCost, maxUses, xp);
    }

    private static VillagerTrades.ItemListing parseEnchantBookWithTagTrade(JsonObject json, JsonObject sellObj) {
        int xp = json.has("xp") ? json.get("xp").getAsInt() : 1;

        if (!sellObj.has("enchantment_tag")) {
            LOGGER.warn("EnchantBookForEmeralds requires 'enchantment_tag' in sell object");
            return null;
        }

        String tagId = sellObj.get("enchantment_tag").getAsString();
        TagKey<@NotNull Enchantment> tag = TagKey.create(Registries.ENCHANTMENT, Identifier.parse(tagId));

        int minLevel = sellObj.has("min_level") ? sellObj.get("min_level").getAsInt() : 0;
        int maxLevel = sellObj.has("max_level") ? sellObj.get("max_level").getAsInt() : Integer.MAX_VALUE;

        if (minLevel > 0 || maxLevel < Integer.MAX_VALUE) {
            return new VillagerTrades.EnchantBookForEmeralds(xp, minLevel, maxLevel, tag);
        }

        return new VillagerTrades.EnchantBookForEmeralds(xp, tag);
    }

    private static VillagerTrades.ItemListing parseSuspiciousStewWithEffectsTrade(JsonObject json, JsonObject sellObj) {
        int xp = json.has("xp") ? json.get("xp").getAsInt() : 1;

        if (!sellObj.has("effect")) {
            return new VillagerTrades.SuspiciousStewForEmerald(MobEffects.NIGHT_VISION, 100, xp);
        }

        String effectId = sellObj.get("effect").getAsString();
        int duration = sellObj.has("duration") ? sellObj.get("duration").getAsInt() : 100;

        Holder<@NotNull MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effectId))
                .orElse((Holder.Reference<@NotNull MobEffect>) MobEffects.ABSORPTION);

        return new VillagerTrades.SuspiciousStewForEmerald(effect, duration, xp);
    }

    private static VillagerTrades.ItemListing parseTreasureMapTrade(JsonObject json, JsonObject sellObj) {
        int emeraldCost = getCount(json.get("buy_a"));
        String structureTag = sellObj.get("structure_tag").getAsString();
        String displayName = sellObj.get("display_name").getAsString();
        String mapDecorationInput = sellObj.get("map_decoration").getAsString();
        int maxUses = json.has("max_uses") ? json.get("max_uses").getAsInt() : 12;
        int xp = json.has("xp") ? json.get("xp").getAsInt() : 5;

        TagKey<@NotNull Structure> tag = TagKey.create(Registries.STRUCTURE, Identifier.parse(structureTag));

        Holder<@NotNull MapDecorationType> decoration = resolveMapDecoration(mapDecorationInput);
        if (decoration == null) {
            LOGGER.error("Map decoration not found: {} - skipping trade", mapDecorationInput);
            return null;
        }

        return new VillagerTrades.TreasureMapForEmeralds(
                emeraldCost, tag, displayName, decoration, maxUses, xp
        );
    }

    private static Holder<@NotNull MapDecorationType> resolveMapDecoration(String decorationInput) {
        Supplier<MapDecorationType> dynamicDecoration = VAPIMapDecorations.getDynamicMapDecorationType(decorationInput);

        if (dynamicDecoration != null) {
            MapDecorationType decorationType = dynamicDecoration.get();
            return BuiltInRegistries.MAP_DECORATION_TYPE.wrapAsHolder(decorationType);
        }

        Identifier decorationLoc = Identifier.parse(decorationInput);
        ResourceKey<@NotNull MapDecorationType> decorationKey =
                ResourceKey.create(Registries.MAP_DECORATION_TYPE, decorationLoc);

        return BuiltInRegistries.MAP_DECORATION_TYPE.get(decorationKey).orElse(null);
    }

    private static VillagerTrades.ItemListing parseCustomTrade(JsonObject json) {
        ItemStack buyA = parseItemStack(json.get("buy_a"));
        ItemStack buyB = json.has("buy_b") ? parseItemStack(json.get("buy_b")) : ItemStack.EMPTY;
        ItemStack sell = parseItemStack(json.get("sell"));

        if (buyA.isEmpty() || sell.isEmpty()) {
            LOGGER.error("Custom trade has empty stacks - invalid item IDs");
            return null;
        }

        int maxUses = json.has("max_uses") ? json.get("max_uses").getAsInt() : 12;
        int xp = json.has("xp") ? json.get("xp").getAsInt() : 1;
        float priceMultiplier = json.has("price_multiplier") ? json.get("price_multiplier").getAsFloat() : 0.05F;

        boolean buyAHasComponents = json.get("buy_a").isJsonObject() &&
                json.getAsJsonObject("buy_a").has("components");
        boolean buyBHasComponents = json.has("buy_b") &&
                json.get("buy_b").isJsonObject() &&
                json.getAsJsonObject("buy_b").has("components");

        Identifier buyAId = BuiltInRegistries.ITEM.getKey(buyA.getItem());
        Identifier buyBId = buyB.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(buyB.getItem());
        Identifier sellId = BuiltInRegistries.ITEM.getKey(sell.getItem());

        int buyACount = buyA.getCount();
        int buyBCount = buyB.getCount();
        int sellCount = sell.getCount();

        CompoundTag buyAComponents = buyAHasComponents ? serializeComponents(buyA) : null;
        CompoundTag buyBComponents = buyBHasComponents ? serializeComponents(buyB) : null;
        CompoundTag sellComponents = serializeComponents(sell);

        final HolderLookup.Provider capturedRegistry = registryLookup;

        return new VillagerTrades.ItemListing() {
            @Override
            public MerchantOffer getOffer(@NotNull ServerLevel level, @NotNull Entity entity, @NotNull RandomSource random) {
                ItemStack freshBuyA = recreateStack(buyAId, buyACount, buyAComponents, capturedRegistry);
                ItemStack freshBuyB = buyBId != null ?
                        recreateStack(buyBId, buyBCount, buyBComponents, capturedRegistry) : ItemStack.EMPTY;
                ItemStack freshSell = recreateStack(sellId, sellCount, sellComponents, capturedRegistry);

                if (freshBuyA.isEmpty() || freshSell.isEmpty()) return null;

                ItemCost costA = new ItemCost(
                        freshBuyA.getItemHolder(),
                        freshBuyA.getCount(),
                        buyAHasComponents ? DataComponentExactPredicate.allOf(freshBuyA.getComponents()) :
                                DataComponentExactPredicate.EMPTY
                );

                Optional<ItemCost> costB = freshBuyB.isEmpty() ? Optional.empty() :
                        Optional.of(new ItemCost(
                                freshBuyB.getItemHolder(),
                                freshBuyB.getCount(),
                                buyBHasComponents ? DataComponentExactPredicate.allOf(freshBuyB.getComponents()) :
                                        DataComponentExactPredicate.EMPTY
                        ));

                return new MerchantOffer(costA, costB, freshSell, maxUses, xp, priceMultiplier);
            }
        };
    }

    private static String getItemId(JsonElement element) {
        if (element.isJsonPrimitive()) return element.getAsString();
        return element.getAsJsonObject().get("item").getAsString();
    }

    private static int getCount(JsonElement element) {
        if (element.isJsonPrimitive()) return 1;
        JsonObject obj = element.getAsJsonObject();
        return obj.has("count") ? obj.get("count").getAsInt() : 1;
    }

    private static Item getItem(String itemId) {
        Item item = VillagerAPI.unwrapHolder(BuiltInRegistries.ITEM.get(Identifier.parse(itemId)));
        if (item == null || item == Items.AIR) {
            LOGGER.error("Item {} not found during trade registration", itemId);
            return null;
        }
        return item;
    }

    private static ItemStack parseItemStack(JsonElement element) {
        String itemId = getItemId(element);
        int count = getCount(element);
        JsonObject components = null;

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            components = obj.has("components") ? obj.getAsJsonObject("components") : null;
        }

        Item item = getItem(itemId);
        if (item == null) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(item, count);
        if (components != null) {
            applyComponentData(stack, components);
        }

        return stack;
    }

    private static CompoundTag serializeComponents(ItemStack stack) {
        if (registryLookup == null) return new CompoundTag();

        var registryOps = registryLookup.createSerializationContext(NbtOps.INSTANCE);
        return (CompoundTag) DataComponentPatch.CODEC
                .encodeStart(registryOps, stack.getComponentsPatch())
                .resultOrPartial(LOGGER::error)
                .orElse(new CompoundTag());
    }

    private static ItemStack recreateStack(Identifier itemId, int count, CompoundTag components, HolderLookup.Provider registry) {
        Item item = VillagerAPI.unwrapHolder(BuiltInRegistries.ITEM.get(itemId));
        if (item == null || item == Items.AIR) {
            LOGGER.warn("Item {} not found during trade recreation", itemId);
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item, count);

        if (components != null && !components.isEmpty() && registry != null) {
            var registryOps = registry.createSerializationContext(NbtOps.INSTANCE);
            DataComponentPatch patch = DataComponentPatch.CODEC
                    .parse(registryOps, components)
                    .resultOrPartial(LOGGER::error)
                    .orElse(DataComponentPatch.EMPTY);
            stack.applyComponents(patch);
        }

        return stack;
    }

    private static void applyComponentData(ItemStack stack, JsonObject componentsObj) {
        if (registryLookup == null) {
            LOGGER.error("Registry lookup not available for component parsing");
            return;
        }

        try {
            var registryOps = registryLookup.createSerializationContext(JsonOps.INSTANCE);
            DataComponentPatch patch = DataComponentPatch.CODEC
                    .parse(registryOps, componentsObj)
                    .resultOrPartial(error -> LOGGER.error("Failed to parse components: {}", error))
                    .orElse(DataComponentPatch.EMPTY);

            if (!patch.isEmpty()) {
                stack.applyComponents(patch);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to apply component data", e);
        }
    }

    public record ProfessionData(String poiType, Identifier workSound, String namespace) {}
    public record PoiTypeData(Identifier block, int tickets, String namespace) {}
    public record TradesData(String profession, Int2ObjectMap<VillagerTrades.ItemListing[]> trades) {}
    public record VillagerTypeData(String name, String namespace) {}
    public record StructureTagData(String name, String decoration, String map_color, String namespace) {}
    public record WorkstationData(String name, String namespace) {}
    public record GiftData(String profession, Identifier lootTable) {}
    public record BiomeTradesData(
            String profession,
            Map<VillagerType, Int2ObjectMap<VillagerTrades.ItemListing[]>> biomeTradeMap,
            Map<VillagerType, Map<Integer, Boolean>> replaceFlags
    ) {}
}