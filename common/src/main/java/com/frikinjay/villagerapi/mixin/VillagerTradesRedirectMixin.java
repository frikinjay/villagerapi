package com.frikinjay.villagerapi.mixin;

import net.minecraft.world.entity.npc.villager.VillagerTrades;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VillagerTrades.class)
public class VillagerTradesRedirectMixin {

    /*@Redirect(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/Util;make(Ljava/lang/Object;Ljava/util/function/Consumer;)Ljava/lang/Object;"))
    private static <T> T redirectTradesInitialization(T object, Consumer<? super T> consumer) {
        // Return our map instead of the vanilla one because injecting it failed
        return (T) VillagerTypeTradesHelper.createCustomTrades();
    }*/
}