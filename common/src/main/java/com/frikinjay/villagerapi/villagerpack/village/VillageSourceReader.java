package com.frikinjay.villagerapi.villagerpack.village;

import com.frikinjay.villagerapi.platform.CommonPlatformHelper;
import com.frikinjay.villagerapi.villagerpack.VillagerPackLoader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

final class VillageSourceReader implements AutoCloseable {

    static final String VANILLA_ORIGIN = "vanilla";

    private final List<VillagerPackLoader.VillagerPack> packs;
    private final PackResources vanilla;
    private final List<Path> modRoots = new ArrayList<>();
    private final List<FileSystem> ownedFileSystems = new ArrayList<>();

    VillageSourceReader(List<VillagerPackLoader.VillagerPack> packs) {
        this.packs = packs;
        this.vanilla = openVanilla();

        Map<String, Path> roots = new TreeMap<>(CommonPlatformHelper.getAllModRootPaths());
        Path minecraftRoot = roots.remove("minecraft");
        for (Path root : roots.values()) {
            addRoot(root);
        }
        if (minecraftRoot != null) {
            addRoot(minecraftRoot);
        }
    }

    private static PackResources openVanilla() {
        try {
            return ServerPacksSource.createVanillaPackSource().fullResources();
        } catch (Exception e) {
            LOGGER.error("Could not open the vanilla data pack for copying villages, vanilla village types can't be used with copy_from", e);
            return null;
        }
    }

    private void addRoot(Path root) {
        try {
            if (Files.isDirectory(root)) {
                modRoots.add(root);
                return;
            }

            String fileName = root.getFileName() != null ? root.getFileName().toString() : "";
            if (Files.isRegularFile(root) && (fileName.endsWith(".jar") || fileName.endsWith(".zip"))) {
                URI uri = URI.create("jar:" + root.toUri());
                FileSystem fs;
                try {
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                    ownedFileSystems.add(fs);
                }
                modRoots.add(fs.getPath("/"));
            }
        } catch (Exception e) {
            LOGGER.debug("Could not open mod root {} for village copying", root, e);
        }
    }

    Source read(Identifier resource) {
        String relative = "data/" + resource.getNamespace() + "/" + resource.getPath();

        for (VillagerPackLoader.VillagerPack pack : packs) {
            byte[] bytes = readFile(pack.getRootPath().resolve(relative));
            if (bytes != null) return new Source(bytes, "villagerpack:" + pack.getName());
        }

        if (vanilla != null && "minecraft".equals(resource.getNamespace())) {
            try {
                IoSupplier<InputStream> supplier = vanilla.getResource(PackType.SERVER_DATA, resource);
                if (supplier != null) {
                    try (InputStream in = supplier.get()) {
                        return new Source(in.readAllBytes(), VANILLA_ORIGIN);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to read {} from the vanilla data pack", resource, e);
            }
        }

        for (Path root : modRoots) {
            byte[] bytes = readFile(root.resolve(relative));
            if (bytes != null) return new Source(bytes, "mod");
        }

        for (ClassLoader loader : new ClassLoader[] {
                VillageSourceReader.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader()
        }) {
            if (loader == null) continue;
            try (InputStream in = loader.getResourceAsStream(relative)) {
                if (in != null) return new Source(in.readAllBytes(), "classpath");
            } catch (IOException ignored) {
            }
        }

        return null;
    }

    JsonObject readJson(Identifier resource) {
        Source source = read(resource);
        if (source == null) return null;
        try {
            JsonElement element = JsonParser.parseString(new String(source.bytes(), StandardCharsets.UTF_8));
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            LOGGER.error("Failed to parse {}", resource, e);
            return null;
        }
    }

    private static byte[] readFile(Path path) {
        try {
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (IOException e) {
            LOGGER.warn("Failed to read {}", path, e);
            return null;
        }
    }

    @Override
    public void close() {
        if (vanilla != null) {
            try {
                vanilla.close();
            } catch (Exception ignored) {
            }
        }
        for (FileSystem fs : ownedFileSystems) {
            try {
                fs.close();
            } catch (IOException ignored) {
            }
        }
    }

    record Source(byte[] bytes, String origin) {}
}
