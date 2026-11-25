package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapDecorations;

import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VAPIMaps {

    private static final RegistryHelper.DynamicRegistry<Item> MAPS =
            new RegistryHelper.DynamicRegistry<>("maps");

    public static void init() {}

    public static void registerFromPack(String name, JsonObject json) {
        try {
            if (!json.has("tag")) return;

            String rawTag = json.get("tag").getAsString();
            String namespace = json.has("namespace") ? json.get("namespace").getAsString() : VillagerAPI.MOD_ID;

            String tag = rawTag;
            if (rawTag.contains(":")) {
                String[] split = rawTag.split(":");
                namespace = split[0];
                tag = split[1];
            }

            String itemName = tag;
            if (itemName.startsWith("on_")) itemName = itemName.substring(3);
            if (itemName.endsWith("_explorer_maps")) itemName = itemName.substring(0, itemName.length() - 14);
            else if (itemName.endsWith("_maps")) itemName = itemName.substring(0, itemName.length() - 5);
            itemName = itemName + "_map";

            Identifier id = Identifier.fromNamespaceAndPath(namespace, itemName);
            ResourceKey<Item> resourceKeyItem = ResourceKey.create(Registries.ITEM, id);

            Supplier<Item> mapItem = CommonPlatformHelper.registerMapItem(
                    namespace,
                    itemName,
                    () -> new MapItem(new Item.Properties()
                            .setId(resourceKeyItem)
                            .component(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY)
                    )
            );

            MAPS.register(itemName, mapItem);

            LOGGER.info("Registered map item from pack structure tag: {}", itemName);

        } catch (Exception e) {
            LOGGER.error("Failed to register map item from pack structure tag: {}", name, e);
        }
    }

    public static Supplier<Item> getDynamicMap(String name) {
        return MAPS.get(name);
    }

    public static java.util.Map<String, Supplier<Item>> getDynamicMaps() {
        return MAPS.getAll();
    }
}