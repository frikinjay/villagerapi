package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.mixin.GiveGiftToHeroAccessor;
import com.frikinjay.villagerapi.villagerpack.VillagerPackCodecs;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VAPIGifts {

    private static final Map<String, LazyGift> DYNAMIC_GIFTS = new HashMap<>();

    public static void init() {}

    public static void registerFromPack(String name, JsonObject json) {
        try {
            VillagerPackCodecs.GiftData giftData = VillagerPackCodecs.parseGift(json);
            LazyGift lazyGift = new LazyGift(name, giftData.profession(), giftData.lootTable());
            DYNAMIC_GIFTS.put(name, lazyGift);

            LOGGER.info("Registered gift from pack: {} (will be applied lazily)", name);
        } catch (Exception e) {
            LOGGER.error("Failed to register gift from pack: {}", name, e);
        }
    }

    public static void applyAllGifts() {
        Map<VillagerProfession, ResourceKey<LootTable>> gifts = GiveGiftToHeroAccessor.getGifts();

        DYNAMIC_GIFTS.values().forEach(lazyGift -> {
            VillagerProfession profession = lazyGift.resolveProfession();
            if (profession != null) {
                gifts.put(profession, lazyGift.getLootTable());
                LOGGER.debug("Applied gift for profession {}", lazyGift.professionName);
            }
        });

        LOGGER.info("Applied {} gifts from packs", DYNAMIC_GIFTS.size());
    }

    public static Map<String, ResourceKey<LootTable>> getDynamicGifts() {
        Map<String, ResourceKey<LootTable>> result = new HashMap<>();
        DYNAMIC_GIFTS.forEach((name, lazyGift) -> result.put(name, lazyGift.getLootTable()));
        return result;
    }

    private static class LazyGift {
        private final String name;
        private final String professionName;
        private final ResourceKey<LootTable> lootTable;
        private VillagerProfession cachedProfession;

        public LazyGift(String name, String professionName, Identifier lootTableId) {
            this.name = name;
            this.professionName = professionName;
            this.lootTable = ResourceKey.create(Registries.LOOT_TABLE, lootTableId);
        }

        public ResourceKey<LootTable> getLootTable() {
            return lootTable;
        }

        public VillagerProfession resolveProfession() {
            if (cachedProfession != null) {
                return cachedProfession;
            }

            Supplier<VillagerProfession> professionSupplier = VAPIProfessions.getDynamicProfession(professionName);
            cachedProfession = professionSupplier != null ? professionSupplier.get() :
                    VillagerAPI.unwrapHolder(BuiltInRegistries.VILLAGER_PROFESSION.get(Identifier.parse(professionName)));

            if (cachedProfession == null) {
                LOGGER.warn("Profession {} not found for gift {}", professionName, name);
            }

            return cachedProfession;
        }
    }
}