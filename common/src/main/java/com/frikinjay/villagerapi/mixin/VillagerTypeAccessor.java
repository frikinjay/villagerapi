package com.frikinjay.villagerapi.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(VillagerType.class)
public interface VillagerTypeAccessor {

    @Accessor("BY_BIOME")
    static Map<ResourceKey<Biome>, VillagerType> getByBiomes() {
        throw new AssertionError();
    }

}
