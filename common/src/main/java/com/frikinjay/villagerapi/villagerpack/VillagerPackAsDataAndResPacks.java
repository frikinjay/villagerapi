package com.frikinjay.villagerapi.villagerpack;

import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackAsDataAndResPacks implements PackResources {

    private final Path rootPath;
    private final String packName;
    private final PackLocationInfo locationInfo;
    private final Map<String, Path> namespacePaths = new HashMap<>();

    public VillagerPackAsDataAndResPacks(Path rootPath, String packName) {
        this.rootPath = rootPath;
        this.packName = packName;
        this.locationInfo = new PackLocationInfo(
                "villagerpack:" + packName,
                Component.literal("Villager Pack: " + packName),
                PackSource.BUILT_IN,
                java.util.Optional.empty()
        );

        indexNamespaces();
    }

    private void indexNamespaces() {
        Path dataPath = rootPath.resolve("data");
        if (!Files.exists(dataPath)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataPath)) {
            for (Path namespacePath : stream) {
                if (Files.isDirectory(namespacePath)) {
                    String namespace = namespacePath.getFileName().toString();
                    namespacePaths.put(namespace, namespacePath);
                }
            }
            LOGGER.info("Indexed {} namespaces in villagerpack: {}", namespacePaths.size(), packName);
        } catch (IOException e) {
            LOGGER.error("Failed to index namespaces for pack: {}", packName, e);
        }
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        Path filePath = rootPath;
        for (String path : paths) {
            filePath = filePath.resolve(path);
        }

        Path finalPath = filePath;
        if (Files.exists(finalPath)) {
            return () -> Files.newInputStream(finalPath);
        }

        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType packType, Identifier location) {
        String typeFolder = packType == PackType.CLIENT_RESOURCES ? "assets" : "data";
        Path resourcePath = rootPath
                .resolve(typeFolder)
                .resolve(location.getNamespace())
                .resolve(location.getPath());

        if (Files.exists(resourcePath)) {
            return () -> Files.newInputStream(resourcePath);
        }

        return null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, ResourceOutput resourceOutput) {
        String typeFolder = packType == PackType.CLIENT_RESOURCES ? "assets" : "data";
        Path basePath = rootPath.resolve(typeFolder).resolve(namespace).resolve(path);

        if (!Files.exists(basePath)) {
            return;
        }

        try {
            Files.walk(basePath)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        Path relativePath = rootPath.resolve(typeFolder).resolve(namespace).relativize(file);
                        String resourcePath = relativePath.toString().replace('\\', '/');
                        Identifier loc =
                                Identifier.fromNamespaceAndPath(namespace, resourcePath);

                        IoSupplier<InputStream> supplier = () -> Files.newInputStream(file);
                        resourceOutput.accept(loc, supplier);
                    });
        } catch (IOException e) {
            LOGGER.error("Failed to list resources for {}/{} in pack {}", namespace, path, packName, e);
        }
    }

    @Override
    public @NotNull Set<String> getNamespaces(PackType packType) {
        String typeFolder = packType == PackType.CLIENT_RESOURCES ? "assets" : "data";
        Path typePath = rootPath.resolve(typeFolder);

        if (!Files.exists(typePath)) {
            return Set.of();
        }

        Set<String> namespaces = new java.util.HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(typePath)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    namespaces.add(path.getFileName().toString());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to get namespaces for pack: {}", packName, e);
        }
        return namespaces;
    }

    @Override
    public @Nullable <T> T getMetadataSection(MetadataSectionType<T> metadataType) {
        Path metaFile = rootPath.resolve("pack.mcmeta");
        if (!Files.exists(metaFile)) {
            return null;
        }

        try (InputStream stream = Files.newInputStream(metaFile)) {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)
            ).getAsJsonObject();

            if (!json.has(metadataType.name())) {
                return null;
            }

            com.google.gson.JsonElement section = json.get(metadataType.name());

            return metadataType.codec()
                    .parse(JsonOps.INSTANCE, section)
                    .resultOrPartial(error -> LOGGER.error("Failed to parse metadata section '{}' for pack {}: {}",
                            metadataType.name(), packName, error))
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.error("Failed to read pack.mcmeta for pack: {}", packName, e);
            return null;
        }
    }

    @Override
    public @NotNull PackLocationInfo location() {
        return locationInfo;
    }

    @Override
    public void close() {
        // No cleanup needed - the VillagerPack itself handles closing zip filesystems
    }

    public String getPackName() {
        return packName;
    }
}