package com.frikinjay.villagerapi.platform.fabric;

import com.frikinjay.villagerapi.mixin.PoiTypesInvoker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class CommonPlatformHelperImpl {

    public static Supplier<Block> registerWorkstationBlock(String namespace, String name, Supplier<Block> blockSupplier) {
        Block workstationBlock = blockSupplier.get();
        Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
        ResourceKey<Block> resourceKey = ResourceKey.create(Registries.BLOCK, id);
        Block registeredBlock = Registry.register(BuiltInRegistries.BLOCK, resourceKey, workstationBlock);
        return () -> registeredBlock;
    }

    public static Supplier<Item> registerWorkstationItem(String namespace, String name, Supplier<Item> item) {
        Item workstationItem = item.get();
        Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
        ResourceKey<Item> resourceKey = ResourceKey.create(Registries.ITEM, id);
        Item registeredItem = Registry.register(BuiltInRegistries.ITEM, resourceKey, workstationItem);
        return () -> registeredItem;
    }

    public static Supplier<VillagerType> registerVillagerType(String namespace, String name, Supplier<VillagerType> type) {
        VillagerType villagerType = type.get();
        Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
        ResourceKey<VillagerType> resourceKey = ResourceKey.create(Registries.VILLAGER_TYPE, id);
        VillagerType registered = Registry.register(BuiltInRegistries.VILLAGER_TYPE, resourceKey, villagerType);
        return () -> registered;
    }

    public static Supplier<MapDecorationType> registerMapDecorationType(String namespace, String name, Supplier<MapDecorationType> decoration) {
        MapDecorationType decorationType = decoration.get();
        Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
        MapDecorationType registered = Registry.register(BuiltInRegistries.MAP_DECORATION_TYPE, id, decorationType);
        return () -> registered;
    }

    public static Supplier<VillagerProfession> registerProfession(String namespace, String name, Supplier<VillagerProfession> profession) {
        VillagerProfession prof = profession.get();
        Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
        VillagerProfession registered = Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, id, prof);
        return () -> registered;
    }

    public static Supplier<PoiType> registerPoiType(String namespace, String name, Supplier<Set<BlockState>> matchingStates) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
        ResourceKey<PoiType> resourceKey = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, id);

        Set<BlockState> states = matchingStates.get();

        PoiType poiType = new PoiType(states, 1, 1);
        PoiType registered = Registry.register(BuiltInRegistries.POINT_OF_INTEREST_TYPE, id, poiType);

        if (!states.isEmpty()) {
            PoiTypesInvoker.invokeRegisterBlockStates(
                    BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(resourceKey),
                    states
            );
        }

        return () -> registered;
    }

    public static boolean isModLoadedMV(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    public static Path getGameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Map<String, Path> getAllModRootPaths() {
        Map<String, Path> modPaths = new HashMap<>();
        net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().forEach(mod -> {
            List<Path> roots = mod.getRootPaths();
            if (!roots.isEmpty()) {
                modPaths.put(mod.getMetadata().getId(), roots.getFirst());
            }
        });
        return modPaths;
    }
}