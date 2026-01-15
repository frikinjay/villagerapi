package com.frikinjay.villagerapi.neoforge;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.platform.neoforge.CommonPlatformHelperImpl;
import com.frikinjay.villagerapi.villagerpack.PackInitializationOrchestrator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * NeoForge platform implementation - simplified using orchestrator
 */
@Mod(VillagerAPI.MOD_ID)
public final class VillagerAPINeoForge {

    public VillagerAPINeoForge(IEventBus modEventBus, ModContainer container) {

        VillagerAPI.init();
        PackInitializationOrchestrator.registerContent();
        CommonPlatformHelperImpl.registerAll(modEventBus);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        PackInitializationOrchestrator.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        PackInitializationOrchestrator.onServerStarted(
                event.getServer(),
                event.getServer().registryAccess()
        );
    }
}