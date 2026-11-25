package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.villagerpack.VillagerPackCodecs;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;

import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VAPIProfessions {

    private static final RegistryHelper.DynamicRegistry<VillagerProfession> PROFESSIONS =
            new RegistryHelper.DynamicRegistry<>("professions");

    public static void init() {}

    public static void registerFromPack(String name, JsonObject json) {
        try {
            VillagerPackCodecs.ProfessionData profData = VillagerPackCodecs.parseProfession(json);
            String registryName = RegistryHelper.sanitizeRegistryName(name);
            String namespace = RegistryHelper.getNamespace(json, VillagerAPI.MOD_ID);

            Supplier<VillagerProfession> profession = CommonPlatformHelper.registerProfession(
                    namespace,
                    registryName,
                    () -> createProfession(registryName, namespace, profData)
            );

            PROFESSIONS.register(name, profession);
            LOGGER.info("Registered profession from pack: {}", name);

        } catch (Exception e) {
            LOGGER.error("Failed to register profession from pack: {}", name, e);
        }
    }

    private static Int2ObjectMap<ResourceKey<TradeSet>> createTradeSetsMap(String namespace, String registryName) {
        Int2ObjectMap<ResourceKey<TradeSet>> map = new Int2ObjectOpenHashMap<>();
        for (int i = 1; i <= 5; i++) {
            ResourceKey<TradeSet> key = ResourceKey.create(
                    Registries.TRADE_SET,
                    Identifier.parse(namespace + ":" + registryName + "/level_" + i)
            );
            map.put(i, key);
        }
        return map;
    }

    private static VillagerProfession createProfession(String registryName, String namespace, VillagerPackCodecs.ProfessionData profData) {
        ResourceKey<PoiType> poiKey = VAPIPoiTypes.getPoiKey(profData.poiType());

        if (poiKey == null) {
            LOGGER.warn("POI type {} not found for profession {}. Profession will not function.",
                    profData.poiType(), registryName);
            return createInactiveProfession(registryName, namespace);
        }

        SoundEvent workSound = getWorkSound(profData.workSound(), registryName);

        Int2ObjectMap<ResourceKey<TradeSet>> tradeSets = createTradeSetsMap(namespace, registryName);

        return new VillagerProfession(
                Component.translatable("entity.minecraft.villager." + namespace + "." + registryName),
                holder -> holder.is(poiKey),
                holder -> holder.is(poiKey),
                ImmutableSet.of(),
                ImmutableSet.of(),
                workSound,
                tradeSets
        );
    }

    private static VillagerProfession createInactiveProfession(String registryName, String namespace) {
        return new VillagerProfession(
                Component.translatable("entity.minecraft.villager." + namespace + "." + registryName),
                holder -> false,
                holder -> false,
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_ARMORER,
                createTradeSetsMap(namespace, registryName)
        );
    }

    private static SoundEvent getWorkSound(Identifier soundId, String professionName) {
        SoundEvent workSound = VillagerAPI.unwrapHolder(BuiltInRegistries.SOUND_EVENT.get(soundId));

        if (workSound == null) {
            LOGGER.warn("Sound event {} not found for profession {}, using default",
                    soundId, professionName);
            return SoundEvents.VILLAGER_WORK_ARMORER;
        }

        return workSound;
    }

    public static void validateProfessions() {
        int valid = 0;
        int invalid = 0;

        for (String name : PROFESSIONS.getAll().keySet()) {
            Supplier<VillagerProfession> supplier = PROFESSIONS.get(name);

            if (RegistryHelper.validateEntry(name, supplier, "Profession validation", LOGGER)) {
                valid++;
            } else {
                invalid++;
            }
        }

        if (valid > 0 || invalid > 0) {
            LOGGER.info("Profession validation: {} valid, {} invalid", valid, invalid);
        }
    }

    public static Supplier<VillagerProfession> getDynamicProfession(String name) {
        return PROFESSIONS.get(name);
    }

    public static java.util.Map<String, Supplier<VillagerProfession>> getDynamicProfessions() {
        return PROFESSIONS.getAll();
    }
}