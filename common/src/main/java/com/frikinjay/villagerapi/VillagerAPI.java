package com.frikinjay.villagerapi;

import com.frikinjay.villagerapi.villagerpack.PackInitializationOrchestrator;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class VillagerAPI {

    public static final String MOD_ID = "villagerapi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        PackInitializationOrchestrator.initializeEarly();
    }

/*    @Deprecated
    public static Identifier getRL(String modid, String location) {
        return Identifier.fromNamespaceAndPath(modid, location);
    }*/

    public static <T> T unwrapHolder(Optional<Holder.Reference<T>> optional) {
        return optional.map(Holder.Reference::value).orElse(null);
    }
}