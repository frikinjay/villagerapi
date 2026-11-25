package com.frikinjay.villagerapi.villagerpack;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.google.gson.*;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public final class VillagerPackOrderConfig {

    public static final String FILE_NAME = "villagerpacks_order.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean loaded;
    private static boolean readFailed;
    private static boolean manualOrderingEnabled;
    private static List<String> packOrder = new ArrayList<>();

    private VillagerPackOrderConfig() {}

    private static Path getFile() {
        return CommonPlatformHelper.getGameDirectory().resolve("villagerpacks").resolve(FILE_NAME);
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Path file = getFile();
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                LOGGER.error("{} is not a JSON object, manual ordering disabled for this session and the file will not be modified", FILE_NAME);
                readFailed = true;
                return;
            }

            JsonObject json = root.getAsJsonObject();

            if (json.has("manualOrderingEnabled") && json.get("manualOrderingEnabled").isJsonPrimitive()) {
                manualOrderingEnabled = json.get("manualOrderingEnabled").getAsBoolean();
            }

            if (json.has("packOrder") && json.get("packOrder").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("packOrder")) {
                    if (element.isJsonPrimitive()) {
                        String name = element.getAsString();
                        if (!packOrder.contains(name)) packOrder.add(name);
                    }
                }
            }

            LOGGER.info("Loaded {} (manual ordering: {})", FILE_NAME, manualOrderingEnabled);
        } catch (Exception e) {
            LOGGER.error("Failed to read {}, manual ordering disabled for this session and the file will not be modified", FILE_NAME, e);
            manualOrderingEnabled = false;
            packOrder = new ArrayList<>();
            readFailed = true;
        }
    }

    public static synchronized void sync(Collection<String> detectedNames) {
        ensureLoaded();

        List<String> automatic = automaticOrder(detectedNames);
        List<String> updated = new ArrayList<>();

        if (manualOrderingEnabled) {
            for (String name : packOrder) {
                if (automatic.contains(name) && !updated.contains(name)) updated.add(name);
            }
            for (String name : automatic) {
                if (!updated.contains(name)) updated.add(name);
            }
        } else {
            updated.addAll(automatic);
        }

        boolean changed = !updated.equals(packOrder) || !Files.exists(getFile());
        packOrder = updated;

        if (changed && !readFailed) {
            write();
        }
    }

    public static synchronized <T> List<T> sort(Collection<T> items, Function<T, String> nameExtractor) {
        ensureLoaded();

        List<T> sorted = new ArrayList<>(items);
        Comparator<T> byName = Comparator.comparing(nameExtractor);

        if (manualOrderingEnabled) {
            Map<String, Integer> positions = new HashMap<>();
            for (int i = 0; i < packOrder.size(); i++) {
                positions.putIfAbsent(packOrder.get(i), i);
            }
            sorted.sort(Comparator.<T>comparingInt(item -> positions.getOrDefault(nameExtractor.apply(item), Integer.MAX_VALUE))
                    .thenComparing(byName));
        } else {
            sorted.sort(byName);
        }

        return sorted;
    }

    public static synchronized boolean isManualOrderingEnabled() {
        ensureLoaded();
        return manualOrderingEnabled;
    }

    public static synchronized List<String> getPackOrder() {
        ensureLoaded();
        return new ArrayList<>(packOrder);
    }

    private static List<String> automaticOrder(Collection<String> names) {
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (!result.contains(name)) result.add(name);
        }
        Collections.sort(result);
        return result;
    }

    private static void write() {
        Path file = getFile();

        JsonObject json = new JsonObject();
        json.addProperty("manualOrderingEnabled", manualOrderingEnabled);
        JsonArray order = new JsonArray();
        packOrder.forEach(order::add);
        json.add("packOrder", order);

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Failed to write {}", FILE_NAME, e);
        }
    }
}