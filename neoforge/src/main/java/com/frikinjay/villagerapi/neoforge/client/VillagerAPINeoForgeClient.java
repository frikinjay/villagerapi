package com.frikinjay.villagerapi.neoforge.client;

import com.frikinjay.villagerapi.VillagerAPI;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = VillagerAPI.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = VillagerAPI.MOD_ID, value = Dist.CLIENT)
public class VillagerAPINeoForgeClient {

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        /*event.enqueueWork(() ->

        );*/
    }

}
