package com.frikinjay.villagerapi.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(GiveGiftToHero.class)
public abstract class GiveGiftToHeroMixin {
    @Final
    @Shadow
    @Mutable
    private static Map<VillagerProfession, ResourceKey<LootTable>> GIFTS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void makeGiftsMutable(CallbackInfo ci) {
        GIFTS = new HashMap<>(GIFTS);
    }
}
