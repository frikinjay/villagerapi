package com.frikinjay.villagerapi.mixin;

import com.frikinjay.villagerapi.villagerpack.BiomeMappingCache;
import net.minecraft.core.Holder;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerStructureSpawnMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void villagerapi$useBiomeTypeForStructureVillagers(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                               EntitySpawnReason spawnReason, SpawnGroupData groupData,
                                                               CallbackInfoReturnable<SpawnGroupData> cir) {
        if (spawnReason != EntitySpawnReason.STRUCTURE) return;

        Villager villager = (Villager) (Object) this;
        Holder<Biome> biome = level.getBiome(villager.blockPosition());
        if (!BiomeMappingCache.hasMapping(biome)) return;

        villager.setVillagerData(villager.getVillagerData().withType(level.registryAccess(), VillagerType.byBiome(biome)));
        villager.setVillagerDataFinalized(true);
    }
}