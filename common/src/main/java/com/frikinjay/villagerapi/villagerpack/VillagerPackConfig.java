package com.frikinjay.villagerapi.villagerpack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackConfig {
    private final String namespace;
    private final String displayName;
    private final String description;
    private final String version;
    private final String author;
    private final Identifier creativeTabIcon;

    private VillagerPackConfig(String namespace, String displayName, String description, String version, String author, Identifier creativeTabIcon) {
        this.namespace = namespace;
        this.displayName = displayName;
        this.description = description;
        this.version = version;
        this.author = author;
        this.creativeTabIcon = creativeTabIcon;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public Identifier getCreativeTabIcon() {
        return creativeTabIcon;
    }

    /**
     * Loads villagerapi_config.json from a villagerpack
     * @param rootPath The root path of the villagerpack
     * @param packName The pack's folder/file name (used as fallback)
     * @return VillagerPackConfig with loaded or default values
     */
    public static VillagerPackConfig load(Path rootPath, String packName) {
        Path configFile = rootPath.resolve("villagerapi_config.json");

        if (Files.exists(configFile)) {
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(configFile))) {
                JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();

                String namespace = config.has("namespace")
                        ? config.get("namespace").getAsString()
                        : sanitizeNamespace(packName);

                String displayName = config.has("display_name")
                        ? config.get("display_name").getAsString()
                        : packName;

                String description = config.has("description")
                        ? config.get("description").getAsString()
                        : "";

                String version = config.has("version")
                        ? config.get("version").getAsString()
                        : "1.0.0";

                String author = config.has("author")
                        ? config.get("author").getAsString()
                        : "Unknown";

                Identifier creativeTabIcon = config.has("creative_tab_icon")
                        ? Identifier.parse(config.get("creative_tab_icon").getAsString())
                        : Identifier.withDefaultNamespace("emerald");

                LOGGER.info("Loaded config for villagerpack '{}' (namespace: {})", displayName, namespace);
                return new VillagerPackConfig(namespace, displayName, description, version, author, creativeTabIcon);

            } catch (Exception e) {
                LOGGER.error("Failed to load villagerapi_config.json for pack '{}', using defaults", packName, e);
            }
        } else {
            LOGGER.debug("No villagerapi_config.json found for pack '{}', using defaults", packName);
        }

        // Return defaults with sanitized namespace
        String defaultNamespace = sanitizeNamespace(packName);
        return new VillagerPackConfig(
                defaultNamespace,
                packName,
                "",
                "1.0.0",
                "Unknown",
                Identifier.withDefaultNamespace("emerald")
        );
    }

    /**
     * Sanitizes a pack name to be a valid namespace
     * - Removes version numbers (e.g., mymod-1.0.0 -> mymod)
     * - Converts to lowercase
     * - Replaces spaces and invalid chars with underscores
     * - Removes leading/trailing underscores
     */
    private static String sanitizeNamespace(String packName) {
        // Remove common version patterns: -1.0.0, _v1.0, -mc1.21.1, etc.
        String cleaned = packName
                .replaceAll("-\\d+\\.\\d+.*$", "")        // Remove -1.0.0, -1.0.0-beta, etc.
                .replaceAll("_v?\\d+\\.\\d+.*$", "")      // Remove _v1.0.0, _1.0.0, etc.
                .replaceAll("-mc\\d+\\.\\d+.*$", "")      // Remove -mc1.21.1, etc.
                .replaceAll("\\+.*$", "")                 // Remove +fabric, +forge, etc.
                .replaceAll("-fabric$", "")               // Remove -fabric suffix
                .replaceAll("-forge$", "")                // Remove -forge suffix
                .replaceAll("-neoforge$", "");            // Remove -neoforge suffix

        return cleaned.toLowerCase()
                .replace(" ", "_")
                .replaceAll("[^a-z0-9_.-]", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");                   // Collapse multiple underscores
    }

    @Override
    public String toString() {
        return "VillagerPackConfig{" +
                "namespace='" + namespace + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", version='" + version + '\'' +
                ", author='" + author + '\'' +
                ", creativeTabIcon=" + creativeTabIcon +
                '}';
    }
}