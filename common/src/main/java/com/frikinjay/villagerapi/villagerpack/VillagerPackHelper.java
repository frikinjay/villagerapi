package com.frikinjay.villagerapi.villagerpack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VillagerPackHelper {

    public static Pack createPack(Path packPath, String packKey, String displayName, PackType packType) {
        String safeId = "villagerpack:" + packKey;

        PackLocationInfo locationInfo = new PackLocationInfo(
                safeId,
                Component.literal("Villager Pack: " + displayName),
                PackSource.DEFAULT,
                Optional.empty()
        );

        Pack.ResourcesSupplier nativeSupplier;
        if (Files.isDirectory(packPath)) {
            nativeSupplier = new PathPackResources.PathResourcesSupplier(packPath);
        } else {
            nativeSupplier = new FilePackResources.FileResourcesSupplier(packPath.toFile());
        }

        Pack.ResourcesSupplier wrappingSupplier = new Pack.ResourcesSupplier() {
            @Override
            public @NotNull PackMetadataResources openMetadata(@NotNull PackLocationInfo location) {
                return nativeSupplier.openMetadata(location);
            }

            @Override
            public @NotNull Stream<PackResources> openResources(@NotNull PackLocationInfo location, Pack.@NotNull Metadata metadata) {
                return nativeSupplier.openResources(location, metadata)
                        .map(nativeRes -> wrapWithVirtualFiles(nativeRes, packPath, packKey, packType));
            }
        };

        return Pack.readMetaAndCreate(
                locationInfo,
                wrappingSupplier,
                packType,
                new PackSelectionConfig(true, Pack.Position.TOP, false)
        );
    }

    private static PackResources wrapWithVirtualFiles(PackResources nativeRes, Path packPath, String packKey, PackType packType) {
        try (VillagerPackLoader.VillagerPack pack = new VillagerPackLoader.VillagerPack(packPath, packKey, false)) {
            String namespace = pack.getNamespace();

            Map<Identifier, byte[]> virtualFiles = packType == PackType.SERVER_DATA
                    ? VillagerPackCodecs.generateVirtualFiles(packKey, namespace, pack.getRootPath())
                    : VillagerPackCodecs.generateVirtualAssetFiles(namespace, pack.getRootPath());

            if (virtualFiles != null && !virtualFiles.isEmpty()) {
                return new VirtualVillagerPackResources(nativeRes, virtualFiles, packType);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate virtual files for pack {}", packKey, e);
        }
        return nativeRes;
    }

    public static boolean hasDataOrAssets(Path packPath) {
        return hasData(packPath) || hasAssets(packPath);
    }

    public static boolean hasData(Path packPath) {
        if (Files.isDirectory(packPath)) {
            return Files.exists(packPath.resolve("data"));
        } else if (packPath.toString().endsWith(".zip") || packPath.toString().endsWith(".jar")) {
            try (VillagerPackLoader.VillagerPack pack = new VillagerPackLoader.VillagerPack(packPath)) {
                return pack.hasData();
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    public static boolean hasAssets(Path packPath) {
        if (Files.isDirectory(packPath)) {
            return Files.exists(packPath.resolve("assets"));
        } else if (packPath.toString().endsWith(".zip") || packPath.toString().endsWith(".jar")) {
            try (VillagerPackLoader.VillagerPack pack = new VillagerPackLoader.VillagerPack(packPath)) {
                return pack.hasAssets();
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    private static class VirtualVillagerPackResources implements PackResources {
        private final PackResources delegate;
        private final Map<Identifier, byte[]> virtualFiles;
        private final PackType boundType;

        public VirtualVillagerPackResources(PackResources delegate, Map<Identifier, byte[]> virtualFiles, PackType boundType) {
            this.delegate = delegate;
            this.virtualFiles = virtualFiles;
            this.boundType = boundType;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String @NonNull ... path) {
            return delegate.getRootResource(path);
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(@NonNull PackType type, @NonNull Identifier location) {
            if (type == boundType && virtualFiles.containsKey(location)) {
                byte[] data = virtualFiles.get(location);
                return () -> new ByteArrayInputStream(data);
            }
            return delegate.getResource(type, location);
        }

        @Override
        public void listResources(@NonNull PackType type, @NonNull String namespace, @NonNull String directory, @NonNull ResourceOutput output) {
            if (type != boundType) {
                delegate.listResources(type, namespace, directory, output);
                return;
            }

            delegate.listResources(type, namespace, directory, (id, supplier) -> {
                if (!virtualFiles.containsKey(id)) {
                    output.accept(id, supplier);
                }
            });

            for (Map.Entry<Identifier, byte[]> entry : virtualFiles.entrySet()) {
                Identifier id = entry.getKey();
                if (id.getNamespace().equals(namespace) && id.getPath().startsWith(directory + "/")) {
                    byte[] data = entry.getValue();
                    output.accept(id, () -> new ByteArrayInputStream(data));
                }
            }
        }

        @Override
        public @NonNull Set<String> getNamespaces(@NonNull PackType type) {
            Set<String> namespaces = new HashSet<>(delegate.getNamespaces(type));
            if (type == boundType) {
                for (Identifier id : virtualFiles.keySet()) namespaces.add(id.getNamespace());
            }
            return namespaces;
        }

        @Override
        public <T> @Nullable T getMetadataSection(@NonNull MetadataSectionType<T> type) throws IOException {
            return delegate.getMetadataSection(type);
        }

        @Override
        public @NonNull PackLocationInfo location() {
            return delegate.location();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}