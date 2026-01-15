package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.villagerpack.VillagerPackConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.*;
import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VAPICreativeTabs {

    public static class TabData {
        private final String namespace;
        private final String displayName;
        private final Identifier iconItemId;
        private final List<Supplier<Block>> workstations;

        public TabData(String namespace, String displayName, Identifier iconItemId) {
            this.namespace = namespace;
            this.displayName = displayName;
            this.iconItemId = iconItemId;
            this.workstations = new ArrayList<>();
        }

        public String getNamespace() {
            return namespace;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Identifier getIconItemId() {
            return iconItemId;
        }

        public ItemStack getIconItemStack() {
            Item item = BuiltInRegistries.ITEM.get(iconItemId)
                    .map(Holder.Reference::value)
                    .orElse(null);

            if (item == null || item == Items.AIR) {
                LOGGER.warn("Creative tab icon item '{}' not found, using emerald", iconItemId);
                return new ItemStack(Items.EMERALD);
            }
            return new ItemStack(item);
        }

        public List<Supplier<Block>> getWorkstations() {
            return workstations;
        }

        public void addWorkstation(Supplier<Block> workstation) {
            workstations.add(workstation);
        }
    }

    private static final Map<String, TabData> DYNAMIC_TABS = new HashMap<>();

    public static void init() {}

    /**
     * Register a creative tab for a villagerpack
     */
    public static void registerTab(VillagerPackConfig config) {
        String namespace = config.getNamespace();
        if (DYNAMIC_TABS.containsKey(namespace)) {
            LOGGER.warn("Creative tab for namespace '{}' already registered", namespace);
            return;
        }

        TabData tabData = new TabData(
                namespace,
                config.getDisplayName(),
                config.getCreativeTabIcon()
        );

        DYNAMIC_TABS.put(namespace, tabData);
        LOGGER.info("Registered creative tab for villagerpack '{}' (namespace: {})",
                config.getDisplayName(), namespace);
    }

    /**
     * Add a workstation to its pack's creative tab
     */
    public static void addWorkstationToTab(String packName, Supplier<Block> workstation) {
        TabData tabData = DYNAMIC_TABS.get(packName);
        if (tabData != null) {
            tabData.addWorkstation(workstation);
        }
    }

    /**
     * Get tab data for a namespace
     */
    public static TabData getTabData(String namespace) {
        return DYNAMIC_TABS.get(namespace);
    }

    /**
     * Get all registered tabs
     */
    public static Map<String, TabData> getAllTabs() {
        return new HashMap<>(DYNAMIC_TABS);
    }

    /**
     * Get resource location for a tab
     */
    public static Identifier getTabId(String namespace) {
        return Identifier.fromNamespaceAndPath(namespace, "villagerpack_tab");
    }
}