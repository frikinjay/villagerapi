package com.frikinjay.villagerapi;

import com.frikinjay.villagerapi.villagerpack.PackInitializationOrchestrator;
import net.minecraft.core.Holder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class VillagerAPI {

    public static final String MOD_ID = "villagerapi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        PackInitializationOrchestrator.initializeEarly();
    }

    public static <T> T unwrapHolder(Optional<Holder.Reference<T>> optional) {
        return optional.map(Holder.Reference::value).orElse(null);
    }
}