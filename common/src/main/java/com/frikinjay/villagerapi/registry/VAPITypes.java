package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.villagerpack.VillagerPackCodecs;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.npc.villager.VillagerType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VAPITypes {

    private static final Map<String, Supplier<VillagerType>> DYNAMIC_TYPES = new HashMap<>();

    public static void init() { }

    public static void registerFromPack(String name, JsonObject json) {
        try {
            VillagerPackCodecs.VillagerTypeData typeData = VillagerPackCodecs.parseVillagerType(json);
            String registryName = name.replace("/", "_");

            String namespace = typeData.namespace() != null ? typeData.namespace() : VillagerAPI.MOD_ID;

            Supplier<VillagerType> type = CommonPlatformHelper.registerVillagerType(
                    namespace,
                    registryName,
                    VillagerType::new
            );

            DYNAMIC_TYPES.put(name, type);
            LOGGER.info("Registered villager type from pack: {}", name);
        } catch (Exception e) {
            LOGGER.error("Failed to register villager type from pack: {}", name, e);
        }
    }

    public static Supplier<VillagerType> getDynamicType(String name) {
        return DYNAMIC_TYPES.get(name);
    }

    public static Map<String, Supplier<VillagerType>> getDynamicTypes() {
        return new HashMap<>(DYNAMIC_TYPES);
    }
}