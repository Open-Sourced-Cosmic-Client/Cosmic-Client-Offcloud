package com.cosmic.launcher.agent;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public class ServerRedirectHelper {
    private static volatile boolean iconApplied = false;

    public static String maybeRedirect(String originalHost) {
        return originalHost;
    }

    public static String replaceInString(String input) {
        return input;
    }

    public static String rebrand(String input) {
        applyCosmicIcon();
        if (input == null) return input;
        if (input.contains("Cosmic Prisons")) {
            return input.replace("Cosmic Prisons", "Cosmic Client");
        }
        if (input.contains("CosmicPrisons")) {
            return input.replace("CosmicPrisons", "CosmicClient");
        }
        return input;
    }

    public static String rebrandURI(String uri) {
        return uri;
    }

    public static synchronized void applyCosmicIcon() {
        if (iconApplied) return;
        try {
            InputStream is16 = ServerRedirectHelper.class.getResourceAsStream("/cosmic-icon-16.png");
            InputStream is32 = ServerRedirectHelper.class.getResourceAsStream("/cosmic-icon-32.png");
            if (is16 == null || is32 == null) {
                return;
            }
            BufferedImage img16 = ImageIO.read(is16);
            BufferedImage img32 = ImageIO.read(is32);
            is16.close();
            is32.close();

            if (img16 != null && img32 != null) {
                ByteBuffer b16 = toByteBuffer(img16);
                ByteBuffer b32 = toByteBuffer(img32);

                Class<?> displayClass = MainMenuHelper.getDisplayClass(null);
                if (displayClass != null) {
                    Method setIconMethod = displayClass.getMethod("setIcon", ByteBuffer[].class);
                    setIconMethod.invoke(null, (Object) new ByteBuffer[] { b16, b32 });
                    iconApplied = true;
                    System.out.println("[CosmicAgent] Applied authentic old Cosmic Client icon to game window!");
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static ByteBuffer toByteBuffer(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int[] pixels = new int[width * height];
        img.getRGB(0, 0, width, height, pixels, 0, width);
        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                buf.put((byte) ((pixel >> 16) & 0xFF)); // R
                buf.put((byte) ((pixel >> 8) & 0xFF));  // G
                buf.put((byte) (pixel & 0xFF));         // B
                buf.put((byte) ((pixel >> 24) & 0xFF)); // A
            }
        }
        buf.flip();
        return buf;
    }
}
