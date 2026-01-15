package com.frikinjay.villagerapi.neoforge;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.registry.VAPICreativeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

@EventBusSubscriber(modid = VillagerAPI.MOD_ID)
public class VillagerPackCreativeTabsNeoForge {

    @SubscribeEvent
    public static void registerCreativeTabs(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            VAPICreativeTabs.getAllTabs().forEach((namespace, tabData) -> {
                try {
                    List<ItemStack> stacks = new ArrayList<>();
                    for (Supplier<Block> workstation : tabData.getWorkstations()) {
                        Block block = workstation.get();
                        if (block != null) {
                            stacks.add(new ItemStack(block));
                        }
                    }

                    CreativeModeTab tab = CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup." + namespace + ".villagerpack_tab"))
                            .icon(tabData::getIconItemStack)
                            .displayItems((parameters, output) -> output.acceptAll(stacks))
                            .build();

                    helper.register(VAPICreativeTabs.getTabId(namespace), tab);

                    LOGGER.info("Registered creative tab for villagerpack '{}' with {} items",
                            tabData.getDisplayName(), stacks.size());
                } catch (Exception e) {
                    LOGGER.error("Failed to register creative tab for namespace '{}'", namespace, e);
                }
            });
        });
    }
}