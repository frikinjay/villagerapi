package com.frikinjay.villagerapi.registry;

import com.frikinjay.villagerapi.VillagerAPI;
import com.frikinjay.villagerapi.villagerpack.VillagerPackCodecs;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.Map;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public class VAPIStructureTags {
    private static final Map<String, TagKey<Structure>> DYNAMIC_STRUCTURE_TAGS = new HashMap<>();

    public static void init() { }

    public static void registerFromPack(String tagName, JsonObject json) {
        try {
            VillagerPackCodecs.StructureTagData tagData = VillagerPackCodecs.parseStructureTag(json);
            String registryName = tagName.replace("/", "_");

            String namespace = tagData.namespace() != null ? tagData.namespace() : VillagerAPI.MOD_ID;

            TagKey<Structure> structureTag = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(namespace, registryName));

            DYNAMIC_STRUCTURE_TAGS.put(tagName, structureTag);
            LOGGER.info("Created structure tag from pack: {}", structureTag);
        } catch (Exception e) {
            LOGGER.error("Failed to create structure tag from pack: {}", tagName, e);
        }
    }

    public static TagKey<Structure> getDynamicStructureTag(String tag) {
        return DYNAMIC_STRUCTURE_TAGS.get(tag);
    }

    public static Map<String, TagKey<Structure>> getDynamicStructureTags() {
        return new HashMap<>(DYNAMIC_STRUCTURE_TAGS);
    }
}
