package com.frikinjay.villagerapi.mixin;

import com.frikinjay.villagerapi.villagerpack.BiomeTradesCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

@Mixin(Villager.class)
public abstract class VillagerMixin {

    @Shadow
    public abstract VillagerData getVillagerData();

    /**
     * Redirects trade map lookups (both EXPERIMENTAL_TRADES and TRADES) to return
     * biome-specific merged trades for this villager.
     */
    @Redirect(
            method = "updateTrades",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private Object useBiomeSpecificTrades(
            Map<ResourceKey<@NotNull VillagerProfession>, Int2ObjectMap<VillagerTrades.ItemListing[]>> tradesMap,
            Object professionKey
    ) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> originalTrades = tradesMap.get(professionKey);

        if (originalTrades == null) {
            return null;
        }

        VillagerData villagerData = this.getVillagerData();
        Holder<@NotNull VillagerProfession> professionHolder = villagerData.profession();
        VillagerType villagerType = villagerData.type().value();

        VillagerProfession profession = professionHolder.value();

        if (BiomeTradesCache.hasBiomeTrades(profession)) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> mergedTrades =
                    BiomeTradesCache.getMergedTrades(profession, villagerType);

            if (mergedTrades != null) {
                return mergedTrades;
            }
        }
        return originalTrades;
    }
}