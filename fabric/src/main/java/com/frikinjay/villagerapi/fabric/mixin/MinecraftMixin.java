package com.frikinjay.villagerapi.fabric.mixin;

import com.frikinjay.villagerapi.villagerpack.VillagerPackRepositorySource;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.RepositorySource;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"
            ),
            index = 0
    )
    private RepositorySource[] villagerapi_addClientPackFinder(RepositorySource[] arg) {
        return ArrayUtils.add(arg, new VillagerPackRepositorySource(PackType.CLIENT_RESOURCES));
    }
}