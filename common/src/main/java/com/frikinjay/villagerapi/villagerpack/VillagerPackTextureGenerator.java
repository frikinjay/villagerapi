package com.frikinjay.villagerapi.villagerpack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.frikinjay.villagerapi.VillagerAPI.LOGGER;

public final class VillagerPackTextureGenerator {

    private static final String MAP_BASE_CLASSPATH = "/assets/villagerapi/textures/map/map_base.png";
    private static final int DECORATION_OFFSET_X = 5;
    private static final int DECORATION_OFFSET_Y = 5;
    private static final float TINT_STRENGTH = 0.20f;

    private static BufferedImage mapBaseCache;

    private VillagerPackTextureGenerator() {}

    public static byte[] generateMapItemTexture(byte[] decorationPng, String mapColorHex) {
        try {
            BufferedImage tintedBase = getTintedMapBase(mapColorHex);
            if (tintedBase == null) return null;

            BufferedImage decoration = ImageIO.read(new ByteArrayInputStream(decorationPng));
            if (decoration == null) {
                LOGGER.error("Failed to decode decoration texture while generating map item texture");
                return null;
            }

            BufferedImage composite = new BufferedImage(tintedBase.getWidth(), tintedBase.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = composite.createGraphics();
            try {
                g.drawImage(tintedBase, 0, 0, null);
                g.drawImage(decoration, DECORATION_OFFSET_X, DECORATION_OFFSET_Y, null);
            } finally {
                g.dispose();
            }

            return toPngBytes(composite);
        } catch (IOException e) {
            LOGGER.error("Failed to generate composite map item texture", e);
            return null;
        }
    }

    public static byte[] getTintedMapBasePng(String mapColorHex) {
        try {
            BufferedImage tintedBase = getTintedMapBase(mapColorHex);
            return tintedBase == null ? null : toPngBytes(tintedBase);
        } catch (IOException e) {
            LOGGER.error("Failed to encode tinted map_base.png", e);
            return null;
        }
    }

    private static BufferedImage getTintedMapBase(String mapColorHex) throws IOException {
        BufferedImage base = getMapBase();
        if (base == null) return null;

        int[] rgb = parseHexColor(mapColorHex);
        if (rgb == null) {
            if (mapColorHex != null) LOGGER.warn("Invalid map_color '{}', skipping tint", mapColorHex);
            return base;
        }
        return applyTint(base, rgb[0], rgb[1], rgb[2], TINT_STRENGTH);
    }

    private static BufferedImage applyTint(BufferedImage source, int tintR, int tintG, int tintB, float strength) {
        BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                int newR = clamp(Math.round(r * (1 - strength) + tintR * strength));
                int newG = clamp(Math.round(g * (1 - strength) + tintG * strength));
                int newB = clamp(Math.round(b * (1 - strength) + tintB * strength));

                tinted.setRGB(x, y, (a << 24) | (newR << 16) | (newG << 8) | newB);
            }
        }
        return tinted;
    }

    private static int clamp(int value) {
        return Math.clamp(value, 0, 255);
    }

    private static int[] parseHexColor(String hex) {
        if (hex == null) return null;
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        if (clean.length() != 6) return null;
        try {
            int value = Integer.parseInt(clean, 16);
            return new int[] { (value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static synchronized BufferedImage getMapBase() throws IOException {
        if (mapBaseCache != null) return mapBaseCache;
        try (InputStream in = VillagerPackTextureGenerator.class.getResourceAsStream(MAP_BASE_CLASSPATH)) {
            if (in == null) {
                LOGGER.error("map_base.png not found on classpath at {}", MAP_BASE_CLASSPATH);
                return null;
            }
            mapBaseCache = ImageIO.read(in);
            return mapBaseCache;
        }
    }
}