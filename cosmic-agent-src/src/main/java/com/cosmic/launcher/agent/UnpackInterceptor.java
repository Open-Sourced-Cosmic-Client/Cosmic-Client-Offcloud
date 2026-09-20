package com.cosmic.launcher.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class UnpackInterceptor {
    public static void onUnpack(Path path, ByteBuffer buffer) {
        try {
            new File("scratch").mkdirs();
            File out = new File("scratch/unpacked_client.jar");
            if (!out.exists() || out.length() < 10000000) {
                ByteBuffer dup = buffer.duplicate();
                dup.rewind();
                try (FileOutputStream fos = new FileOutputStream(out);
                     FileChannel ch = fos.getChannel()) {
                    ch.write(dup);
                }
                System.out.println("[CosmicAgent] Captured full unpacked client jar to: " + out.getAbsolutePath() + " (" + out.length() + " bytes)");
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Failed to capture unpacked jar: " + t.getMessage());
        }
    }
}
