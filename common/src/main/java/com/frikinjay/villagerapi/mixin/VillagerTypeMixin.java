package com.frikinjay.villagerapi.mixin;

import com.frikinjay.villagerapi.villagerpack.BiomeMappingCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VillagerType.class)
public class VillagerTypeMixin {

    @WrapMethod(method = "byBiome")
    private static ResourceKey<VillagerType> villagerapi$pickPackVillagerType(Holder<Biome> biome, Operation<ResourceKey<VillagerType>> original) {
        ResourceKey<VillagerType> result = original.call(biome);
        return BiomeMappingCache.pick(biome, result);
    }
}
