package com.frikinjay.villagerapi.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.HashMap;
import java.util.Map;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

/**
 * Prevents crash when custom villager types are added without EmeraldsForVillagerTypeItem entries.
 */
@Mixin(VillagerTrades.EmeraldsForVillagerTypeItem.class)
public class EmeraldsForVillagerTypeItemMixin {

    /**
     * Captures and modifies the 'map' parameter in the constructor before it's used for validation.
     * This adds all missing villager types with the PLAINS item as a fallback.
     */
    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private static Map<ResourceKey<@NotNull VillagerType>, Item> addMissingVillagerTypes(Map<ResourceKey<@NotNull VillagerType>, Item> originalTrades) {
        Item plainsItem = originalTrades.get(VillagerType.PLAINS);

        if (plainsItem == null) {
            LOGGER.warn("PLAINS villager type not found in EmeraldsForVillagerTypeItem trade map, cannot add fallbacks");
            return originalTrades;
        }

        Map<ResourceKey<@NotNull VillagerType>, Item> expandedTrades = new HashMap<>(originalTrades);

        int addedCount = 0;
        for (ResourceKey<@NotNull VillagerType> typeKey : BuiltInRegistries.VILLAGER_TYPE.registryKeySet()) {
            if (!expandedTrades.containsKey(typeKey)) {
                expandedTrades.put(typeKey, plainsItem);
                addedCount++;
                LOGGER.debug("Added fallback PLAINS item for custom villager type: {}", typeKey.identifier());
            }
        }

        if (addedCount > 0) {
            LOGGER.info("Added {} custom villager type(s) to Fisherman level 5 trade with PLAINS fallback", addedCount);
        }

        return expandedTrades;
    }
}