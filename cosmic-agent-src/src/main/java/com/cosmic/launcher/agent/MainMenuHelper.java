package com.cosmic.launcher.agent;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;

public class MainMenuHelper {
    private static int customTextureId = -1;
    private static int logoTextureId = -1;
    private static int logoImgWidth = 575;
    private static int logoImgHeight = 311;
    private static float logoUmax = 1.0f;
    private static float logoVmax = 1.0f;
    private static boolean logged = false;

    private static Object singleplayerButton = null;
    private static Object multiplayerButton = null;
    private static Object accountsButton = null;
    private static Object addAccountButton = null;
    private static Object exitButton = null;
    private static int lastArgI = (int) (20434654649837L >>> 32);
    private static char lastArgC1 = (char) ((int) ((20434654649837L << 32) >>> 48));
    private static char lastArgC2 = (char) ((int) ((20434654649837L << 48) >>> 48));
    private static double savedSingleplayerD = 0.0;
    private static double savedMultiplayerD = 0.0;
    private static double savedAccountsD = 0.0;
    private static double savedAddAccountD = 0.0;

    // Pre-cached LWJGL Display & GL11 handles to achieve 144+ FPS with 0 per-frame reflection overhead
    private static volatile boolean glInitialized = false;
    private static Method getWidthMethod = null;
    private static Method getHeightMethod = null;
    private static Method glPushAttrib = null;
    private static Method glPopAttrib = null;
    private static Method glMatrixMode = null;
    private static Method glPushMatrix = null;
    private static Method glPopMatrix = null;
    private static Method glLoadIdentity = null;
    private static Method glOrtho = null;
    private static Method glEnable = null;
    private static Method glDisable = null;
    private static Method glBindTexture = null;
    private static Method glColor4f = null;
    private static Method glBegin = null;
    private static Method glEnd = null;
    private static Method glTexCoord2f = null;
    private static Method glVertex2f = null;
    private static Method glDepthMask = null;
    private static Method glBlendFunc = null;

    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;
    private static final int GL_PROJECTION = 0x1701;
    private static final int GL_MODELVIEW = 0x1700;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_LIGHTING = 0x0B50;
    private static final int GL_ALPHA_TEST = 0x0BC0;
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_QUADS = 0x0007;
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;

    private static boolean glLogged = false;

