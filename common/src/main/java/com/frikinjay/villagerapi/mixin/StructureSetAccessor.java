package com.frikinjay.villagerapi.mixin;

import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(StructureSet.class)
public interface StructureSetAccessor {

    @Mutable
    @Accessor("structures")
    void villagerapi$setStructures(List<StructureSet.StructureSelectionEntry> structures);
}
