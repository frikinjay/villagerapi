package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

/**
 * Wandering trader trades from villagerpacks. Files in a pack's {@code villagers/wandering_trader/} folder can add
 * trades to the trader's pools ({@code buying}, {@code uncommon}, {@code common}), add pools of their own, change
 * how many trades a pool offers, remove every trade that buys or sells certain items, or replace the vanilla
 * trades altogether. Without any such file the wandering trader is left exactly as vanilla makes it.
 */
public final class VillagerPackWanderingTrades {

    private static final List<ResourceKey<TradeSet>> VANILLA_POOLS = List.of(
            TradeSets.WANDERING_TRADER_BUYING, TradeSets.WANDERING_TRADER_UNCOMMON, TradeSets.WANDERING_TRADER_COMMON);
    private static final String POOL_PREFIX = "wandering_trader/";
    private static final Set<String> KNOWN_POOLS = Set.of("buying", "uncommon", "common");

    private static volatile Plan plan = Plan.EMPTY;
    private static volatile HolderLookup.Provider builtFor;

    private VillagerPackWanderingTrades() {}

    /**
     * Fills a wandering trader's offers from its vanilla trade sets and the villagerpack changes. Returns false when
     * no villagerpack changes the wandering trader, so vanilla picks its trades as usual.
     */
    public static boolean addOffers(WanderingTrader trader, ServerLevel level) {
        Plan current;
        try {
            current = ensureBuilt(level.getServer().registryAccess());
        } catch (RuntimeException e) {
            LOGGER.error("Failed to read the villagerpack wandering trader trades, the trader keeps its vanilla trades", e);
            return false;
        }
        if (current.isEmpty()) return false;

        MerchantOffers offers = trader.getOffers();
        try {
            Registry<TradeSet> tradeSets = level.registryAccess().lookupOrThrow(Registries.TRADE_SET);
            Set<String> handled = new HashSet<>();
            for (ResourceKey<TradeSet> key : VANILLA_POOLS) {
                String path = key.identifier().getPath();
                String name = path.startsWith(POOL_PREFIX) ? path.substring(POOL_PREFIX.length()) : path;
                handled.add(name);

                Optional<TradeSet> tradeSet = tradeSets.getOptional(key);
                LootContext context = context(trader, level, tradeSet.flatMap(TradeSet::randomSequence));
                List<VillagerTrade> vanilla = tradeSet
                        .map(set -> set.trades().stream().map(Holder::value).toList())
                        .orElse(List.of());
                Integer amount = tradeSet.map(set -> set.calculateNumberOfTrades(context)).orElse(null);
                fill(context, offers, current, name, vanilla, amount);
            }
            for (String name : current.pools.keySet()) {
                if (!handled.contains(name)) {
                    fill(context(trader, level, Optional.empty()), offers, current, name, List.of(), null);
                }
            }
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Failed to pick the wandering trader's villagerpack trades, using its vanilla trades instead", e);
            offers.clear();
            return false;
        }
    }

