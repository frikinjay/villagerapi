package com.frikinjay.villagerapi.platform.neoforge;

import com.frikinjay.villagerapi.VillagerAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class CommonPlatformHelperImpl {
    private static final Map<String, DeferredRegister<Block>> WORKSTATION_BLOCK_REGISTERS = new HashMap<>();
    private static final Map<String, DeferredRegister<Item>> WORKSTATION_ITEM_REGISTERS = new HashMap<>();
    private static final Map<String, DeferredRegister<VillagerType>> VILLAGER_TYPE_REGISTERS = new HashMap<>();
    private static final Map<String, DeferredRegister<MapDecorationType>> MAP_DECO_TYPE_REGISTERS = new HashMap<>();
    private static final Map<String, DeferredRegister<VillagerProfession>> PROFESSION_REGISTERS = new HashMap<>();
    private static final Map<String, DeferredRegister<PoiType>> POI_TYPE_REGISTERS = new HashMap<>();

    public static final DeferredRegister<Block> WORKSTATION_BLOCKS =
            getOrCreateWorkstationBlockRegister(VillagerAPI.MOD_ID);

    public static final DeferredRegister<Item> WORKSTATION_ITEMS =
            getOrCreateWorkstationItemRegister(VillagerAPI.MOD_ID);

    public static final DeferredRegister<VillagerType> VILLAGER_TYPES =
            getOrCreateVillagerTypeRegister(VillagerAPI.MOD_ID);

    public static final DeferredRegister<MapDecorationType> MAP_DECORATION_TYPES =
            getOrCreateMapDecoTypeRegister(VillagerAPI.MOD_ID);

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            getOrCreateProfessionRegister(VillagerAPI.MOD_ID);

    public static final DeferredRegister<PoiType> POI_TYPES =
            getOrCreatePoiTypeRegister(VillagerAPI.MOD_ID);

    private static DeferredRegister<Block> getOrCreateWorkstationBlockRegister(String namespace) {
        return WORKSTATION_BLOCK_REGISTERS.computeIfAbsent(namespace,
                ns -> DeferredRegister.create(BuiltInRegistries.BLOCK, ns));
    }

    private static DeferredRegister<Item> getOrCreateWorkstationItemRegister(String namespace) {
        return WORKSTATION_ITEM_REGISTERS.computeIfAbsent(namespace,
                ns -> DeferredRegister.create(BuiltInRegistries.ITEM, ns));
    }

    private static DeferredRegister<VillagerType> getOrCreateVillagerTypeRegister(String namespace) {
        return VILLAGER_TYPE_REGISTERS.computeIfAbsent(namespace,
                ns -> DeferredRegister.create(BuiltInRegistries.VILLAGER_TYPE, ns));
    }

    private static DeferredRegister<MapDecorationType> getOrCreateMapDecoTypeRegister(String namespace) {
        return MAP_DECO_TYPE_REGISTERS.computeIfAbsent(namespace,
                ns -> DeferredRegister.create(BuiltInRegistries.MAP_DECORATION_TYPE, ns));
    }

    private static DeferredRegister<VillagerProfession> getOrCreateProfessionRegister(String namespace) {
        return PROFESSION_REGISTERS.computeIfAbsent(namespace,
                ns -> DeferredRegister.create(BuiltInRegistries.VILLAGER_PROFESSION, ns));
    }

    private static DeferredRegister<PoiType> getOrCreatePoiTypeRegister(String namespace) {
        return POI_TYPE_REGISTERS.computeIfAbsent(namespace,
                ns -> DeferredRegister.create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, ns));
    }

    public static Supplier<Block> registerWorkstationBlock(String namespace, String name, Supplier<Block> block) {
        DeferredRegister<Block> register = getOrCreateWorkstationBlockRegister(namespace);
        return register.register(name, block);
    }

    public static Supplier<Item> registerWorkstationItem(String namespace, String name, Supplier<Item> item) {
        DeferredRegister<Item> register = getOrCreateWorkstationItemRegister(namespace);
        return register.register(name, item);
    }

    public static Supplier<VillagerType> registerVillagerType(String namespace, String name, Supplier<VillagerType> type) {
        DeferredRegister<VillagerType> register = getOrCreateVillagerTypeRegister(namespace);
        return register.register(name, type);
    }

    public static Supplier<MapDecorationType> registerMapDecorationType(String namespace, String name, Supplier<MapDecorationType> type) {
        DeferredRegister<MapDecorationType> register = getOrCreateMapDecoTypeRegister(namespace);
        return register.register(name, type);
    }

    public static Supplier<VillagerProfession> registerProfession(String namespace, String name, Supplier<VillagerProfession> profession) {
        DeferredRegister<VillagerProfession> register = getOrCreateProfessionRegister(namespace);
        return register.register(name, profession);
    }

    public static Supplier<PoiType> registerPoiType(String namespace, String name, Supplier<Set<BlockState>> matchingStates) {
        DeferredRegister<PoiType> register = getOrCreatePoiTypeRegister(namespace);
        return register.register(name, () -> {
            Set<BlockState> states = matchingStates.get();
            return new PoiType(states, 1, 1);
        });
    }

    public static boolean isModLoadedMV(String id) {
        return ModList.get().isLoaded(id);
    }

    public static Path getGameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    public static void registerAll(IEventBus modEventBus) {
        WORKSTATION_BLOCKS.register(modEventBus);
        WORKSTATION_ITEMS.register(modEventBus);
        VILLAGER_TYPES.register(modEventBus);
        MAP_DECORATION_TYPES.register(modEventBus);
        PROFESSIONS.register(modEventBus);
        POI_TYPES.register(modEventBus);

        WORKSTATION_BLOCK_REGISTERS.values().forEach(reg -> {
            if (reg != WORKSTATION_BLOCKS) reg.register(modEventBus);
        });
        WORKSTATION_ITEM_REGISTERS.values().forEach(reg -> {
            if (reg != WORKSTATION_ITEMS) reg.register(modEventBus);
        });
        VILLAGER_TYPE_REGISTERS.values().forEach(reg -> {
            if (reg != VILLAGER_TYPES) reg.register(modEventBus);
        });
        MAP_DECO_TYPE_REGISTERS.values().forEach(reg -> {
            if (reg != MAP_DECORATION_TYPES) reg.register(modEventBus);
        });
        PROFESSION_REGISTERS.values().forEach(reg -> {
            if (reg != PROFESSIONS) reg.register(modEventBus);
        });
        POI_TYPE_REGISTERS.values().forEach(reg -> {
            if (reg != POI_TYPES) reg.register(modEventBus);
        });
    }

    public static Map<String, Path> getAllModRootPaths() {
        Map<String, Path> modPaths = new HashMap<>();
        net.neoforged.fml.ModList.get().getMods().forEach(mod -> {
            Path root = mod.getOwningFile().getFile().getFilePath();
            modPaths.put(mod.getModId(), root);
        });
        return modPaths;
    }
}