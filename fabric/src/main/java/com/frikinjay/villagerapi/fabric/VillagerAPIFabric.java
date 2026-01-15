package com.frikinjay.villagerapi.fabric;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.fabric.villagerpack.VillagerPackRegistrationFabric;
import com.frikinjay.villagerapi.villagerpack.PackInitializationOrchestrator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.jetbrains.annotations.NotNull;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

/**
 * Fabric platform implementation - simplified using orchestrator
 */
public final class VillagerAPIFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        VillagerAPI.init();

        PackInitializationOrchestrator.registerContent();

        VillagerPackCreativeTabsFabric.registerAllTabs();
        VillagerPackRegistrationFabric.registerPacks();

        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                PackInitializationOrchestrator.onServerStarting(server)
        );

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PackInitializationOrchestrator.onServerStarted(server, server.registryAccess());

            if (LOGGER.isDebugEnabled()) {
                logJobSitePOIs();
            }
        });
    }

    private void logJobSitePOIs() {
        TagKey<@NotNull PoiType> jobSiteTag = TagKey.create(
                Registries.POINT_OF_INTEREST_TYPE,
                Identifier.withDefaultNamespace("acquirable_job_site")
        );

        Registry<@NotNull PoiType> poiRegistry = BuiltInRegistries.POINT_OF_INTEREST_TYPE;
        poiRegistry.get(jobSiteTag).ifPresent(tag ->
                tag.forEach(holder ->
                        LOGGER.debug("Job site POI: {}", poiRegistry.getKey(holder.value()))
                )
        );
    }
}