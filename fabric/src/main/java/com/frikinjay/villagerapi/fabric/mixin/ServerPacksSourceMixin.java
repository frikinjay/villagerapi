package com.frikinjay.villagerapi.fabric.mixin;

import com.frikinjay.villagerapi.villagerpack.VillagerPackRepositorySource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerPacksSource.class)
public class ServerPacksSourceMixin {

    @ModifyArg(
            method = "createPackRepository",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"
            )
    )
    private static RepositorySource[] villagerapi_addServerPackFinder(RepositorySource[] arg) {
        return ArrayUtils.add(arg, new VillagerPackRepositorySource(PackType.SERVER_DATA));
    }
}