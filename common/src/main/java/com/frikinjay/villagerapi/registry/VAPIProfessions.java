package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.villagerpack.VillagerPackCodecs;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

/**
 * Optimized professions registry using new helper classes
 */
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

    private static VillagerProfession createProfession(String registryName, String namespace, VillagerPackCodecs.ProfessionData profData) {
        Supplier<PoiType> poiTypeSupplier = VAPIPoiTypes.getDynamicPoiType(profData.poiType());

        if (poiTypeSupplier == null) {
            LOGGER.warn("POI type {} not found for profession {}. Profession will not function.",
                    profData.poiType(), registryName);
            return createInactiveProfession(registryName, namespace);
        }

        SoundEvent workSound = getWorkSound(profData.workSound(), registryName);

        return new VillagerProfession(
                Component.translatable("entity.minecraft.villager." + namespace + "." + registryName),
                holder -> holder.value().equals(poiTypeSupplier.get()),
                holder -> holder.value().equals(poiTypeSupplier.get()),
                ImmutableSet.of(),
                ImmutableSet.of(),
                workSound
        );
    }

    private static VillagerProfession createInactiveProfession(String registryName, String namespace) {
        return new VillagerProfession(
                Component.translatable("entity.minecraft.villager." + namespace + "." + registryName),
                holder -> false,
                holder -> false,
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_ARMORER
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

    /**
     * Validate all registered professions after POI types are loaded
     */
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