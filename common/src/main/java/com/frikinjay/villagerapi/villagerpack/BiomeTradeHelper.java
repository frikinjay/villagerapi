package com.frikinjay.villagerapi.villagerpack;

//import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
//import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.component.predicates.VillagerTypePredicate;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

public class BiomeTradeHelper {

/*
    */
/**
     * Generates the LootItemCondition required to restrict a VillagerTrade to a single biome type.
     * Pass the result of this method into the Optional<LootItemCondition> parameter of the VillagerTrade constructor.
     *
     * @param villagerVariants The HolderGetter for VillagerTypes (obtained from BootstrapContext)
     * @param targetType The specific VillagerType this trade should be restricted to
     * @return An Optional containing the restriction condition
     *//*

    public static Optional<LootItemCondition> restrictToBiome(HolderGetter<VillagerType> villagerVariants, ResourceKey<VillagerType> targetType) {
        return restrictToBiomes(villagerVariants, List.of(targetType));
    }

    */
/**
     * Generates the LootItemCondition required to restrict a VillagerTrade to multiple biome types.
     *
     * @param villagerVariants The HolderGetter for VillagerTypes
     * @param targetTypes A list of VillagerTypes this trade is allowed to appear for
     * @return An Optional containing the restriction condition
     *//*

    public static Optional<LootItemCondition> restrictToBiomes(HolderGetter<VillagerType> villagerVariants, List<ResourceKey<VillagerType>> targetTypes) {
        if (targetTypes == null || targetTypes.isEmpty()) {
            return Optional.empty();
        }

        List<Holder<VillagerType>> holders = new ArrayList<>();
        for (ResourceKey<VillagerType> typeKey : targetTypes) {
            Optional<Holder.Reference<VillagerType>> holderOpt = villagerVariants.get(typeKey);
            holderOpt.ifPresent(holders::add);
        }

        if (holders.isEmpty()) {
            return Optional.empty();
        }

        HolderSet<VillagerType> allowedTypes = HolderSet.direct(holders);

        return Optional.of(new LootItemEntityPropertyCondition(
                Optional.of(EntityPredicate.Builder.entity().components(
                        DataComponentMatchers.Builder.components().partial(
                                DataComponentPredicates.VILLAGER_VARIANT,
                                VillagerTypePredicate.villagerTypes(allowedTypes)
                        ).build()
                ).build()),
                LootContext.EntityTarget.THIS
        ));
    }
*/

}