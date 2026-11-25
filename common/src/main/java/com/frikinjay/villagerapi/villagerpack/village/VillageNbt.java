package com.frikinjay.villagerapi.villagerpack.village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

final class VillageNbt {

    private VillageNbt() {}

    static CompoundTag read(byte[] bytes) throws IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
    }

    static byte[] write(CompoundTag tag) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        return out.toByteArray();
    }

    static Set<String> jigsawPools(CompoundTag template) {
        return rewriteJigsawPools(template, UnaryOperator.identity());
    }

    static Set<String> rewriteJigsawPools(CompoundTag template, UnaryOperator<String> rewriter) {
        Set<String> original = new LinkedHashSet<>();
        ListTag blocks = template.getListOrEmpty("blocks");

        for (int i = 0; i < blocks.size(); i++) {
            blocks.getCompound(i).flatMap(block -> block.getCompound("nbt")).ifPresent(nbt ->
                    nbt.getString("pool").ifPresent(pool -> {
                        original.add(pool);
                        String rewritten = rewriter.apply(pool);
                        if (!rewritten.equals(pool)) {
                            nbt.putString("pool", rewritten);
                        }
                    })
            );
        }

        return original;
    }
}
