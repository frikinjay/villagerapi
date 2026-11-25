package com.frikinjay.villagerapi.villagerpack;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.*;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public final class VillagerPackTradeSelector {

    private static volatile Map<Identifier, SelectionPlan> plans = Map.of();

    private VillagerPackTradeSelector() {}

    public static void setPlans(Map<Identifier, SelectionPlan> newPlans) {
        plans = Map.copyOf(newPlans);
    }

    public static boolean hasPlan(Identifier tradeSetId) {
        return plans.containsKey(tradeSetId);
    }

    public static boolean addOffers(AbstractVillager villager, ServerLevel level, MerchantOffers offers, ResourceKey<TradeSet> resourceKey) {
        SelectionPlan plan = plans.get(resourceKey.identifier());
        if (plan == null) return false;

        try {
            List<SelectedOffer> selected = new ArrayList<>();
            boolean anyGroupFound = plan.groups().isEmpty();

            for (GroupPlan group : plan.groups()) {
                ResourceKey<TradeSet> groupKey = ResourceKey.create(Registries.TRADE_SET, group.tradeSetId());
                Optional<TradeSet> tradeSetOpt = level.registryAccess().lookupOrThrow(Registries.TRADE_SET).getOptional(groupKey);
                if (tradeSetOpt.isEmpty()) {
                    LOGGER.warn("Villagerpack trade set {} used by {} is missing, skipping it", group.tradeSetId(), resourceKey.identifier());
                    continue;
                }

                anyGroupFound = true;
                TradeSet tradeSet = tradeSetOpt.get();
                LootContext lootContext = createLootContext(villager, level, tradeSet);
                int count = tradeSet.calculateNumberOfTrades(lootContext);
                if (count <= 0) continue;

                selectWeighted(lootContext, tradeSet.trades(), group.weights(), count, tradeSet.allowDuplicates(), selected);
            }

            if (!anyGroupFound) {
                LOGGER.warn("No villagerpack trade sets found for {}, falling back to vanilla trade selection", resourceKey.identifier());
                return false;
            }

            if (plan.maxTrades() != null && selected.size() > plan.maxTrades()) {
                selected = limitWeighted(selected, plan.maxTrades(), villager.getRandom());
            }

            for (SelectedOffer selectedOffer : selected) {
                offers.add(selectedOffer.offer());
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to select villagerpack trades for {}, falling back to vanilla trade selection", resourceKey.identifier(), e);
            return false;
        }
    }

    private static LootContext createLootContext(AbstractVillager villager, ServerLevel level, TradeSet tradeSet) {
        return new LootContext.Builder(
                new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, villager.position())
                        .withParameter(LootContextParams.THIS_ENTITY, villager)
                        .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                        .create(LootContextParamSets.VILLAGER_TRADE)
        ).create(tradeSet.randomSequence());
    }

    private static void selectWeighted(LootContext lootContext, HolderSet<VillagerTrade> trades, Map<Identifier, Integer> weights, int count, boolean allowDuplicates, List<SelectedOffer> output) {
        List<Holder<VillagerTrade>> pool = new ArrayList<>();
        List<Integer> poolWeights = new ArrayList<>();

        for (Holder<VillagerTrade> trade : trades) {
            int weight = trade.unwrapKey()
                    .map(key -> weights.getOrDefault(key.identifier(), 1))
                    .orElse(1);
            if (weight > 0) {
                pool.add(trade);
                poolWeights.add(weight);
            }
        }

        int found = 0;
        while (found < count && !pool.isEmpty()) {
            int index = pickWeightedIndex(poolWeights, lootContext.getRandom());
            Holder<VillagerTrade> trade = pool.get(index);
            int weight = poolWeights.get(index);
            MerchantOffer offer = trade.value().getOffer(lootContext);

            if (offer == null || !allowDuplicates) {
                pool.remove(index);
                poolWeights.remove(index);
            }

            if (offer != null) {
                output.add(new SelectedOffer(offer, weight));
                found++;
            }
        }
    }

    private static List<SelectedOffer> limitWeighted(List<SelectedOffer> selected, int limit, RandomSource random) {
        List<Integer> remaining = new ArrayList<>();
        List<Integer> remainingWeights = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            remaining.add(i);
            remainingWeights.add(Math.max(1, selected.get(i).weight()));
        }

        Set<Integer> kept = new HashSet<>();
        while (kept.size() < limit && !remaining.isEmpty()) {
            int index = pickWeightedIndex(remainingWeights, random);
            kept.add(remaining.remove(index));
            remainingWeights.remove(index);
        }

        List<SelectedOffer> result = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            if (kept.contains(i)) {
                result.add(selected.get(i));
            }
        }
        return result;
    }

    private static int pickWeightedIndex(List<Integer> weights, RandomSource random) {
        long total = 0;
        for (int weight : weights) {
            total += weight;
        }

        double roll = random.nextDouble() * total;
        long cumulative = 0;
        for (int i = 0; i < weights.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) {
                return i;
            }
        }
        return weights.size() - 1;
    }

    private record SelectedOffer(MerchantOffer offer, int weight) {}

    public record GroupPlan(Identifier tradeSetId, Map<Identifier, Integer> weights) {}

    public record SelectionPlan(List<GroupPlan> groups, Integer maxTrades) {}
}