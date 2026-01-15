package com.frikinjay.villagerapi.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class CommonPlatformHelper {
    @ExpectPlatform
    public static Supplier<Block> registerWorkstationBlock(String namespace, String name, Supplier<Block> type) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Supplier<Item> registerWorkstationItem(String namespace, String name, Supplier<Item> type) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Supplier<VillagerType> registerVillagerType(String namespace, String name, Supplier<VillagerType> type) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Supplier<MapDecorationType> registerMapDecorationType(String namespace, String name, Supplier<MapDecorationType> type) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Supplier<VillagerProfession> registerProfession(String namespace, String name, Supplier<VillagerProfession> profession) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Supplier<PoiType> registerPoiType(String namespace, String name, Supplier<Set<BlockState>> matchingStates) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isModLoadedMV(String id) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Path getGameDirectory() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Map<String, Path> getAllModRootPaths() {
        throw new AssertionError();
    }
}