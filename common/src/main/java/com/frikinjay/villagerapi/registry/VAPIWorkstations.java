package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.villagerpack.VillagerPackCodecs;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

/**
 * Optimized workstations registry using new helper classes
 */
public class VAPIWorkstations {

    private static final RegistryHelper.DynamicRegistry<Block> WORKSTATIONS =
            new RegistryHelper.DynamicRegistry<>("workstations");

    public static void init() {}

    public static void registerFromPack(String name, JsonObject json) {
        try {
            //VillagerPackCodecs.WorkstationData workstationData = VillagerPackCodecs.parseWorkstation(json);
            String registryName = RegistryHelper.sanitizeRegistryName(name);
            String namespace = RegistryHelper.getNamespace(json, VillagerAPI.MOD_ID);
            Identifier id = Identifier.fromNamespaceAndPath(namespace, name);

            ResourceKey<Block> resourceKeyBlock = ResourceKey.create(Registries.BLOCK, id);

            Supplier<Block> block = CommonPlatformHelper.registerWorkstationBlock(
                    namespace,
                    registryName,
                    () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.CARTOGRAPHY_TABLE).setId(resourceKeyBlock))
            );

            ResourceKey<Item> resourceKeyItem = ResourceKey.create(Registries.ITEM, id);

            CommonPlatformHelper.registerWorkstationItem(
                    namespace,
                    registryName,
                    () -> new BlockItem(block.get(), new Item.Properties().setId(resourceKeyItem))
            );

            WORKSTATIONS.register(name, block);

            addToCreativeTab(json, registryName, namespace, block);

            LOGGER.info("Registered villager workstation from pack: {}", name);

        } catch (Exception e) {
            LOGGER.error("Failed to register villager workstation from pack: {}", name, e);
        }
    }

    private static void addToCreativeTab(JsonObject json, String registryName, String namespace, Supplier<Block> block) {
        if (json.has("_pack_namespace")) {
            String packNamespace = json.get("_pack_namespace").getAsString();
            VAPICreativeTabs.addWorkstationToTab(packNamespace, block);
            LOGGER.debug("Added workstation '{}' to creative tab for pack '{}'", registryName, packNamespace);
        }
    }

    public static Supplier<Block> getDynamicWorkstation(String name) {
        return WORKSTATIONS.get(name);
    }

    public static java.util.Map<String, Supplier<Block>> getDynamicWorkstations() {
        return WORKSTATIONS.getAll();
    }
}