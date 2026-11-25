package com.frikinjay.villagerapi.mixin;

import com.frikinjay.villagerapi.villagerpack.VillagerPackWanderingTrades;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WanderingTrader.class)
public abstract class WanderingTraderTradesMixin {

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void villagerapi$addPackTradeOffers(ServerLevel level, CallbackInfo ci) {
        if (VillagerPackWanderingTrades.addOffers((WanderingTrader) (Object) this, level)) {
            ci.cancel();
        }
    }
}
