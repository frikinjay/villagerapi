package com.frikinjay.villagerapi.fabric;

import com.frikinjay.villagerapi.registry.VAPICreativeTabs;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackCreativeTabsFabric {

    public static void registerAllTabs() {
        VAPICreativeTabs.getAllTabs().forEach((namespace, tabData) -> {
            try {
                CreativeModeTab tab = FabricItemGroup.builder()
                        .icon(tabData::getIconItemStack)
                        .title(Component.translatable("itemGroup." + namespace + ".villagerpack_tab"))
                        .displayItems((context, entries) -> {
                            for (Supplier<Block> workstation : tabData.getWorkstations()) {
                                Block block = workstation.get();
                                if (block != null) {
                                    entries.accept(new ItemStack(block));
                                }
                            }
                        })
                        .build();

                Registry.register(
                        BuiltInRegistries.CREATIVE_MODE_TAB,
                        VAPICreativeTabs.getTabId(namespace),
                        tab
                );

                LOGGER.info("Registered creative tab for villagerpack '{}' with {} items",
                        tabData.getDisplayName(), tabData.getWorkstations().size());
            } catch (Exception e) {
                LOGGER.error("Failed to register creative tab for namespace '{}'", namespace, e);
            }
        });
    }
}