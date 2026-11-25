package com.frikinjay.villagerapi.neoforge.villagerpack;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.villagerpack.VillagerPackRepositorySource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(modid = VillagerAPI.MOD_ID)
public class VillagerPackRegistrationNeoForge {

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        event.addRepositorySource(new VillagerPackRepositorySource(event.getPackType()));
    }
}