    public static Class<?> getDisplayClass(ClassLoader cl) {
        String[] classNames = new String[] {
            "org.lwjglx.opengl.Display",
            "org.lwjgl.opengl.Display"
        };
        for (String name : classNames) {
            if (cl != null) {
                try {
                    return Class.forName(name, true, cl);
                } catch (Throwable ignored) {}
            }
            try {
                ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                if (tccl != null && tccl != cl) {
                    return Class.forName(name, true, tccl);
                }
            } catch (Throwable ignored) {}
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static int getDisplayWidth(Object screen) {
        if (getWidthMethod != null) {
            try {
                int w = ((Number) getWidthMethod.invoke(null)).intValue();
                if (w > 0) return w;
            } catch (Throwable ignored) {}
        }
        if (screen != null) {
            Class<?> curr = screen.getClass();
            while (curr != null && curr != Object.class) {
                try {
                    Field f = curr.getDeclaredField("y");
                    if (f.getType() == double.class) {
                        f.setAccessible(true);
                        double dw = f.getDouble(screen);
                        if (dw > 0) return (int) dw;
                    }
                } catch (Throwable ignored) {}
                curr = curr.getSuperclass();
            }
            try {
                Field f = screen.getClass().getField("k"); // Ev.k (width)
                int w = f.getInt(screen);
                if (w > 0) return w;
            } catch (Throwable ignored) {}
            try {
                Field f = screen.getClass().getField("width");
                int w = f.getInt(screen);
                if (w > 0) return w;
            } catch (Throwable ignored) {}
        }
        return 1280;
    }

    public static int getDisplayHeight(Object screen) {
        if (getHeightMethod != null) {
            try {
                int h = ((Number) getHeightMethod.invoke(null)).intValue();
                if (h > 0) return h;
            } catch (Throwable ignored) {}
        }
        if (screen != null) {
            try {
                Field f = screen.getClass().getField("q"); // Ev.q (height)
                int h = f.getInt(screen);
                if (h > 0) return h;
            } catch (Throwable ignored) {}
            try {
                Field f = screen.getClass().getField("height");
                int h = f.getInt(screen);
                if (h > 0) return h;
            } catch (Throwable ignored) {}
        }
        return 720;
    }

    private static synchronized void ensureGLInitialized(ClassLoader cl) {
        if (glInitialized) return;
        try {
            Class<?> displayClass = getDisplayClass(cl);
            if (displayClass != null) {
                try {
                    getWidthMethod = displayClass.getMethod("getWidth");
                    getHeightMethod = displayClass.getMethod("getHeight");
                } catch (Throwable ignored) {}
            }

            Class<?> gl11 = null;
            try {
                gl11 = Class.forName("org.lwjgl.opengl.GL11", true, cl);
            } catch (Throwable ignored) {
                try {
                    gl11 = Class.forName("org.lwjgl.opengl.GL11");
                } catch (Throwable ignored2) {
                    ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                    if (tccl != null) gl11 = Class.forName("org.lwjgl.opengl.GL11", true, tccl);
                }
            }
            if (gl11 != null) {
                glPushAttrib = gl11.getMethod("glPushAttrib", int.class);
                glPopAttrib = gl11.getMethod("glPopAttrib");
                glMatrixMode = gl11.getMethod("glMatrixMode", int.class);
                glPushMatrix = gl11.getMethod("glPushMatrix");
                glPopMatrix = gl11.getMethod("glPopMatrix");
                glLoadIdentity = gl11.getMethod("glLoadIdentity");
                glOrtho = gl11.getMethod("glOrtho", double.class, double.class, double.class, double.class, double.class, double.class);
                glEnable = gl11.getMethod("glEnable", int.class);
                glDisable = gl11.getMethod("glDisable", int.class);
                glBindTexture = gl11.getMethod("glBindTexture", int.class, int.class);
                glColor4f = gl11.getMethod("glColor4f", float.class, float.class, float.class, float.class);
                glBegin = gl11.getMethod("glBegin", int.class);
                glEnd = gl11.getMethod("glEnd");
                glTexCoord2f = gl11.getMethod("glTexCoord2f", float.class, float.class);
                glVertex2f = gl11.getMethod("glVertex2f", float.class, float.class);
                glDepthMask = gl11.getMethod("glDepthMask", boolean.class);
                glBlendFunc = gl11.getMethod("glBlendFunc", int.class, int.class);
                glInitialized = true;
                if (!glLogged) {
                    glLogged = true;
                    System.out.println("[CosmicAgent] OpenGL and Display bindings initialized (" + (displayClass != null ? displayClass.getName() : "Direct GL") + ")");
                }
            } else {
                glInitialized = true; // prevent per-frame loop
            }
        } catch (Throwable t) {
            if (!glLogged) {
                glLogged = true;
                System.err.println("[CosmicAgent] ensureGLInitialized error: " + t.getMessage());
            }
            glInitialized = true; // prevent per-frame loop
        }
    }

    /**
     * Render the custom 4K background directly using LWJGL OpenGL with 0 per-frame reflection lookups.
     */
    public static void renderCustomBackground(Object screen) {
        try {
            if (!ClientHardeningGuard.isHeartbeatValid()) return;
            ClassLoader cl = (screen != null) ? screen.getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = MainMenuHelper.class.getClassLoader();
            }
            if (isInWorld(cl)) {
                return; // Never render 4K space background over active in-game screens!
            }
            IconHelper.applyWindowIcon();
            ensureTextureLoaded(cl);
            if (customTextureId <= 0) return;

            ensureGLInitialized(cl);
            if (!glInitialized || glBegin == null) return;

            int width = getDisplayWidth(screen);
            int height = getDisplayHeight(screen);

            // Push all attributes & matrices
            glPushAttrib.invoke(null, GL_ALL_ATTRIB_BITS);

            glMatrixMode.invoke(null, GL_PROJECTION);
            glPushMatrix.invoke(null);
            glLoadIdentity.invoke(null);
            glOrtho.invoke(null, 0.0, (double) width, (double) height, 0.0, -1000.0, 1000.0);

            glMatrixMode.invoke(null, GL_MODELVIEW);
            glPushMatrix.invoke(null);
            glLoadIdentity.invoke(null);

            glDisable.invoke(null, GL_LIGHTING);
            glDisable.invoke(null, GL_DEPTH_TEST);
            glDepthMask.invoke(null, false);
            glDisable.invoke(null, GL_CULL_FACE);
            glDisable.invoke(null, GL_ALPHA_TEST);
            glDisable.invoke(null, GL_BLEND);
            glEnable.invoke(null, GL_TEXTURE_2D);

            glBindTexture.invoke(null, GL_TEXTURE_2D, customTextureId);
            glColor4f.invoke(null, 1.0f, 1.0f, 1.0f, 1.0f);

            // Draw full-screen quad covering exact display pixels
            glBegin.invoke(null, GL_QUADS);
            glTexCoord2f.invoke(null, 0.0f, 0.0f); glVertex2f.invoke(null, 0.0f, 0.0f);
            glTexCoord2f.invoke(null, 0.0f, 1.0f); glVertex2f.invoke(null, 0.0f, (float) height);
            glTexCoord2f.invoke(null, 1.0f, 1.0f); glVertex2f.invoke(null, (float) width, (float) height);
            glTexCoord2f.invoke(null, 1.0f, 0.0f); glVertex2f.invoke(null, (float) width, 0.0f);
            glEnd.invoke(null);

            glMatrixMode.invoke(null, GL_PROJECTION);
            glPopMatrix.invoke(null);
            glMatrixMode.invoke(null, GL_MODELVIEW);
            glPopMatrix.invoke(null);

            glPopAttrib.invoke(null);
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Error in renderCustomBackground: " + t.getMessage());
        }
    }

    /**
     * Render the authentic Cosmic Client Logo centered at the top of the screen.
     * Called at the conclusion of drawScreen to guarantee it is never occluded.
     */
    public static void renderCustomLogo(Object screen) {
        try {
            ClassLoader cl = (screen != null) ? screen.getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = MainMenuHelper.class.getClassLoader();
            }
            ensureLogoLoaded(cl);
            if (logoTextureId <= 0) return;

            ensureGLInitialized(cl);
            if (!glInitialized || glBegin == null) return;

            int width = getDisplayWidth(screen);
            int height = getDisplayHeight(screen);

            glPushAttrib.invoke(null, GL_ALL_ATTRIB_BITS);

            glMatrixMode.invoke(null, GL_PROJECTION);
            glPushMatrix.invoke(null);
            glLoadIdentity.invoke(null);
            glOrtho.invoke(null, 0.0, (double) width, (double) height, 0.0, -1000.0, 1000.0);

            glMatrixMode.invoke(null, GL_MODELVIEW);
            glPushMatrix.invoke(null);
            glLoadIdentity.invoke(null);

            glDisable.invoke(null, GL_LIGHTING);
            glDisable.invoke(null, GL_DEPTH_TEST);
            glDepthMask.invoke(null, false);
            glDisable.invoke(null, GL_CULL_FACE);
            glDisable.invoke(null, GL_ALPHA_TEST);
            glEnable.invoke(null, GL_BLEND);
            glBlendFunc.invoke(null, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glEnable.invoke(null, GL_TEXTURE_2D);

            glBindTexture.invoke(null, GL_TEXTURE_2D, logoTextureId);
            glColor4f.invoke(null, 1.0f, 1.0f, 1.0f, 1.0f);

            float targetLogoWidth = Math.min((float) width * 0.40f, 440.0f);
            if (targetLogoWidth < 260.0f && width >= 300) targetLogoWidth = Math.min((float) width * 0.75f, 320.0f);
            float targetLogoHeight = targetLogoWidth * ((float) logoImgHeight / (float) logoImgWidth);
            float logoX = ((float) width - targetLogoWidth) / 2.0f;
            float logoY = Math.max(16.0f, (float) height * 0.07f);

            glBegin.invoke(null, GL_QUADS);
            glTexCoord2f.invoke(null, 0.0f, 0.0f); glVertex2f.invoke(null, logoX, logoY);
            glTexCoord2f.invoke(null, 0.0f, logoVmax); glVertex2f.invoke(null, logoX, logoY + targetLogoHeight);
            glTexCoord2f.invoke(null, logoUmax, logoVmax); glVertex2f.invoke(null, logoX + targetLogoWidth, logoY + targetLogoHeight);
            glTexCoord2f.invoke(null, logoUmax, 0.0f); glVertex2f.invoke(null, logoX + targetLogoWidth, logoY);
            glEnd.invoke(null);

            glMatrixMode.invoke(null, GL_PROJECTION);
            glPopMatrix.invoke(null);
            glMatrixMode.invoke(null, GL_MODELVIEW);
            glPopMatrix.invoke(null);

            glPopAttrib.invoke(null);
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Error in renderCustomLogo: " + t.getMessage());
        }
    }

    private static synchronized void ensureLogoLoaded(ClassLoader cl) {
        if (logoTextureId > 0) return;
        try {
            BufferedImage img = loadLogoImage();
            if (img == null) {
                System.err.println("[CosmicAgent] Could not find Cosmic Client logo image!");
                return;
            }
            logoImgWidth = img.getWidth();
            logoImgHeight = img.getHeight();

            // Use Power-of-Two (1024x512) buffer to ensure complete OpenGL compatibility across all GPUs
            int potW = 1024;
            int potH = 512;
            BufferedImage potImage = new BufferedImage(potW, potH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = potImage.createGraphics();
            g2.drawImage(img, 0, 0, null);
            g2.dispose();

            logoUmax = (float) logoImgWidth / (float) potW;
            logoVmax = (float) logoImgHeight / (float) potH;

            int[] pixels = new int[potW * potH];
            potImage.getRGB(0, 0, potW, potH, pixels, 0, potW);

            ByteBuffer buffer = ByteBuffer.allocateDirect(potW * potH * 4).order(ByteOrder.nativeOrder());
            for (int y = 0; y < potH; y++) {
                for (int x = 0; x < potW; x++) {
                    int pixel = pixels[y * potW + x];
                    buffer.put((byte) ((pixel >> 16) & 0xFF));
                    buffer.put((byte) ((pixel >> 8) & 0xFF));
                    buffer.put((byte) (pixel & 0xFF));
                    buffer.put((byte) ((pixel >> 24) & 0xFF));
                }
            }
            buffer.flip();

            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11", true, cl);
            Method glGenTextures = gl11.getMethod("glGenTextures");
            Method glBindTexture = gl11.getMethod("glBindTexture", int.class, int.class);
            Method glTexParameteri = gl11.getMethod("glTexParameteri", int.class, int.class, int.class);
            Method glTexImage2D = gl11.getMethod("glTexImage2D", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, ByteBuffer.class);

            int GL_TEXTURE_2D = 0x0DE1;
            int GL_TEXTURE_MIN_FILTER = 0x2801;
            int GL_TEXTURE_MAG_FILTER = 0x2800;
            int GL_TEXTURE_WRAP_S = 0x2802;
            int GL_TEXTURE_WRAP_T = 0x2803;
            int GL_CLAMP_TO_EDGE = 0x812F;
            int GL_LINEAR = 0x2601;
            int GL_RGBA = 0x1908;
            int GL_UNSIGNED_BYTE = 0x1401;

            int texId = ((Number) glGenTextures.invoke(null)).intValue();
            glBindTexture.invoke(null, GL_TEXTURE_2D, texId);
            glTexParameteri.invoke(null, GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri.invoke(null, GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            try {
                glTexParameteri.invoke(null, GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri.invoke(null, GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            } catch (Throwable ignored) {}
            glTexImage2D.invoke(null, GL_TEXTURE_2D, 0, GL_RGBA, potW, potH, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

            logoTextureId = texId;
            System.out.println("[CosmicAgent] Custom Cosmic Client Main Menu Logo loaded successfully (POT 1024x512) into GL Texture ID: " + texId);
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Failed to upload logo texture: " + t.getMessage());
        }
    }

    private static BufferedImage loadLogoImage() {
        try {
            String appData = System.getenv("APPDATA");
            List<File> candidates = new ArrayList<>();
            if (appData != null) {
                candidates.add(new File(appData, ".minecraft/cosmic/logo.png"));
            }
            candidates.add(new File("CosmicClient-x64/logo.png"));
            candidates.add(new File("../CosmicClient-x64/logo.png"));
            candidates.add(new File("scratch/cosmic_client_logo_transparent.png"));
            candidates.add(new File("scratch/cosmic_client_logo_perfect.png"));
            candidates.add(new File("logo.png"));

            for (File f : candidates) {
                if (f.exists() && f.length() > 0) {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) {
                        System.out.println("[CosmicAgent] Loaded logo from: " + f.getAbsolutePath());
                        return img;
                    }
                }
            }

            InputStream res = MainMenuHelper.class.getResourceAsStream("/logo.png");
            if (res == null) res = MainMenuHelper.class.getResourceAsStream("/assets/minecraft/cosmic/textures/logo.png");
            if (res != null) {
                try {
                    return ImageIO.read(res);
                } finally {
                    res.close();
                }
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] loadLogoImage error: " + t.getMessage());
        }
        return null;
    }

    private static synchronized void ensureTextureLoaded(ClassLoader cl) {
        if (customTextureId > 0) return;
        try {
            BufferedImage img = loadBackgroundImage();
            if (img == null) {
                System.err.println("[CosmicAgent] Could not find any background image on disk or classpath!");
                return;
            }

            int w = img.getWidth();
            int h = img.getHeight();
            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);

            ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixel = pixels[y * w + x];
                    buffer.put((byte) ((pixel >> 16) & 0xFF));
                    buffer.put((byte) ((pixel >> 8) & 0xFF));
                    buffer.put((byte) (pixel & 0xFF));
                    buffer.put((byte) ((pixel >> 24) & 0xFF));
                }
            }
            buffer.flip();

            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11", true, cl);
            Method glGenTextures = gl11.getMethod("glGenTextures");
            Method glBindTexture = gl11.getMethod("glBindTexture", int.class, int.class);
            Method glTexParameteri = gl11.getMethod("glTexParameteri", int.class, int.class, int.class);
            Method glTexImage2D = gl11.getMethod("glTexImage2D", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, ByteBuffer.class);

            int GL_TEXTURE_2D = 0x0DE1;
            int GL_TEXTURE_MIN_FILTER = 0x2801;
            int GL_TEXTURE_MAG_FILTER = 0x2800;
            int GL_TEXTURE_WRAP_S = 0x2802;
            int GL_TEXTURE_WRAP_T = 0x2803;
            int GL_CLAMP_TO_EDGE = 0x812F;
            int GL_LINEAR = 0x2601;
            int GL_RGBA = 0x1908;
            int GL_UNSIGNED_BYTE = 0x1401;

            int texId = ((Number) glGenTextures.invoke(null)).intValue();
            glBindTexture.invoke(null, GL_TEXTURE_2D, texId);
            glTexParameteri.invoke(null, GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri.invoke(null, GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            try {
                glTexParameteri.invoke(null, GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri.invoke(null, GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            } catch (Throwable ignored) {}
            glTexImage2D.invoke(null, GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

            customTextureId = texId;
            System.out.println("[CosmicAgent] Custom CosmicClient-TEST 4K Background loaded successfully into GL Texture ID: " + texId);
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Failed to upload custom background texture: " + t.getMessage());
        }
    }

    private static BufferedImage loadBackgroundImage() {
        try {
            String appData = System.getenv("APPDATA");
            List<File> candidates = new ArrayList<>();
            if (appData != null) {
                candidates.add(new File(appData, ".minecraft/cosmic/background.jpg"));
                candidates.add(new File(appData, ".minecraft/cosmic/background.png"));
                candidates.add(new File(appData, ".minecraft/cosmic/mainmenu/background.jpg"));
            }
            candidates.add(new File("CosmicClient-x64/background.jpg"));
            candidates.add(new File("CosmicClient-x64/cosmic/background.jpg"));
            candidates.add(new File("../CosmicClient-x64/background.jpg"));
            candidates.add(new File("background.jpg"));
            candidates.add(new File("background.png"));

            for (File f : candidates) {
                if (f.exists() && f.length() > 0) {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) {
                        System.out.println("[CosmicAgent] Loaded background image from: " + f.getAbsolutePath());
                        return img;
                    }
                }
            }

            InputStream res = MainMenuHelper.class.getResourceAsStream("/background.jpg");
            if (res == null) res = MainMenuHelper.class.getResourceAsStream("/assets/minecraft/cosmic/textures/mainmenu/background.jpg");
            if (res == null) res = MainMenuHelper.class.getResourceAsStream("/assets/minecraft/cosmic/textures/background.jpg");
            if (res != null) {
                try {
                    BufferedImage img = ImageIO.read(res);
                    if (img != null) {
                        System.out.println("[CosmicAgent] Loaded background image from classpath resource");
                        return img;
                    }
                } finally {
                    res.close();
                }
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] loadBackgroundImage error: " + t.getMessage());
        }
        return null;
    }

    public static Class<?> resolveClientClass(String name, ClassLoader cl) {
        if (cl == null) cl = MainMenuHelper.class.getClassLoader();
        String[] prefixes = new String[] { "cosmicclient.", "net.minecraft.client.", "net.minecraft.", "" };
        for (String p : prefixes) {
            try {
                return Class.forName(p + name, true, cl);
            } catch (Throwable ignored) {}
        }
        try {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            if (tccl != null && tccl != cl) {
                for (String p : prefixes) {
                    try {
                        return Class.forName(p + name, true, tccl);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static volatile boolean fullClientModeEnsured = false;

    /**
     * Ensures Cosmic Client initializes in authentic full client mode (ug.e = false)
     * so Ea naturally creates Singleplayer (kd), Multiplayer (km), Options (ka),
     * and Accounts (k9) in L and stores k9 in Q.
     */
    public static void ensureFullClientMode() {
        if (fullClientModeEnsured) return;
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = MainMenuHelper.class.getClassLoader();
            Class<?> ugClass = resolveClientClass("ug", cl);
            if (ugClass != null) {
                for (Field f : ugClass.getDeclaredFields()) {
                    if (f.getType() == boolean.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        if (f.getName().equals("e")) {
                            f.setAccessible(true);
                            f.setBoolean(null, false);
                            fullClientModeEnsured = true;
                            System.out.println("[CosmicAgent] ensureFullClientMode: ug.e = false (Authentic full client mode enabled)");
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] ensureFullClientMode error: " + t.getMessage());
        }
    }

    private static Object lastConfiguredEaForButtons = null;

    /**
     * Set up Main Menu buttons:
     * - Removes Exit Game button completely
     * - Keeps Singleplayer, Multiplayer, Options
     * - Moves Accounts to the bottom
     * - Links eaInstance.Q to accounts button for drawer alignment
     */
    public static synchronized void setupMainMenuButtons(Object eaInstance) {
        if (eaInstance == null) return;
        try {
            Field lField = eaInstance.getClass().getDeclaredField("L");
            lField.setAccessible(true);
            List<?> lVal = (List<?>) lField.get(eaInstance);
            if (lVal == null || eaInstance == lastConfiguredEaForButtons) {
                return;
            }

            ensureFullClientMode();

            exitButton = null;
            Object accountsBtn = null;
            List<Object> expanded = new ArrayList<>();
            for (Object btn : lVal) {
                if (btn == null) continue;
                String label = getButtonLabel(btn);
                if (label != null && (label.toUpperCase().contains("EXIT") || label.toUpperCase().contains("QUIT"))) {
                    continue; // Completely remove Exit / Quit button
                }
                if (label != null && label.toUpperCase().contains("ACCOUNT")) {
                    accountsBtn = btn;
                    accountsButton = btn;
                    continue;
                }
                expanded.add(btn);
                if (label != null) {
                    if (label.equalsIgnoreCase("SINGLEPLAYER") || label.equalsIgnoreCase("SINGLE PLAYER")) {
                        singleplayerButton = btn;
                    } else if (label.equalsIgnoreCase("MULTIPLAYER")) {
                        multiplayerButton = btn;
                    }
                }
            }

            // Move Accounts to the very bottom
            if (accountsBtn != null) {
                expanded.add(accountsBtn);
                accountsButton = accountsBtn;
                try {
                    Field qField = eaInstance.getClass().getDeclaredField("Q");
                    qField.setAccessible(true);
                    qField.set(eaInstance, accountsBtn);
                } catch (Throwable ignored) {}
            }

            lField.set(eaInstance, expanded);
            lastConfiguredEaForButtons = eaInstance;

            System.out.println("[CosmicAgent] setupMainMenuButtons: Clean menu arranged (Exit removed, Accounts at bottom): [Singleplayer, Multiplayer, Options, Accounts] (Total buttons: " + expanded.size() + ")");
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] setupMainMenuButtons error: " + t.getMessage());
        }
    }

    private static String getButtonLabel(Object btn) {
        if (btn == null) return null;
        Class<?> curr = btn.getClass();
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                if (f.getType() == String.class) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(btn);
                        if (val instanceof String) {
                            return (String) val;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    private static void setButtonLabel(Object btn, String label) {
        if (btn == null || label == null) return;
        Class<?> curr = btn.getClass();
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                if (f.getType() == String.class) {
                    f.setAccessible(true);
                    try {
                        f.set(btn, label);
                    } catch (Throwable ignored) {}
                }
            }
            curr = curr.getSuperclass();
        }
    }

    private static boolean isButtonHovered(Object btn, double mouseX, double mouseY) {
        if (btn == null) return false;
        try {
            Method cMethod = null;
            Class<?> curr = btn.getClass();
            while (curr != null && curr != Object.class && cMethod == null) {
                try {
                    cMethod = curr.getDeclaredMethod("c", double.class, double.class);
                } catch (NoSuchMethodException e) {
                    curr = curr.getSuperclass();
                }
            }
            if (cMethod != null) {
                cMethod.setAccessible(true);
                return Boolean.TRUE.equals(cMethod.invoke(btn, mouseX, mouseY));
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void playClickSound(Object eaInstance) {
        try {
            for (Method m : eaInstance.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() == 3 && m.getParameterTypes()[0] == int.class && m.getParameterTypes()[1] == int.class && m.getParameterTypes()[2] == char.class) {
                    m.setAccessible(true);
                    m.invoke(eaInstance, 0, 0, (char) 0);
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void displayScreen(Object eaInstance, Object screen) {
        displayScreen(eaInstance, screen, lastArgI, lastArgC1, lastArgC2);
    }

    public static void displayScreen(Object eaInstance, Object screen, int argI, char argC1, char argC2) {
        if (screen == null) return;
        try {
            ClassLoader cl = eaInstance != null ? eaInstance.getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = MainMenuHelper.class.getClassLoader();

            Class<?> evClass = resolveClientClass("Ev", cl);
            Class<?> wbClass = resolveClientClass("wb", cl);

            if (wbClass != null && evClass != null && evClass.isAssignableFrom(screen.getClass())) {
                Object wb = wbClass.getMethod("ap").invoke(null);
                if (wb != null) {
                    // 1. Try authentic native screen transition wb.b(Ev, int, char, char)
                    try {
                        Method bMethod = wbClass.getMethod("b", evClass, int.class, char.class, char.class);
                        bMethod.setAccessible(true);
                        bMethod.invoke(wb, screen, argI, argC1, argC2);
                        System.out.println("[CosmicAgent] Successfully displayed screen via wb.b: " + screen.getClass().getName());
                        return;
                    } catch (Throwable t) {
                        System.err.println("[CosmicAgent] wb.b call failed, using direct screen resolution fallback: " + t.getMessage());
                    }

                    // 2. Direct screen resolution fallback
                    displayScreenDirect(wb, screen, cl);
                    return;
                }
            }

            // Fallback for j5 screens (vanilla 1.8 l8)
            try {
                Class<?> j5Class = resolveClientClass("j5", cl);
                Class<?> l8Class = resolveClientClass("l8", cl);
                if (j5Class != null && l8Class != null && j5Class.isAssignableFrom(screen.getClass())) {
                    Object mc = l8Class.getMethod("w").invoke(null);
                    if (mc != null) {
                        for (Method m : l8Class.getMethods()) {
                            if (m.getParameterCount() == 4 && m.getParameterTypes()[2] == j5Class) {
                                m.setAccessible(true);
                                m.invoke(mc, 0, (short) 0, screen, 0);
                                System.out.println("[CosmicAgent] Displayed j5 screen: " + screen.getClass().getName());
                                return;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            System.err.println("[CosmicAgent] displayScreen error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static void displayScreenDirect(Object wb, Object screen, ClassLoader cl) {
        try {
            Class<?> wbClass = wb.getClass();
            Class<?> evClass = resolveClientClass("Ev", cl);

            // Set active screen on wb: wb.ak = screen
            try {
                Field akField = wbClass.getDeclaredField("ak");
                akField.setAccessible(true);
                akField.set(wb, screen);
            } catch (Throwable ignored) {}

            // Set screen.l = wb
            try {
                Field lField = evClass.getDeclaredField("l");
                lField.setAccessible(true);
                lField.set(screen, wb);
            } catch (Throwable ignored) {
                try {
                    Field lField = screen.getClass().getField("l");
                    lField.setAccessible(true);
                    lField.set(screen, wb);
                } catch (Throwable ignored2) {}
            }

            // Post event & notify wb listener
            try {
                wbClass.getMethod("a", evClass).invoke(wb, screen);
            } catch (Throwable ignored) {}

            // Calculate scaled resolution
            int scaledW = 960;
            int scaledH = 540;
            try {
                Class<?> gdClass = resolveClientClass("Gd", cl);
                if (gdClass != null) {
                    Constructor<?> gdCtor = gdClass.getConstructor(wbClass);
                    Object gd = gdCtor.newInstance(wb);
                    try {
                        Class<?> ugClass = resolveClientClass("ug", cl);
                        if (ugClass != null) {
                            Method ugA = ugClass.getMethod("a", gdClass);
                            ugA.invoke(null, gd);
                        }
                    } catch (Throwable ignored) {}
                    scaledW = (Integer) gdClass.getMethod("f").invoke(gd);
                    scaledH = (Integer) gdClass.getMethod("a").invoke(gd);
                }
            } catch (Throwable ignored) {}

            // Set width & height on screen
            try {
                Field kField = screen.getClass().getField("k");
                kField.setInt(screen, scaledW);
            } catch (Throwable ignored) {
                try {
                    Field kField = evClass.getDeclaredField("k");
                    kField.setAccessible(true);
                    kField.setInt(screen, scaledW);
                } catch (Throwable ignored2) {}
            }
            try {
                Field qField = screen.getClass().getField("q");
                qField.setInt(screen, scaledH);
            } catch (Throwable ignored) {
                try {
                    Field qField = evClass.getDeclaredField("q");
                    qField.setAccessible(true);
                    qField.setInt(screen, scaledH);
                } catch (Throwable ignored2) {}
            }

            // Clear existing buttons in screen.i
            try {
                Field iField = evClass.getDeclaredField("i");
                iField.setAccessible(true);
                Object iObj = iField.get(screen);
                if (iObj instanceof List) {
                    ((List<?>) iObj).clear();
                }
            } catch (Throwable ignored) {
                try {
                    Field iField = screen.getClass().getField("i");
                    iField.setAccessible(true);
                    Object iObj = iField.get(screen);
                    if (iObj instanceof List) {
                        ((List<?>) iObj).clear();
                    }
                } catch (Throwable ignored2) {}
            }

            // Ungrab mouse focus: wb.e = false
            try {
                Field eField = wbClass.getDeclaredField("e");
                eField.setAccessible(true);
                eField.setBoolean(wb, false);
            } catch (Throwable ignored) {}

            System.out.println("[CosmicAgent] Successfully displayed Ev screen directly: " + screen.getClass().getName() + " (" + scaledW + "x" + scaledH + ")");
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] displayScreenDirect error: " + t.getMessage());
        }
    }

    public static void handleKdButtonClick(Object kdInstance) {
        handleKdButtonClick(kdInstance, 0, 0, (byte) 0);
    }

    /**
     * Primary click hook invoked directly when any menu button (kd) is clicked in kd.b(int, int, byte).
     * Dispatches MULTIPLAYER, SINGLEPLAYER, ACCOUNTS, and EXIT GAME cleanly.
     */
    public static void handleKdButtonClick(Object kdInstance, int i1, int i2, byte b3) {
        if (kdInstance == null) return;
        try {
            ClassLoader cl = kdInstance.getClass().getClassLoader();
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();

            // Compute authentic obfuscated arguments for wb.b
            long l4 = ((long) i1 << 32) | (((long) i2 << 40) >>> 32) | (((long) b3 << 56) >>> 56);
            long val = l4 ^ 20434654649837L;
            int argI = (int) (val >>> 32);
            char argC1 = (char) ((int) ((val << 32) >>> 48));
            char argC2 = (char) ((int) ((val << 48) >>> 48));
            lastArgI = argI;
            lastArgC1 = argC1;
            lastArgC2 = argC2;

            // 1. Resolve Ea instance from field 'o'
            Object eaInstance = null;
            try {
                Field oField = kdInstance.getClass().getDeclaredField("o");
                oField.setAccessible(true);
                eaInstance = oField.get(kdInstance);
            } catch (Throwable t) {
                for (Field f : kdInstance.getClass().getDeclaredFields()) {
                    if (f.getType().getName().endsWith("Ea")) {
                        f.setAccessible(true);
                        eaInstance = f.get(kdInstance);
                        break;
                    }
                }
            }

            // 2. Play click sound
            if (eaInstance != null) {
                playClickSound(eaInstance);
            }

            // 3. Resolve button label
            String label = getButtonLabel(kdInstance);
            System.out.println("[CosmicAgent] handleKdButtonClick: Button clicked (label='" + label + "', instance=" + kdInstance.getClass().getName() + ")");

            boolean isExit = (kdInstance == exitButton) || (label != null && (label.toUpperCase().contains("EXIT") || label.toUpperCase().contains("QUIT")));
            boolean isMp = (kdInstance == multiplayerButton) || (label != null && label.toUpperCase().contains("MULTIPLAYER"));
            boolean isSp = (kdInstance == singleplayerButton) || (label != null && (label.toUpperCase().contains("SINGLEPLAYER") || label.toUpperCase().contains("SINGLE PLAYER")));

            if (isExit) {
                System.out.println("[CosmicAgent] Main Menu EXIT GAME button clicked! Exiting cleanly...");
                try {
                    Class<?> wbClass = resolveClientClass("wb", cl);
                    if (wbClass != null) {
                        Object wb = wbClass.getMethod("ap").invoke(null);
                        if (wb != null) {
                            wbClass.getMethod("c").invoke(wb);
                        }
                    }
                } catch (Throwable ignored) {}
                System.exit(0);
                return;
            }

            if (isMp) {
                if (!InGameLoginHelper.isCurrentAccountMicrosoft()) {
                    System.out.println("[CosmicAgent] Multiplayer requires a Microsoft account! Prompting Microsoft login (Es)...");
                    Class<?> esClass = resolveClientClass("Es", cl);
                    Class<?> egClass = resolveClientClass("EG", cl);
                    if (esClass != null && egClass != null) {
                        Constructor<?> esCtor = esClass.getDeclaredConstructor(egClass);
                        esCtor.setAccessible(true);
                        Object esScreen = esCtor.newInstance(eaInstance);
                        displayScreen(eaInstance, esScreen, argI, argC1, argC2);
                        return;
                    }
                    return;
                }
                System.out.println("[CosmicAgent] Opening Multiplayer Server List (cosmicclient.FD)...");
                Class<?> fdClass = resolveClientClass("FD", cl);
                Class<?> evClass = resolveClientClass("Ev", cl);
                if (fdClass != null && evClass != null) {
                    Constructor<?> fdCtor = fdClass.getDeclaredConstructor(evClass);
                    fdCtor.setAccessible(true);
                    Object fdScreen = fdCtor.newInstance(eaInstance);
                    displayScreen(eaInstance, fdScreen, argI, argC1, argC2);
                    return;
                }
            }

            if (isSp) {
                System.out.println("[CosmicAgent] Opening Singleplayer world selection (cosmicclient.Fc)...");
                Class<?> fcClass = resolveClientClass("Fc", cl);
                Class<?> evClass = resolveClientClass("Ev", cl);
                if (fcClass != null && evClass != null) {
                    long valFc = l4 ^ 53260612200332L;
                    int fcArgI = (int) (valFc >>> 32);
                    char fcArgC1 = (char) ((int) ((valFc << 32) >>> 48));
                    char fcArgC2 = (char) ((int) ((valFc << 48) >>> 48));

                    Constructor<?> fcCtor = null;
                    try {
                        fcCtor = fcClass.getDeclaredConstructor(evClass, int.class, char.class, char.class);
                    } catch (Throwable t) {
                        for (Constructor<?> c : fcClass.getDeclaredConstructors()) {
                            if (c.getParameterCount() == 4) {
                                fcCtor = c;
                                break;
                            }
                        }
                    }
                    if (fcCtor != null) {
                        fcCtor.setAccessible(true);
                        Object fcScreen = fcCtor.newInstance(eaInstance, fcArgI, fcArgC1, fcArgC2);
                        displayScreen(eaInstance, fcScreen, argI, argC1, argC2);
                        return;
                    }
                }
            }

            // Default fallback: Singleplayer (Fc)
            if (eaInstance != null) {
                Class<?> fcClass = resolveClientClass("Fc", cl);
                Class<?> evClass = resolveClientClass("Ev", cl);
                if (fcClass != null && evClass != null) {
                    long valFc = l4 ^ 53260612200332L;
                    int fcArgI = (int) (valFc >>> 32);
                    char fcArgC1 = (char) ((int) ((valFc << 32) >>> 48));
                    char fcArgC2 = (char) ((int) ((valFc << 48) >>> 48));
                    for (Constructor<?> c : fcClass.getDeclaredConstructors()) {
                        if (c.getParameterCount() == 4) {
                            c.setAccessible(true);
                            Object fcScreen = c.newInstance(eaInstance, fcArgI, fcArgC1, fcArgC2);
                            displayScreen(eaInstance, fcScreen, argI, argC1, argC2);
                            return;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] handleKdButtonClick error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Mouse click hook for Ea - handles top-right corner Exit Button:
     * Checks via Ea.a(mouseX, mouseY) and GUI scaled resolution coordinates.
     */
    public static void handleMainMenuClick(Object eaInstance, double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0 || eaInstance == null) return;
        try {
            ClassLoader cl = eaInstance.getClass().getClassLoader();
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();

            boolean isCornerExit = false;
            // 1. Check via Ea's native a(double, double)
            try {
                Method aMethod = eaInstance.getClass().getMethod("a", double.class, double.class);
                aMethod.setAccessible(true);
                isCornerExit = Boolean.TRUE.equals(aMethod.invoke(eaInstance, mouseX, mouseY));
            } catch (Throwable ignored) {}

            // 2. Check via GUI scaled resolution width (eaInstance.y)
            if (!isCornerExit) {
                double guiW = 0.0;
                Class<?> curr = eaInstance.getClass();
                while (curr != null && curr != Object.class) {
                    try {
                        Field yf = curr.getDeclaredField("y");
                        if (yf.getType() == double.class) {
                            yf.setAccessible(true);
                            guiW = yf.getDouble(eaInstance);
                            if (guiW > 0.0) break;
                        }
                    } catch (Throwable ignored) {}
                    curr = curr.getSuperclass();
                }
                if (guiW > 0.0) {
                    if (mouseX >= (guiW - 44.0) && mouseX <= (guiW - 4.0) && mouseY >= 4.0 && mouseY <= 40.0) {
                        isCornerExit = true;
                    }
                }
            }

            if (isCornerExit) {
                // Exit button is disabled per user request
                return;
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] handleMainMenuClick error: " + t.getMessage());
        }
    }

    public static boolean handleMainMenuClickHook(Object eaInstance, double mouseX, double mouseY, int mouseButton) {
        handleMainMenuClick(eaInstance, mouseX, mouseY, mouseButton);
        return false;
    }

    private static Object lastEsScreen = null;

    /**
     * Configure authentic Microsoft-only login screen (Es):
     * Retains all authentic Microsoft login widgets (C, D, H, J) intact on-screen.
     */
    public static void configureEsScreen(Object esScreen) {
        if (esScreen == null || esScreen == lastEsScreen) return;
        lastEsScreen = esScreen;
    }

    /**
     * Intercepts clicks on Es screen (Microsoft & Mojang buttons).
     */
    public static void handleEsClick(Object esScreen, double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0 || esScreen == null) return;
        int width = getDisplayWidth(esScreen);
        int height = getDisplayHeight(esScreen);
        double cx = width / 2.0;
        double cy = height / 2.0;
        // Button C (Microsoft): cx - 120, cy - 65, w=110, h=50
        // Button D (Mojang): cx + 10, cy - 65, w=110, h=50
        boolean isLoginCard = (mouseX >= cx - 130 && mouseX <= cx + 130 && mouseY >= cy - 75 && mouseY <= cy - 10);
        boolean isOverlayArea = (mouseX >= cx - 150 && mouseX <= cx + 150 && mouseY >= cy + 40 && mouseY <= cy + 90);
        if (isLoginCard || isOverlayArea) {
            System.out.println("[CosmicAgent] Detected click on login button in Es! (mouseX=" + mouseX + ", mouseY=" + mouseY + ")");
            InGameLoginHelper.startMicrosoftDeviceLogin(esScreen);
        }
    }

    /**
     * Renders live Microsoft Device Code status and instructions on top of Es screen.
     */
    public static void renderEsOverlay(Object esScreen) {
        if (esScreen == null) return;
        try {
            String status = InGameLoginHelper.getMicrosoftAuthStatus();
            if (status == null || status.trim().isEmpty()) return;

            ClassLoader cl = esScreen.getClass().getClassLoader();
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();

            // Find fontRenderer from esScreen (Ev.h)
            Object fontRenderer = null;
            Class<?> cur = esScreen.getClass();
            while (cur != null && fontRenderer == null) {
                try {
                    Field f = cur.getDeclaredField("h");
                    f.setAccessible(true);
                    fontRenderer = f.get(esScreen);
                } catch (Throwable ignored) {}
                cur = cur.getSuperclass();
            }

            int width = getDisplayWidth(esScreen);
            int height = getDisplayHeight(esScreen);
            int cx = width / 2;
            int cy = height / 2 + 50;

            if (fontRenderer != null) {
                Method drawCentered = null;
                cur = esScreen.getClass();
                while (cur != null && drawCentered == null) {
                    for (Method m : cur.getDeclaredMethods()) {
                        if (m.getParameterTypes().length == 5 &&
                            m.getParameterTypes()[1] == String.class &&
                            m.getParameterTypes()[2] == int.class &&
                            m.getParameterTypes()[3] == int.class &&
                            m.getParameterTypes()[4] == int.class) {
                            drawCentered = m;
                            m.setAccessible(true);
                            break;
                        }
                    }
                    cur = cur.getSuperclass();
                }

                if (drawCentered != null) {
                    drawCentered.invoke(esScreen, fontRenderer, "§e" + status, cx, cy, 0xFFFFFF);
                    if (status.contains("Code:")) {
                        drawCentered.invoke(esScreen, fontRenderer, "§aCode copied to clipboard! Opening browser...", cx, cy + 14, 0x55FF55);
                        drawCentered.invoke(esScreen, fontRenderer, "§7Enter code at microsoft.com/link if needed", cx, cy + 28, 0xAAAAAA);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }


    /**
     * Suppresses legacy Mojang login screen (Em) and redirects cleanly to Main Menu (Ea).
     */
    public static void suppressEmScreen(Object emScreen) {
        if (emScreen == null) return;
        try {
            System.out.println("[CosmicAgent] Suppressing legacy Mojang screen (Em), redirecting to Main Menu...");
            ClassLoader cl = emScreen.getClass().getClassLoader();
            Class<?> wbClass = resolveClientClass("wb", cl);
            Class<?> eaClass = resolveClientClass("Ea", cl);
            if (wbClass != null && eaClass != null) {
                Object wb = wbClass.getMethod("ap").invoke(null);
                if (wb != null) {
                    Object ea = eaClass.getConstructor().newInstance();
                    displayScreen(ea, ea);
                }
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] suppressEmScreen error: " + t.getMessage());
        }
    }

    private static Object lastClearedEaInstance = null;

    /**
     * Clear all official gamemodes and hide forum/store buttons from the Main Menu (Ea).
     */
    public static void clearOfficialGamemodes(Object eaInstance) {
        if (eaInstance == null) return;
        if (eaInstance == lastClearedEaInstance) return;
        lastClearedEaInstance = eaInstance;
        try {
            setupMainMenuButtons(eaInstance);

            // Remove Forum ('ac') and Store ('C') icon buttons completely
            try {
                for (String fieldName : new String[]{"ac", "C"}) {
                    try {
                        Field f = eaInstance.getClass().getDeclaredField(fieldName);
                        f.setAccessible(true);
                        Object btn = f.get(eaInstance);
                        if (btn != null) {
                            Class<?> curr = btn.getClass();
                            while (curr != null && curr != Object.class) {
                                for (Field bf : curr.getDeclaredFields()) {
                                    if (bf.getType() == double.class) {
                                        bf.setAccessible(true);
                                        bf.setDouble(btn, -999999.0);
                                    }
                                }
                                curr = curr.getSuperclass();
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

        } catch (Throwable ignored) {
        }
    }

    /**
     * Clear all official pinned servers from the Multiplayer Screen.
     * Kept as a safe no-op to prevent wiping mod categories in E9.
     */
    public static void clearOfficialServers(Object e9Instance) {
        // Safe no-op
    }

    /**
     * Completely suppress "OFFICIAL GAMEMODES" text rendering in Ea:
     * dT.b:(SLjava/lang/String;FICFII)F
     */
    public static float suppressOfficialGamemodesText(Object fontRenderer, short s, String text, float f, int i1, char c, float f2, int i2, int i3) {
        return 0.0f;
    }

    public static float suppressOfficialGamemodesText(Object fontRenderer, String text, double d1, int i1, double d2, long l1, int i2) {
        return 0.0f;
    }

    private static volatile boolean addAccountTextLogged = false;

    /**
     * Renders authentic "Add Account" button text inside Ea's accounts drawer.
     * dT.b:(Ljava/lang/String;DIDJI)F
     */
    public static float renderAddAccountText(Object fontRenderer, String text, double d1, int i1, double d2, long l1, int i2) {
        String label = "Add Account";
        if (text != null && !text.trim().isEmpty() && !text.toUpperCase().contains("OFFICIAL") && !text.toUpperCase().contains("GAMEMODE")) {
            if (text.equalsIgnoreCase("ADD ACCOUNT") || text.equalsIgnoreCase("ADD_ACCOUNT")) {
                label = "Add Account";
            } else {
                label = text;
            }
        }
        if (!addAccountTextLogged) {
            addAccountTextLogged = true;
            System.out.println("[CosmicAgent] Rendering Add Account button text: '" + label + "'");
        }
        try {
            Method m = fontRenderer.getClass().getMethod("b", String.class, double.class, int.class, double.class, long.class, int.class);
            m.setAccessible(true);
            return (Float) m.invoke(fontRenderer, label, d1, i1, d2, l1, i2);
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] renderAddAccountText error: " + t.getMessage());
            return 0.0f;
        }
    }

    private static volatile Object activeSession = null;

    public static boolean isAccountMatching(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equalsIgnoreCase(b)) return true;
        String cleanA = a.replace("-", "").trim();
        String cleanB = b.replace("-", "").trim();
        if (!cleanA.isEmpty() && cleanA.equalsIgnoreCase(cleanB)) return true;
        return false;
    }

    public static synchronized void updateFallbackSession(String name, String uuid, String token) {
        try {
            System.setProperty("cosmic.player.name", name);
            System.setProperty("cosmic.player.uuid", uuid);
            System.setProperty("cosmic.player.token", token);

            ClassLoader cl = MainMenuHelper.class.getClassLoader();
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();
            Class<?> ywClass = resolveClientClass("Yw", cl);
            if (ywClass != null) {
                Constructor<?> ctor = ywClass.getConstructor(String.class, String.class, String.class, String.class);
                activeSession = ctor.newInstance(name, uuid, token, "mojang");
                System.out.println("[CosmicAgent] MainMenuHelper activeSession updated to: " + name + " (" + uuid + ")");

                try {
                    Class<?> wbClass = resolveClientClass("wb", cl);
                    if (wbClass != null) {
                        Object mc = wbClass.getMethod("ap").invoke(null);
                        if (mc != null) {
                            wbClass.getMethod("a", ywClass).invoke(mc, activeSession);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] updateFallbackSession error: " + t.getMessage());
        }
    }

    /**
     * Guarantees that wb.G() and callers in kN never receive a null Session (cosmicclient.Yw).
     */
    public static Object ensureSessionNotNull(Object wb, Object session) {
        if (activeSession != null) {
            return activeSession;
        }
        if (session != null) {
            return session;
        }
        return getFallbackSession(wb);
    }

    public static Object ensureSessionNotNullDirect(Object session) {
        if (activeSession != null) {
            return activeSession;
        }
        if (session != null) {
            return session;
        }
        return getFallbackSession(null);
    }

    public static synchronized Object getFallbackSession(Object wb) {
        try {
            ClassLoader cl = wb != null ? wb.getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = MainMenuHelper.class.getClassLoader();
            Class<?> ywClass = resolveClientClass("Yw", cl);
            if (ywClass == null) return null;

            String playerName = System.getProperty("cosmic.player.name");
            String playerUuid = System.getProperty("cosmic.player.uuid");
            String playerToken = System.getProperty("cosmic.player.token");

            if (playerName == null || playerName.trim().isEmpty()) {
                try {
                    List<InGameLoginHelper.AccountEntry> accs = InGameLoginHelper.loadAllAccountsFromJson();
                    if (accs != null && !accs.isEmpty()) {
                        for (InGameLoginHelper.AccountEntry acc : accs) {
                            if (acc.isSelected) {
                                playerName = acc.displayName != null ? acc.displayName : acc.username;
                                playerUuid = acc.uuid;
                                playerToken = acc.token;
                                break;
                            }
                        }
                        if (playerName == null && !accs.isEmpty()) {
                            InGameLoginHelper.AccountEntry first = accs.get(0);
                            playerName = first.displayName != null ? first.displayName : first.username;
                            playerUuid = first.uuid;
                            playerToken = first.token;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (playerName == null || playerName.trim().isEmpty()) {
                playerName = "CosmicPlayer";
            }

            if (playerUuid == null || playerUuid.trim().isEmpty()) {
                playerUuid = java.util.UUID.randomUUID().toString().replace("-", "");
            }

            if (playerToken == null || playerToken.trim().isEmpty()) {
                playerToken = "0";
            }

            Constructor<?> ctor = ywClass.getConstructor(String.class, String.class, String.class, String.class);
            Object newSession = ctor.newInstance(playerName, playerUuid, playerToken, "mojang");
            activeSession = newSession;

            if (wb != null) {
                try {
                    for (Field f : wb.getClass().getDeclaredFields()) {
                        if (f.getType() == ywClass) {
                            f.setAccessible(true);
                            f.set(wb, newSession);
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            System.out.println("[CosmicAgent] Immunized session! Created safe fallback Session for wb.G(): " + playerName + " (" + playerUuid + ")");
            return newSession;
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] getFallbackSession error: " + t.getMessage());
            return null;
        }
    }

    /**
     * Intercepts clicking an account row in Ea (Ea.b(short, int, kN, int)).
     * Bypasses the 30-second cooldown, switches the active Minecraft & CosmicClient session,
     * updates selectedUser, and returns a positive aPf result so kN animates green immediately.
     */
    public static Object handleAccountSelectFromMenu(Object eaInstance, Object knInstance) {
        try {
            System.out.println("[CosmicAgent] handleAccountSelectFromMenu invoked for " + (knInstance != null ? knInstance.getClass().getName() : "null"));
            ClassLoader cl = knInstance != null ? knInstance.getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = MainMenuHelper.class.getClassLoader();

            // 1. Extract v_ from knInstance
            Object v_Instance = null;
            if (knInstance != null) {
                try {
                    Method aMethod = knInstance.getClass().getMethod("a");
                    v_Instance = aMethod.invoke(knInstance);
                } catch (Throwable ignored) {
                    for (Field f : knInstance.getClass().getDeclaredFields()) {
                        if (f.getType().getName().endsWith("v_")) {
                            f.setAccessible(true);
                            v_Instance = f.get(knInstance);
                            break;
                        }
                    }
                }
            }

            // 2. Switch session & credentials via InGameLoginHelper
            if (v_Instance != null) {
                InGameLoginHelper.handleV_AccountSelect(v_Instance);
            }

            // 3. Reset cooldown timer on Ea.P if present
            if (eaInstance != null) {
                try {
                    for (Field f : eaInstance.getClass().getDeclaredFields()) {
                        if (f.getType().getName().endsWith("Lc")) {
                            f.setAccessible(true);
                            Object lc = f.get(eaInstance);
                            if (lc != null) {
                                for (Method m : lc.getClass().getDeclaredMethods()) {
                                    if (m.getParameterCount() == 0 && m.getReturnType() == void.class) {
                                        m.setAccessible(true);
                                        m.invoke(lc);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 4. Construct new cosmicclient.aPf("Logged in", true)
            Class<?> apfClass = resolveClientClass("aPf", cl);
            if (apfClass != null) {
                Constructor<?> apfCtor = apfClass.getConstructor(String.class, boolean.class);
                Object apfResult = apfCtor.newInstance("Logged in", true);
                System.out.println("[CosmicAgent] Created positive aPf result for kN green animation!");
                return apfResult;
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] handleAccountSelectFromMenu error: " + t.getMessage());
            t.printStackTrace();
        }
        return null;
    }

    /**
     * Completely suppress Forum ('ac') and Store ('C') drawing in Ea.
     */
    public static void suppressKW(Object kwInstance, double d1, double d2, int i1, char c1, float f1, short s1) {
    }

    /**
     * Completely suppress Forum ('ac') and Store ('C') click handling in Ea.
     */
    public static void suppressKWClick(Object kwInstance, long j1, int i1, double d1, double d2, int i2) {
    }

    /**
     * Completely suppress hover detection for Forum and Store.
     */
    public static boolean suppressKWHover(Object kwInstance, double d1, double d2) {
        return false;
    }

    /**
     * Check if the game is currently inside an active world.
     */
    public static boolean isInWorld(ClassLoader cl) {
        try {
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = MainMenuHelper.class.getClassLoader();
            Class<?> wbClass = null;
            try {
                wbClass = Class.forName("cosmicclient.wb", true, cl);
            } catch (Throwable ignored) {
                try {
                    wbClass = Class.forName("net.minecraft.client.l8", true, cl);
                } catch (Throwable ignored2) {
                    try {
                        wbClass = Class.forName("net.minecraft.client.Minecraft", true, cl);
                    } catch (Throwable ignored3) {}
                }
            }
            if (wbClass == null) return false;
            Object mc = null;
            try {
                mc = wbClass.getMethod("ap").invoke(null);
            } catch (Throwable ignored) {
                try {
                    mc = wbClass.getMethod("w").invoke(null);
                } catch (Throwable ignored2) {
                    try {
                        mc = wbClass.getMethod("getMinecraft").invoke(null);
                    } catch (Throwable ignored3) {}
                }
            }
            if (mc == null) return false;

            // In Cosmic Client 1.8.9 (cosmicclient.wb):
            // H is cosmicclient.YC (WorldClient/World)
            // d is cosmicclient.Me (EntityPlayerSP/Player)
            try {
                Field hField = wbClass.getField("H");
                if (hField.get(mc) != null) return true;
            } catch (Throwable ignored) {}

            try {
                Field dField = wbClass.getField("d");
                if (dField.get(mc) != null) return true;
            } catch (Throwable ignored) {}

            // Dynamic scan for non-primitive fields matching World or Player types
            for (Field f : wbClass.getDeclaredFields()) {
                if (f.getType().isPrimitive()) continue;
                String typeName = f.getType().getName().toLowerCase();
                if (typeName.contains("world") || typeName.endsWith(".bu") || typeName.endsWith(".yc") || typeName.endsWith(".yu")) {
                    f.setAccessible(true);
                    if (f.get(mc) != null) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Hook called by Ev.d(int, int, short, int) to replace dirt background with 4K space nebula when in menus.
     * Returns true if custom background was drawn (so dirt background is skipped).
     */
    public static boolean renderDefaultBackgroundHook(Object screen) {
        if (screen == null) return false;
        ClassLoader cl = screen.getClass().getClassLoader();
        if (isInWorld(cl)) {
            return false;
        }
        renderCustomBackground(screen);
        return true;
    }

    /**
     * Intercept CEF resource requests for in-game HTML/CEF views.
     */
    public static boolean interceptResource(Object hkInstance, Object cefRequest, Object cefCallback) {
        try {
            String url = null;
            try {
                Method getURL = cefRequest.getClass().getMethod("getURL", new Class[0]);
                url = (String) getURL.invoke(cefRequest, new Object[0]);
            } catch (Exception ignored) {
            }
            if (url == null) {
                return false;
            }

            // Suppress the looping background video so static 4K image is displayed
            if (url.contains("background.webm")) {
                InputStream is = new ByteArrayInputStream(new byte[0]);
                applyStream(hkInstance, cefCallback, is);
                return true;
            }

            // In-Game Mod & HUD menu
            if (url.contains("hud_dummy.html") || url.contains("hud_menu.html")) {
                InputStream is = MainMenuHelper.class.getResourceAsStream("/assets/minecraft/cosmic/html/hud_menu.html");
                if (is == null) is = MainMenuHelper.class.getResourceAsStream("/assets/minecraft/cosmic/ui/hud_dummy.html");
                if (is != null) {
                    applyStream(hkInstance, cefCallback, is);
                    System.out.println("[CosmicAgent] In-Game Mod Menu HTML intercepted & served");
                    return true;
                }
            }

            String resourcePath = null;
            if (url.contains("cosmic/textures/")) {
                int idx = url.indexOf("cosmic/textures/");
                resourcePath = "/assets/minecraft/" + url.substring(idx);
            } else if (url.contains("cosmic/ui/")) {
                int idx = url.indexOf("cosmic/ui/");
                resourcePath = "/assets/minecraft/" + url.substring(idx);
            } else if (url.contains("cosmic/font/") || url.contains("cosmic/fonts/")) {
                int idx = url.indexOf("cosmic/font");
                resourcePath = "/assets/minecraft/" + url.substring(idx);
            } else if (url.contains("cosmic/shaders/")) {
                int idx = url.indexOf("cosmic/shaders/");
                resourcePath = "/assets/minecraft/" + url.substring(idx);
            } else if (url.contains("cosmic/html/") || url.contains("cosmic/css/") || url.contains("cosmic/js/")) {
                int idx = url.indexOf("cosmic/");
                resourcePath = "/assets/minecraft/" + url.substring(idx);
            }

            if (resourcePath != null) {
                if (resourcePath.contains("?")) {
                    resourcePath = resourcePath.substring(0, resourcePath.indexOf("?"));
                }

                InputStream is = null;
                if (resourcePath.contains("background")) {
                    try {
                        String appData = System.getenv("APPDATA");
                        if (appData != null) {
                            File customBg1 = new File(appData + "/.minecraft/cosmic/background.jpg");
                            File customBg2 = new File(appData + "/.minecraft/cosmic/background.png");
                            File customBg3 = new File(appData + "/.minecraft/cosmic/mainmenu/background.jpg");
                            if (customBg1.exists()) is = new FileInputStream(customBg1);
                            else if (customBg2.exists()) is = new FileInputStream(customBg2);
                            else if (customBg3.exists()) is = new FileInputStream(customBg3);
                        }
                    } catch (Throwable ignored) {
                    }
                }

                if (is == null) {
                    is = MainMenuHelper.class.getResourceAsStream(resourcePath);
                }
                if (is == null && resourcePath.contains("background")) {
                    is = MainMenuHelper.class.getResourceAsStream("/background.jpg");
                    if (is == null) {
                        is = MainMenuHelper.class.getResourceAsStream("/assets/minecraft/cosmic/textures/mainmenu/background.jpg");
                    }
                }

                if (is != null) {
                    applyStream(hkInstance, cefCallback, is);
                    if (!logged) {
                        logged = true;
                        System.out.println("[CosmicAgent] In-Game Menu Interceptor Active: " + resourcePath);
                    }
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void applyStream(Object hkInstance, Object cefCallback, InputStream is) {
        try {
            Field cField = hkInstance.getClass().getDeclaredField("c");
            cField.setAccessible(true);
            cField.set(hkInstance, is);
            Field aField = hkInstance.getClass().getDeclaredField("a");
            aField.setAccessible(true);
            aField.setBoolean(hkInstance, true);
            if (cefCallback != null) {
                Method cont = cefCallback.getClass().getMethod("Continue", new Class[0]);
                cont.invoke(cefCallback, new Object[0]);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Initializes the authentic Main Menu (nn screen) with strictly 4 buttons:
     * SINGLEPLAYER, MULTIPLAYER, OPTIONS, and QUIT GAME.
     * Completely removes Official Gamemodes, Forums, and Store.
     */
    public static synchronized void initNN(Object nnScreen) {
        if (nnScreen == null) return;
        try {
            Class<?> nnClass = nnScreen.getClass();
            Field iField = null;
            try {
                iField = nnClass.getDeclaredField("I");
            } catch (NoSuchFieldException e) {
                return; // Not 1.12 GuiMainMenu
            }

            ClassLoader cl = nnScreen.getClass().getClassLoader();
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = MainMenuHelper.class.getClassLoader();

            Class<?> fpClass = null;
            try {
                fpClass = Class.forName("fp", true, cl);
            } catch (Throwable ignored) {
                return; // Not 1.12
            }

            // 1. Ensure auto-login is active for player
            InGameLoginHelper.ensureAutoLogin(cl);

            // 2. Retain dummy instances of U, T, Y, C initialized by constructor
            // to ensure no internal unhooked event dispatchers ever encounter NullPointerException.

            // 3. Create the 4 navigation buttons:
            // fp = Singleplayer
            // fV = Multiplayer
            // fe = Options
            // fr = Quit Game
            Class<?> fVClass = Class.forName("fV", true, cl);
            Class<?> feClass = Class.forName("fe", true, cl);
            Class<?> frClass = Class.forName("fr", true, cl);

            Constructor<?> fpCtor = fpClass.getDeclaredConstructor(nnClass, String.class);
            Constructor<?> fVCtor = fVClass.getDeclaredConstructor(nnClass, String.class);
            Constructor<?> feCtor = feClass.getDeclaredConstructor(nnClass, String.class);
            Constructor<?> frCtor = frClass.getDeclaredConstructor(nnClass, String.class);

            fpCtor.setAccessible(true);
            fVCtor.setAccessible(true);
            feCtor.setAccessible(true);
            frCtor.setAccessible(true);

            Object spBtn = fpCtor.newInstance(nnScreen, "SINGLEPLAYER");
            Object mpBtn = fVCtor.newInstance(nnScreen, "MULTIPLAYER");
            Object optBtn = feCtor.newInstance(nnScreen, "OPTIONS");
            Object quitBtn = frCtor.newInstance(nnScreen, "QUIT GAME");

            setButtonLabel(spBtn, "SINGLEPLAYER");
            setButtonLabel(mpBtn, "MULTIPLAYER");
            setButtonLabel(optBtn, "OPTIONS");
            setButtonLabel(quitBtn, "QUIT GAME");

            List<Object> btnList = new ArrayList<>();
            btnList.add(spBtn);
            btnList.add(mpBtn);
            btnList.add(optBtn);
            btnList.add(quitBtn);

            iField.setAccessible(true);
            iField.set(nnScreen, btnList);

            // 4. Layout buttons
            layoutNNButtons(nnScreen);

            System.out.println("[CosmicAgent] initNN: Configured SINGLEPLAYER, MULTIPLAYER, OPTIONS, and QUIT GAME buttons (Total: " + btnList.size() + ")");
        } catch (Throwable t) {
            // Silently return to prevent log pollution on incompatible screens
        }
    }

    /**
     * Positions the 4 main menu buttons cleanly on the left side with optimal responsive spacing.
     */
    public static void layoutNNButtons(Object nnScreen) {
        if (nnScreen == null) return;
        try {
            double screenWidth = 0;
            double screenHeight = 0;
            Class<?> curr = nnScreen.getClass();
            while (curr != null && curr != Object.class) {
                try {
                    Field bF = curr.getDeclaredField("B");
                    bF.setAccessible(true);
                    screenWidth = bF.getDouble(nnScreen);
                } catch (Throwable ignored) {}
                try {
                    Field aF = curr.getDeclaredField("A");
                    aF.setAccessible(true);
                    screenHeight = aF.getDouble(nnScreen);
                } catch (Throwable ignored) {}
                if (screenWidth > 0 && screenHeight > 0) break;
                curr = curr.getSuperclass();
            }
            if (screenWidth <= 0 || screenHeight <= 0) {
                try {
                    ClassLoader cl = nnScreen.getClass().getClassLoader();
                    Class<?> displayClass = getDisplayClass(cl);
                    if (displayClass != null) {
                        screenWidth = ((Number) displayClass.getMethod("getWidth").invoke(null)).doubleValue();
                        screenHeight = ((Number) displayClass.getMethod("getHeight").invoke(null)).doubleValue();
                    }
                } catch (Throwable ignored) {}
            }
            if (screenWidth <= 0) screenWidth = 854;
            if (screenHeight <= 0) screenHeight = 480;

            Field iField = nnScreen.getClass().getDeclaredField("I");
            iField.setAccessible(true);
            List<?> btnList = (List<?>) iField.get(nnScreen);
            if (btnList == null || btnList.isEmpty()) {
                initNN(nnScreen);
                btnList = (List<?>) iField.get(nnScreen);
            }
            if (btnList == null) return;

            double btnWidth = Math.max(160.0, Math.min(240.0, screenWidth * 0.25));
            double btnHeight = 22.0;
            double startX = Math.max(28.0, screenWidth * 0.08);
            double startY = Math.max(120.0, screenHeight * 0.38);
            double gap = 30.0;

            for (int i = 0; i < btnList.size(); i++) {
                Object btn = btnList.get(i);
                if (btn == null) continue;
                double x = startX;
                double y = startY + (i * gap);

                setButtonBounds(btn, x, y, btnWidth, btnHeight);
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] layoutNNButtons error: " + t.getMessage());
        }
    }

    private static Field btnEField = null;
    private static Field btnGField = null;
    private static Field btnJField = null;
    private static Field btnFField = null;
    private static boolean btnBoundsFieldsCached = false;

    private static void setButtonBounds(Object btn, double x, double y, double w, double h) {
        if (btn == null) return;
        try {
            if (!btnBoundsFieldsCached) {
                btnBoundsFieldsCached = true;
                Class<?> curr = btn.getClass();
                while (curr != null && curr != Object.class) {
                    for (Field f : curr.getDeclaredFields()) {
                        if (f.getName().equals("e") && f.getType() == double.class) { f.setAccessible(true); btnEField = f; }
                        else if (f.getName().equals("g") && f.getType() == double.class) { f.setAccessible(true); btnGField = f; }
                        else if (f.getName().equals("j") && f.getType() == double.class) { f.setAccessible(true); btnJField = f; }
                        else if (f.getName().equals("f") && f.getType() == double.class) { f.setAccessible(true); btnFField = f; }
                    }
                    curr = curr.getSuperclass();
                }
            }
            if (btnEField != null) btnEField.setDouble(btn, x);
            if (btnGField != null) btnGField.setDouble(btn, y);
            if (btnJField != null) btnJField.setDouble(btn, w);
            if (btnFField != null) btnFField.setDouble(btn, h);
        } catch (Throwable ignored) {}
    }

    private static Field cachedIField = null;
    private static Method cachedDrawLambda = null;
    private static boolean cachedDrawLambdaSearched = false;
    private static Method cachedAMethod = null;
    private static int cachedK1 = 0;
    private static long cachedK2 = 0L;
    private static boolean cachedKeysInitialized = false;

    /**
     * Renders only the 4 main menu buttons (Singleplayer, Multiplayer, Options, Quit Game)
     * with their smooth hover animations and labels at 144+ FPS.
     */
    public static void drawNNButtons(Object nnScreen, double mouseX, double mouseY, float partialTicks) {
        if (nnScreen == null) return;
        try {
            if (cachedIField == null) {
                cachedIField = nnScreen.getClass().getDeclaredField("I");
                cachedIField.setAccessible(true);
            }
            List<?> btnList = (List<?>) cachedIField.get(nnScreen);
            if (btnList == null || btnList.isEmpty()) {
                initNN(nnScreen);
                btnList = (List<?>) cachedIField.get(nnScreen);
            }
            if (btnList == null) return;

            if (!cachedDrawLambdaSearched) {
                cachedDrawLambdaSearched = true;
                try {
                    ClassLoader cl = nnScreen.getClass().getClassLoader();
                    Class<?> fZClass = Class.forName("fZ", true, cl);
                    cachedDrawLambda = nnScreen.getClass().getDeclaredMethod("lambda$draw$4", double.class, double.class, float.class, fZClass);
                    cachedDrawLambda.setAccessible(true);
                } catch (Throwable ignored) {}
            }

            if (cachedDrawLambda != null) {
                for (int i = 0; i < btnList.size(); i++) {
                    Object btn = btnList.get(i);
                    if (btn != null) {
                        try {
                            cachedDrawLambda.invoke(null, mouseX, mouseY, partialTicks, btn);
                        } catch (Throwable ignored) {}
                    }
                }
                return;
            }

            if (!cachedKeysInitialized) {
                cachedKeysInitialized = true;
                try {
                    Field bbField = nnScreen.getClass().getDeclaredField("bb");
                    bbField.setAccessible(true);
                    long bb = bbField.getLong(null);
                    long v6 = bb ^ 91920687565130L;
                    long v9_xor = v6 ^ 123504975606370L;
                    cachedK1 = (int) (v9_xor >>> 32);
                    cachedK2 = (v9_xor << 32) >>> 32;
                } catch (Throwable ignored) {}
            }

            for (int i = 0; i < btnList.size(); i++) {
                Object btn = btnList.get(i);
                if (btn == null) continue;
                try {
                    if (cachedAMethod == null) {
                        cachedAMethod = btn.getClass().getMethod("a", int.class, double.class, long.class, double.class, float.class);
                    }
                    cachedAMethod.invoke(btn, cachedK1, mouseX, cachedK2, mouseY, partialTicks);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] drawNNButtons error: " + t.getMessage());
        }
    }

    /**
     * Handles mouse clicks on the 4 main menu navigation buttons.
     */
    public static void handleNNClick(Object nnScreen, double mouseX, int p2, double mouseY, int mouseButton, long p5) {
        if (nnScreen == null || mouseButton != 0) return;
        try {
            if (cachedIField == null) {
                cachedIField = nnScreen.getClass().getDeclaredField("I");
                cachedIField.setAccessible(true);
            }
            List<?> btnList = (List<?>) cachedIField.get(nnScreen);
            if (btnList == null) return;

            for (int i = 0; i < btnList.size(); i++) {
                Object btn = btnList.get(i);
                if (btn == null) continue;
                try {
                    Method cMethod = btn.getClass().getMethod("c", double.class, double.class);
                    boolean isHovered = (Boolean) cMethod.invoke(btn, mouseX, mouseY);
                    if (isHovered) {
                        System.out.println("[CosmicAgent] Main menu button clicked: index " + i + " (" + btn.getClass().getName() + ")");
                        if (i == 3 || btn.getClass().getName().equals("fr")) {
                            quitGame();
                            return;
                        }
                        openScreenClean(nnScreen, i);
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] handleNNClick error: " + t.getMessage());
        }
    }

    /**
     * Cleanly opens the appropriate Minecraft screen without obfuscated token arithmetic traps.
     */
    public static void openScreenClean(Object nnScreen, int index) {
        try {
            if (!ClientHardeningGuard.isHeartbeatValid()) return;
            ClassLoader cl = nnScreen.getClass().getClassLoader();
            Class<?> l8Class = Class.forName("net.minecraft.client.l8", true, cl);
            Object mc = l8Class.getMethod("w").invoke(null);
            if (mc == null) return;

            Object target = null;
            if (index == 0) {
                System.out.println("[CosmicAgent] Transitioning to Singleplayer (net.minecraft.client.ko)...");
                Class<?> koClass = Class.forName("net.minecraft.client.ko", true, cl);
                Class<?> j5Class = Class.forName("net.minecraft.client.j5", true, cl);
                Constructor<?> ctor = koClass.getDeclaredConstructor(j5Class, long.class, char.class);
                ctor.setAccessible(true);
                target = ctor.newInstance(nnScreen, 0L, (char) 0);
            } else if (index == 1) {
                System.out.println("[CosmicAgent] Transitioning to Multiplayer (net.minecraft.client.jR)...");
                Class<?> jRClass = Class.forName("net.minecraft.client.jR", true, cl);
                Class<?> j5Class = Class.forName("net.minecraft.client.j5", true, cl);
                Constructor<?> ctor = jRClass.getDeclaredConstructor(j5Class);
                ctor.setAccessible(true);
                target = ctor.newInstance(nnScreen);
            } else if (index == 2) {
                System.out.println("[CosmicAgent] Transitioning to Options (net.minecraft.client.jQ)...");
                Class<?> jQClass = Class.forName("net.minecraft.client.jQ", true, cl);
                Class<?> j5Class = Class.forName("net.minecraft.client.j5", true, cl);
                Class<?> qClass = Class.forName("net.minecraft.client.q", true, cl);
                Constructor<?> ctor = jQClass.getDeclaredConstructor(int.class, j5Class, qClass, byte.class, int.class);
                ctor.setAccessible(true);
                Field aEField = l8Class.getDeclaredField("aE");
                aEField.setAccessible(true);
                Object gameSettings = aEField.get(mc);
                target = ctor.newInstance(0, nnScreen, gameSettings, (byte) 0, 0);
            }

            if (target != null) {
                displayGuiScreenClean(mc, target);
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] openScreenClean failed: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Cleanly displays a GuiScreen on Minecraft without requiring obfuscated parameter tokens.
     */
    public static void displayGuiScreenClean(Object mc, Object targetScreen) {
        try {
            if (!ClientHardeningGuard.isHeartbeatValid()) return;
            Class<?> l8Class = mc.getClass();
            ClassLoader cl = l8Class.getClassLoader();
            Field adField = l8Class.getDeclaredField("ad");
            adField.setAccessible(true);
            Object oldScreen = adField.get(mc);

            // 1. If previous screen was active, close it cleanly
            if (oldScreen != null) {
                try {
                    Method fMethod = oldScreen.getClass().getMethod("f", int.class, char.class, char.class);
                    fMethod.invoke(oldScreen, 0, (char) 0, (char) 0);
                } catch (Throwable ignored) {}
            }

            // 2. If targetScreen is null (closing current screen / returning to world or menu)
            if (targetScreen == null) {
                adField.set(mc, null);
                try {
                    Field asField = l8Class.getDeclaredField("as");
                    asField.setAccessible(true);
                    Object world = asField.get(mc);
                    Class<?> mouseClass = Class.forName("org.lwjgl.input.Mouse");
                    Field atField = l8Class.getDeclaredField("aT");
                    atField.setAccessible(true);
                    if (world != null) {
                        mouseClass.getMethod("setGrabbed", boolean.class).invoke(null, true);
                        atField.setBoolean(mc, true);
                    } else {
                        mouseClass.getMethod("setGrabbed", boolean.class).invoke(null, false);
                        atField.setBoolean(mc, false);
                    }
                } catch (Throwable ignored) {}
                System.out.println("[CosmicAgent] Screen closed (currentScreen set to null)");
                return;
            }

            // 3. Set currentScreen
            adField.set(mc, targetScreen);

            try {
                Class<?> mouseClass = Class.forName("org.lwjgl.input.Mouse");
                mouseClass.getMethod("setGrabbed", boolean.class).invoke(null, false);
            } catch (Throwable ignored) {}

            try {
                Field atField = l8Class.getDeclaredField("aT");
                atField.setAccessible(true);
                atField.setBoolean(mc, false);
            } catch (Throwable ignored) {}

            int w = 854;
            int h = 480;
            try {
                Field aUField = l8Class.getDeclaredField("aU");
                aUField.setAccessible(true);
                int dw = aUField.getInt(mc);
                Field aField = l8Class.getDeclaredField("A");
                aField.setAccessible(true);
                int dh = aField.getInt(mc);

                int scaleFactor = 1;
                int guiScale = 2;
                try {
                    Field aEField = l8Class.getDeclaredField("aE");
                    aEField.setAccessible(true);
                    Object gs = aEField.get(mc);
                    if (gs != null) {
                        Field bLField = gs.getClass().getDeclaredField("bL");
                        bLField.setAccessible(true);
                        guiScale = bLField.getInt(gs);
                    }
                } catch (Throwable ignored) {}
                if (guiScale == 0) guiScale = 1000;
                while (scaleFactor < guiScale && dw / (scaleFactor + 1) >= 320 && dh / (scaleFactor + 1) >= 240) {
                    scaleFactor++;
                }
                w = (int) Math.ceil((double) dw / (double) scaleFactor);
                h = (int) Math.ceil((double) dh / (double) scaleFactor);
            } catch (Throwable ignored) {}

            Class<?> j5Class = Class.forName("net.minecraft.client.j5", true, cl);
            setFieldSafe(j5Class, targetScreen, "s", mc);
            try {
                setFieldSafe(j5Class, targetScreen, "h", l8Class.getMethod("E").invoke(mc));
            } catch (Throwable ignored) {}
            try {
                Field dF = l8Class.getDeclaredField("D");
                dF.setAccessible(true);
                setFieldSafe(j5Class, targetScreen, "t", dF.get(mc));
            } catch (Throwable ignored) {}
            setFieldSafe(j5Class, targetScreen, "o", w);
            setFieldSafe(j5Class, targetScreen, "k", h);

            Field lF = j5Class.getDeclaredField("l");
            lF.setAccessible(true);
            List<?> bl = (List<?>) lF.get(targetScreen);
            if (bl == null) {
                bl = new ArrayList<>();
                lF.set(targetScreen, bl);
            } else {
                bl.clear();
            }

            try {
                Method mMethod = targetScreen.getClass().getMethod("m", int.class, byte.class, int.class);
                mMethod.invoke(targetScreen, 1, (byte) -1, 1);
            } catch (Throwable ignored) {}
            System.out.println("[CosmicAgent] Screen display transition complete: " + targetScreen.getClass().getName());
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] displayGuiScreenClean error: " + t.getMessage());
        }
    }

    /**
     * Cleanly shuts down the Minecraft client on Quit Game click.
     */
    public static void quitGame() {
        System.out.println("[CosmicAgent] Quit Game clicked. Shutting down cleanly...");
        try {
            Class<?> l8Class = Class.forName("net.minecraft.client.l8");
            Object mc = l8Class.getMethod("w").invoke(null);
            if (mc != null) {
                try {
                    l8Class.getMethod("ai").invoke(mc);
                } catch (Throwable t) {
                    l8Class.getMethod("at").invoke(mc);
                }
            }
        } catch (Throwable ignored) {}
        System.exit(0);
    }

    public static String getKoString(int id, long key) {
        switch (id) {
            case 9332: return "Select World";
            case 27774: return "selectWorld.title";
            case 1451: return "lanServer.scanning";
            case 7980: return "lanServer.otherPlayers";
            case 4606: return "selectWorld.select";
            case 19431: return "selectWorld.create";
            case 565: return "gameMode.survival";
            case 26734: return "gameMode.creative";
            case 20535: return "gameMode.adventure";
            case 27732: return "gameMode.spectator";
            case 29088: return "selectWorld.conversion";
            case 28796: return "selectWorld.select";
            case 2146: return "selectWorld.create";
            case 4744: return "selectWorld.rename";
            case 18298: return "selectWorld.delete";
            case 26624: return "selectWorld.recreate";
            case 4949: return "gui.cancel";
            case 9226: return "selectWorld.renameTitle";
            case 23858: return "selectWorld.recreateTitle";
            case 20067: return "Unable to load worlds";
            case 18641: return "selectWorld.deleteQuestion";
            case 4158: return "selectWorld.deleteWarning";
            case 5545: return "selectWorld.deleteButton";
            case 21204: return "selectWorld.hardcoreMode";
            case 9839: return "gameMode.hardcore";
            default: return "Select World";
        }
    }

    public static String getDrString(int id, long key) {
        switch (id) {
            case 21169: return "selectWorld.conversion";
            case 15226: return "gameMode.hardcore";
            default: return "";
        }
    }

    public static void loadKoSaves(Object koInstance) {
        if (koInstance == null) return;
        try {
            Class<?> koClass = koInstance.getClass();
            Field fField = koClass.getDeclaredField("F");
            fField.setAccessible(true);
            List<?> list = (List<?>) fField.get(koInstance);
            if (list == null) {
                list = new ArrayList<>();
                fField.set(koInstance, list);
            }
            Field iField = koClass.getDeclaredField("I");
            iField.setAccessible(true);
            iField.setInt(koInstance, -1);
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] loadKoSaves error: " + t.getMessage());
        }
    }

    public static String getJRString(int id, long key) {
        if (!ClientHardeningGuard.isHeartbeatValid()) return "";
        switch (id) {
            case 3892:
            case 11331: return "multiplayer.title";
            case 8080: return "selectServer.select";
            case 8831: return "selectServer.direct";
            case 9432: return "selectServer.add";
            case 16580: return "selectServer.edit";
            case 28784: return "selectServer.delete";
            case 10814: return "selectServer.refresh";
            case 12802:
            case 2408: return "gui.cancel";
            case 26263: return "selectServer.deleteQuestion";
            case 26592: return "selectServer.deleteWarning";
            case 21521: return "selectServer.deleteButton";
            case 24120:
            case 18253:
            case 23319: return "selectServer.defaultName";
            default: return "Play Multiplayer";
        }
    }

    public static String getGzString(int id, long key) {
        if (!ClientHardeningGuard.isHeartbeatValid()) return "";
        switch (id) {
            case 4901:
            case 16286: return "servers.dat";
            case 19176:
            case 22505: return "servers";
            case 7244: return "Failed to load servers.dat";
            case 3073: return "Failed to save servers.dat";
            default: return "servers.dat";
        }
    }

    public static String getJQString(int id, long key) {
        if (!ClientHardeningGuard.isHeartbeatValid()) return "";
        switch (id) {
            case 8599:
            case 28616:
            case 1436: return "options.title";
            case 10165: return "options.fov";
            case 2219: return "options.realmsNotifications";
            case 1431: return "options.skinCustomisation";
            case 22484: return "options.video";
            case 30230: return "options.controls";
            case 31273: return "options.language";
            case 3326: return "options.chat.title";
            case 25202: return "options.sounds";
            case 25380: return "options.snooper.view";
            case 10595: return "options.resourcepack";
            case 28063: return "gui.done";
            case 19625: return "options.difficulty";
            case 20868: return "options.difficulty.lock";
            case 28651: return "options.difficulty.unlock";
            default: return "Options";
        }
    }

    public static String getKeyBinding(Object gameSettings, Object options) {
        if (options == null || !ClientHardeningGuard.isHeartbeatValid()) return "";
        try {
            Class<?> lSClass = options.getClass();
            Method getEnumString = lSClass.getMethod("a");
            String key = (String) getEnumString.invoke(options);
            String title = formatI18n(key, null) + ": ";

            Method isFloat = lSClass.getMethod("d");
            if ((Boolean) isFloat.invoke(options)) {
                Method getFloat = gameSettings.getClass().getMethod("b", lSClass);
                float val = ((Number) getFloat.invoke(gameSettings, options)).floatValue();
                String name = ((Enum<?>) options).name();
                if ("FOV".equals(name)) {
                    if (val == 0.0f) return title + formatI18n("options.fov.min", null);
                    if (val == 1.0f) return title + formatI18n("options.fov.max", null);
                    return title + (int) (70.0f + val * 40.0f);
                } else if ("GAMMA".equals(name)) {
                    if (val == 0.0f) return title + formatI18n("options.gamma.min", null);
                    if (val == 1.0f) return title + formatI18n("options.gamma.max", null);
                    return title + "+" + (int) (val * 100.0f) + "%";
                } else if ("FRAMERATE_LIMIT".equals(name)) {
                    if (val >= 260.0f) return title + formatI18n("options.framerateLimit.max", null);
                    return title + (int) val + " fps";
                }
                return title + (int) (val * 100.0f) + "%";
            }

            Method isBoolean = lSClass.getMethod("b");
            if ((Boolean) isBoolean.invoke(options)) {
                Method getBool = gameSettings.getClass().getMethod("d", lSClass);
                boolean val = (Boolean) getBool.invoke(gameSettings, options);
                return title + formatI18n(val ? "options.on" : "options.off", null);
            }

            return title;
        } catch (Throwable t) {
            return options != null ? options.toString() : "";
        }
    }

    public static String getKhString(int id, long key) {
        switch (id) {
            case 26606: return "addServer.title";
            case 14872: return "addServer.enterName";
            case 907: return "addServer.enterIp";
            case 1057: return "addServer.resourcePack";
            default: return "";
        }
    }

    public static String getJKString(int id, long key) {
        switch (id) {
            case 24644:
            case 26608:
            case 6867: return "selectServer.direct";
            case 14056:
            case 9190: return "selectServer.enterIp";
            case 514:
            case 4080: return "selectServer.select";
            case 31337:
            case 9040: return "gui.cancel";
            default: return "";
        }
    }

    public static void safeNoop() {}
    public static int safeZeroInt() { return 0; }
    public static boolean safeFalse() { return false; }
    public static Object safeNull() { return null; }

    public static CallSite safeBootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
        if (!ClientHardeningGuard.isHeartbeatValid()) return null;
        try {
            Class<?> ret = type.returnType();
            MethodHandle target;
            if (ret == void.class) {
                Method m = MainMenuHelper.class.getDeclaredMethod("safeNoop");
                target = lookup.unreflect(m);
                target = MethodHandles.dropArguments(target, 0, type.parameterList());
            } else if (ret == int.class) {
                Method m = MainMenuHelper.class.getDeclaredMethod("safeZeroInt");
                target = lookup.unreflect(m);
                target = MethodHandles.dropArguments(target, 0, type.parameterList());
            } else if (ret == boolean.class) {
                Method m = MainMenuHelper.class.getDeclaredMethod("safeFalse");
                target = lookup.unreflect(m);
                target = MethodHandles.dropArguments(target, 0, type.parameterList());
            } else if (Throwable.class.isAssignableFrom(ret)) {
                if (type.parameterCount() > 0 && Throwable.class.isAssignableFrom(type.parameterType(0))) {
                    target = MethodHandles.identity(type.parameterType(0));
                    if (type.parameterCount() > 1) {
                        target = MethodHandles.dropArguments(target, 1, type.parameterList().subList(1, type.parameterCount()));
                    }
                } else {
                    Method m = MainMenuHelper.class.getDeclaredMethod("safeNull");
                    target = lookup.unreflect(m);
                    target = MethodHandles.dropArguments(target, 0, type.parameterList());
                }
            } else {
                Method m = MainMenuHelper.class.getDeclaredMethod("safeNull");
                target = lookup.unreflect(m);
                target = MethodHandles.dropArguments(target, 0, type.parameterList());
            }
            return new ConstantCallSite(target.asType(type));
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] safeBootstrap error for " + name + " : " + type + " - " + t.getMessage());
            return null;
        }
    }

    public static String formatI18n(String key, Object[] args) {
        if (key == null) return "";
        try {
            Class<?> ldClass = Class.forName("net.minecraft.client.ld");
            Field aField = ldClass.getDeclaredField("a");
            aField.setAccessible(true);
            Object b1 = aField.get(null);
            if (b1 != null) {
                for (Method m : b1.getClass().getMethods()) {
                    if (m.getName().equals("a") && m.getParameterCount() == 5 && m.getParameterTypes()[2] == String.class) {
                        m.setAccessible(true);
                        return (String) m.invoke(b1, 0, (byte) 0, key, 0, args != null ? args : new Object[0]);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return key;
    }

    private static void setFieldSafe(Class<?> clazz, Object obj, String fieldName, Object val) {
        if (obj == null) return;
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(obj, val);
        } catch (Throwable ignored) {}
    }

    private static void setFieldSafe(Object obj, String fieldName, Object val) {
        if (obj == null) return;
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(obj, val);
        } catch (Throwable ignored) {}
    }
}