    private static LootContext context(WanderingTrader trader, ServerLevel level, Optional<Identifier> randomSequence) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, trader.position())
                .withParameter(LootContextParams.THIS_ENTITY, trader)
                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .create(LootContextParamSets.VILLAGER_TRADE);
        return new LootContext.Builder(params).create(randomSequence);
    }

    private static void fill(LootContext context, MerchantOffers offers, Plan plan, String name,
                             List<VillagerTrade> vanilla, Integer vanillaAmount) {
        Pool pool = plan.pools.get(name);
        List<VillagerTrade> candidates = new ArrayList<>();
        if (!plan.replaceAll && (pool == null || !pool.replace)) {
            candidates.addAll(vanilla);
        }
        if (pool != null) {
            candidates.addAll(pool.trades);
        }

        int amount = pool != null && pool.amount != null ? pool.amount
                : vanillaAmount != null ? vanillaAmount : candidates.size();
        RandomSource random = context.getRandom();
        int added = 0;
        while (added < amount && !candidates.isEmpty()) {
            VillagerTrade trade = candidates.remove(random.nextInt(candidates.size()));
            MerchantOffer offer;
            try {
                offer = trade.getOffer(context);
            } catch (RuntimeException e) {
                LOGGER.error("A wandering trader trade in pool '{}' failed to create its offer, skipping it", name, e);
                continue;
            }
            if (offer == null || plan.removes(offer)) continue;
            offers.add(offer);
            added++;
        }
    }

    /**
     * Reads the villagerpacks' wandering trader files when the server starts, so mistakes show up in the log
     * straight away instead of when the first wandering trader makes its offers.
     */
    public static void rebuild(HolderLookup.Provider registries) {
        synchronized (VillagerPackWanderingTrades.class) {
            plan = build(registries);
            builtFor = registries;
        }
    }

    private static Plan ensureBuilt(HolderLookup.Provider registries) {
        if (builtFor != registries) {
            synchronized (VillagerPackWanderingTrades.class) {
                if (builtFor != registries) {
                    plan = build(registries);
                    builtFor = registries;
                }
            }
        }
        return plan;
    }

    private static Plan build(HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Builder builder = new Builder();
        VillagerPackLoader loader = new VillagerPackLoader(CommonPlatformHelper.getGameDirectory());
        List<VillagerPackLoader.VillagerPack> packs = loader.getDiscoveredPacks();
        try {
            for (VillagerPackLoader.VillagerPack pack : packs) {
                if (!pack.hasConfig()) continue;
                String packKey = pack.getName();
                String namespace = pack.getNamespace();
                Path root = pack.getRootPath();
                List<JsonObject> files = readFiles(root.resolve("villagers/wandering_trader"), packKey);
                if (files.isEmpty()) continue;
                Map<String, String> tagNamespaceMap = VillagerPackCodecs.loadTagNamespaceMap(namespace, root);
                for (JsonObject json : files) {
                    builder.read(json, packKey, (source, description) ->
                            decode(VillagerPackTrades.prepareTrade(namespace, source, tagNamespaceMap), ops, description));
                }
            }
        } finally {
            for (VillagerPackLoader.VillagerPack pack : packs) {
                pack.close();
            }
        }

        Plan built = builder.build();
        if (!built.isEmpty()) {
            LOGGER.info("Wandering trader trades from villagerpacks: {}", built.describe());
        }
        return built;
    }

    private static VillagerTrade decode(JsonObject trade, RegistryOps<JsonElement> ops, String description) {
        return VillagerTrade.CODEC.parse(ops, trade)
                .resultOrPartial(error -> LOGGER.error("{} is not a valid trade: {}", description, error))
                .orElse(null);
    }

    private static List<JsonObject> readFiles(Path dir, String packKey) {
        if (!Files.isDirectory(dir)) return List.of();
        List<JsonObject> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element.isJsonObject()) {
                        files.add(element.getAsJsonObject());
                    } else {
                        LOGGER.warn("Wandering trader file {} in villagerpack '{}' is not a JSON object, skipping it", path.getFileName(), packKey);
                    }
                } catch (IOException | RuntimeException e) {
                    LOGGER.error("Failed to read wandering trader file {} in villagerpack '{}'", path.getFileName(), packKey, e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list the wandering trader files of villagerpack '{}'", packKey, e);
        }
        return files;
    }

    /**
     * Turns one trade entry into a vanilla trade, the same way villager trades are read.
     */
    @FunctionalInterface
    interface TradeParser {
        VillagerTrade parse(JsonObject source, String description);
    }

    static final class Builder {
        private boolean replaceAll;
        private final Set<Identifier> removed = new LinkedHashSet<>();
        private final Map<String, Pool> pools = new LinkedHashMap<>();

        /**
         * Reads one wandering trader file. Packs are read in priority order: the first pack to set a pool's
         * amount wins, while replacing, removing and adding trades all add up.
         */
        void read(JsonObject json, String packKey, TradeParser parser) {
            String context = "wandering trader file in villagerpack '" + packKey + "'";

            if (json.has("replace") && json.get("replace").isJsonPrimitive() && json.get("replace").getAsBoolean()) {
                replaceAll = true;
            }

            if (json.has("remove")) {
                readRemoved(json.get("remove"), context);
            }

            if (json.has("pools")) {
                if (!json.get("pools").isJsonObject()) {
                    LOGGER.warn("'pools' in {} must be an object of pool names, ignoring it", context);
                    return;
                }
                JsonObject poolsJson = json.getAsJsonObject("pools");
                for (String key : poolsJson.keySet()) {
                    readPool(poolName(key), poolsJson.get(key), context, parser);
                }
            }
        }

        private void readRemoved(JsonElement element, String context) {
            if (!element.isJsonArray()) {
                LOGGER.warn("'remove' in {} must be a list of item ids, ignoring it", context);
                return;
            }
            for (JsonElement entry : element.getAsJsonArray()) {
                Identifier id = entry.isJsonPrimitive() ? Identifier.tryParse(entry.getAsString()) : null;
                if (id == null) {
                    LOGGER.warn("'remove' in {} has an invalid item id '{}', ignoring it", context, entry);
                } else if (!BuiltInRegistries.ITEM.containsKey(id)) {
                    LOGGER.warn("'remove' in {} names item '{}', which doesn't exist, ignoring it", context, id);
                } else {
                    removed.add(id);
                }
            }
        }

        private void readPool(String name, JsonElement element, String context, TradeParser parser) {
            String poolContext = "pool '" + name + "' of the " + context;
            JsonArray trades = null;
            boolean replace = false;
            Integer amount = null;

            if (element.isJsonArray()) {
                trades = element.getAsJsonArray();
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("trades") && object.get("trades").isJsonArray()) {
                    trades = object.getAsJsonArray("trades");
                }
                replace = object.has("replace") && object.get("replace").isJsonPrimitive() && object.get("replace").getAsBoolean();
                if (object.has("amount")) {
                    try {
                        amount = object.get("amount").getAsInt();
                    } catch (RuntimeException e) {
                        LOGGER.warn("'amount' of {} must be a number, ignoring it", poolContext);
                    }
                    if (amount != null && amount < 0) {
                        LOGGER.warn("'amount' of {} can't be negative, ignoring it", poolContext);
                        amount = null;
                    }
                }
            } else {
                LOGGER.warn("{} must be a list of trades or an object, ignoring it", poolContext);
                return;
            }

            Pool pool = pools.computeIfAbsent(name, key -> new Pool());
            if (replace) pool.replace = true;
            if (amount != null && pool.amount == null) pool.amount = amount;

            if (trades == null) return;
            int index = 0;
            for (JsonElement trade : trades) {
                index++;
                if (!trade.isJsonObject()) continue;
                VillagerTrade parsed = parser.parse(trade.getAsJsonObject(), "trade " + index + " in " + poolContext);
                if (parsed != null) {
                    pool.trades.add(parsed);
                }
            }
        }

        /**
         * Vanilla's pools may also be written with the minecraft namespace, like "minecraft:common".
         */
        private static String poolName(String key) {
            String name = key.toLowerCase(Locale.ROOT);
            return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        }

        Plan build() {
            if (!replaceAll && removed.isEmpty() && pools.isEmpty()) return Plan.EMPTY;
            return new Plan(replaceAll, Set.copyOf(removed), new LinkedHashMap<>(pools));
        }
    }

    private static final class Pool {
        private final List<VillagerTrade> trades = new ArrayList<>();
        private boolean replace;
        private Integer amount;
    }

    private record Plan(boolean replaceAll, Set<Identifier> removed, Map<String, Pool> pools) {
        private static final Plan EMPTY = new Plan(false, Set.of(), Map.of());

        boolean isEmpty() {
            return !replaceAll && removed.isEmpty() && pools.isEmpty();
        }

        boolean removes(MerchantOffer offer) {
            if (removed.isEmpty()) return false;
            if (isRemoved(offer.getResult().getItem()) || isRemoved(offer.getItemCostA().item().value())) return true;
            return offer.getItemCostB().map(cost -> isRemoved(cost.item().value())).orElse(false);
        }

        private boolean isRemoved(Item item) {
            return removed.contains(BuiltInRegistries.ITEM.getKey(item));
        }

        String describe() {
            List<String> parts = new ArrayList<>();
            if (replaceAll) parts.add("vanilla trades replaced");
            if (!removed.isEmpty()) parts.add("trades with " + removed + " removed");
            for (Map.Entry<String, Pool> entry : pools.entrySet()) {
                Pool pool = entry.getValue();
                boolean known = KNOWN_POOLS.contains(entry.getKey());
                StringBuilder text = new StringBuilder(known ? "pool '" : "new pool '").append(entry.getKey()).append("'");
                if (pool.replace) text.append(" replaced");
                if (!pool.trades.isEmpty()) text.append(" +").append(pool.trades.size()).append(" trade(s)");
                if (pool.amount != null) text.append(", offers ").append(pool.amount);
                parts.add(text.toString());
            }
            return String.join("; ", parts);
        }
    }
}
