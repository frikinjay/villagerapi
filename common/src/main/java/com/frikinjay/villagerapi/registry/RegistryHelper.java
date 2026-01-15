package com.frikinjay.villagerapi.registry;

import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Centralized helper for common registry operations across all VAPI registry classes.
 * Reduces code duplication and provides consistent error handling.
 */
public class RegistryHelper {

    /**
     * Generic storage for dynamic registrations with lazy initialization
     */
    public static class DynamicRegistry<T> {
        private final Map<String, Supplier<T>> entries = new HashMap<>();
        private final String registryName;

        public DynamicRegistry(String registryName) {
            this.registryName = registryName;
        }

        public void register(String key, Supplier<T> supplier) {
            entries.put(key, supplier);
        }

        public Supplier<T> get(String key) {
            return entries.get(key);
        }

        public Map<String, Supplier<T>> getAll() {
            return new HashMap<>(entries);
        }

        public boolean contains(String key) {
            return entries.containsKey(key);
        }

        public int size() {
            return entries.size();
        }

        public String getRegistryName() {
            return registryName;
        }
    }

    /**
     * Registers entries from pack data with standardized error handling
     *
     * @param data The JSON data map from packs
     * @param registryName Name for logging
     * @param registrar Function to register a single entry
     * @param logger Logger instance
     */
    public static void registerFromPacks(
            Map<String, JsonObject> data,
            String registryName,
            BiConsumer<String, JsonObject> registrar,
            Logger logger) {

        if (data == null || data.isEmpty()) {
            return;
        }

        logger.info("Registering {} {} from packs", data.size(), registryName);

        int succeeded = 0;
        int failed = 0;

        for (Map.Entry<String, JsonObject> entry : data.entrySet()) {
            String name = entry.getKey();
            try {
                registrar.accept(name, entry.getValue());
                succeeded++;
            } catch (Exception e) {
                failed++;
                logger.error("Failed to register {} '{}': {}", registryName, name, e.getMessage(), e);
            }
        }

        if (failed > 0) {
            logger.warn("Registration summary for {}: {} succeeded, {} failed",
                    registryName, succeeded, failed);
        }
    }

    /**
     * Sanitizes a registry name from a file path
     */
    public static String sanitizeRegistryName(String name) {
        return name.replace("/", "_");
    }

    /**
     * Extracts namespace from JSON with fallback
     */
    public static String getNamespace(JsonObject json, String fallback) {
        return json.has("namespace") ? json.get("namespace").getAsString() : fallback;
    }

    /**
     * Validates a registry entry exists and logs warnings if not
     */
    public static <T> boolean validateEntry(
            String entryName,
            Supplier<T> supplier,
            String context,
            Logger logger) {

        if (supplier == null) {
            logger.warn("{}: Entry '{}' not found", context, entryName);
            return false;
        }

        try {
            T value = supplier.get();
            if (value == null) {
                logger.warn("{}: Entry '{}' resolved to null", context, entryName);
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.error("{}: Failed to resolve entry '{}': {}", context, entryName, e.getMessage());
            return false;
        }
    }
}