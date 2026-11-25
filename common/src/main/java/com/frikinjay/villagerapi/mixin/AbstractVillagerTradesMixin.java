package com.frikinjay.villagerapi.mixin;

import com.frikinjay.villagerapi.villagerpack.VillagerPackTradeSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradesMixin {

    @Inject(method = "addOffersFromTradeSet", at = @At("HEAD"), cancellable = true)
    private void villagerapi$addPackTradeOffers(ServerLevel level, MerchantOffers offers, ResourceKey<TradeSet> resourceKey, CallbackInfo ci) {
        if (VillagerPackTradeSelector.addOffers((AbstractVillager) (Object) this, level, offers, resourceKey)) {
            ci.cancel();
        }
    }
}