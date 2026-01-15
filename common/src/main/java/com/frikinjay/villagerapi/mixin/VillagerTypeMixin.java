package com.frikinjay.villagerapi.mixin;

import com.frikinjay.villagerapi.villagerpack.BiomeMappingCache;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

@Mixin(VillagerType.class)
public class VillagerTypeMixin {

    @Inject(method = "byBiome", at = @At("HEAD"), cancellable = true)
    private static void applyCustomBiomeMappings(Holder<Biome> biomeHolder, CallbackInfoReturnable<ResourceKey<VillagerType>> cir) {
        biomeHolder.unwrapKey().ifPresent(biomeKey -> {
            ResourceKey<VillagerType> customTypeKey = BiomeMappingCache.getVillagerTypeForBiome(biomeKey);

            if (customTypeKey != null) {
                LOGGER.debug("Applying custom villager type mapping: {} -> {}",
                        biomeKey.registryKey(), customTypeKey.registryKey());
                cir.setReturnValue(customTypeKey);
            }
        });
    }
}