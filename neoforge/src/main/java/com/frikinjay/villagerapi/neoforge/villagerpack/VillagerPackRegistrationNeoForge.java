package com.frikinjay.villagerapi.neoforge.villagerpack;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.villagerpack.VillagerPackHelper;
import com.frikinjay.villagerapi.villagerpack.VillagerPackLoader;
import com.frikinjay.villagerapi.villagerpack.VillagerPackResourceManager;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.ArrayList;
import java.util.List;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

/**
 * NeoForge pack registration - simplified using VillagerPackResourceManager
 */
@EventBusSubscriber(modid = VillagerAPI.MOD_ID)
public class VillagerPackRegistrationNeoForge {

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        PackType packType = event.getPackType();

        // Determine which filter to use based on pack type
        List<VillagerPackResourceManager.PackFilter> filters = new ArrayList<>();
        filters.add(VillagerPackResourceManager.PackFilter.HAS_CONFIG);

        if (packType == PackType.SERVER_DATA) {
            filters.add(VillagerPackResourceManager.PackFilter.HAS_DATA);
        } else if (packType == PackType.CLIENT_RESOURCES) {
            filters.add(VillagerPackResourceManager.PackFilter.HAS_ASSETS);
        }

        VillagerPackResourceManager.DiscoveryResult result =
                VillagerPackResourceManager.discoverAndProcessPacks(filters, pack -> {
                    String displayName = pack.getDisplayName();
                    String namespace = pack.getNamespace();

                    event.addRepositorySource(consumer -> {
                        Pack registeredPack = VillagerPackHelper.createPack(
                                pack.getPath(),
                                displayName,
                                packType
                        );

                        if (registeredPack != null) {
                            consumer.accept(registeredPack);
                            LOGGER.info("Registered villagerpack '{}' as {} (namespace: {})",
                                    displayName, packType.name(), namespace);
                        } else {
                            LOGGER.warn("Failed to create pack for villagerpack: {}", displayName);
                        }
                    });
                });

        if (result.getSkippedCount() > 0) {
            LOGGER.debug("Skipped {} pack(s) for {} registration (missing required content)",
                    result.getSkippedCount(), packType.name());
        }
    }
}