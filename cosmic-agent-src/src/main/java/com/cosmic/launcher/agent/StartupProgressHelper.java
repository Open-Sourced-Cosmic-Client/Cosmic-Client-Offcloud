package com.cosmic.launcher.agent;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class StartupProgressHelper {

    private static volatile boolean isFinished = false;
    private static long startTime = 0L;
    private static int frameCount = 0;
    private static boolean loggedStart = false;

    public static void onDisplayUpdate() {
        if (isFinished) return;

        try {
            if (startTime == 0L) {
                startTime = System.currentTimeMillis();
            }
            frameCount++;

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = StartupProgressHelper.class.getClassLoader();
            }

            // 1. Check if Minecraft has finished initialization and loaded the first screen (net.minecraft.client.l8.ad or Ea)
            try {
                Class<?> l8Class = null;
                try {
                    l8Class = Class.forName("net.minecraft.client.l8", false, cl);
                } catch (Throwable ignored) {
                    try {
                        l8Class = Class.forName("cosmicclient.wb", false, cl);
                    } catch (Throwable ignored2) {}
                }

                if (l8Class != null) {
                    Method mcGetter = null;
                    try { mcGetter = l8Class.getMethod("w"); } catch (Throwable ignored) {
                        try { mcGetter = l8Class.getMethod("ap"); } catch (Throwable ignored2) {}
                    }
                    if (mcGetter != null) {
                        Object mc = mcGetter.invoke(null);
                        if (mc != null) {
                            Field adField = null;
                            try { adField = l8Class.getField("ad"); } catch (Throwable ignored) {
                                try { adField = l8Class.getDeclaredField("ak"); adField.setAccessible(true); } catch (Throwable ignored2) {}
                            }
                            if (adField != null && adField.get(mc) != null) {
                                isFinished = true;
                                System.out.println("[CosmicAgent] In-game startup complete (" + frameCount + " splash frames rendered). Main Menu active: " + adField.get(mc).getClass().getName());
                                return;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            // Safety limit: never render custom splash beyond 100 frames or 7.5 seconds
            if (frameCount > 100 || (System.currentTimeMillis() - startTime > 7500)) {
                isFinished = true;
                System.out.println("[CosmicAgent] In-game startup threshold reached. Main menu active.");
                return;
            }

            // 2. Un-minimize and focus window ONCE on frame 2 (without repeating on subsequent frames)
            if (frameCount == 2) {
                IconHelper.restoreAndFocusWindow();
                IconHelper.applyWindowIcon();
            }

            if (!loggedStart) {
                loggedStart = true;
                System.out.println("[CosmicAgent] Rendering authentic in-game Cosmic loading progress on Display...");
            }

            // 3. Render 4K background and authentic logo
            MainMenuHelper.renderCustomBackground(null);
            MainMenuHelper.renderCustomLogo(null);

            // 4. Render sleek modern glowing progress bar
            renderProgressBar(cl);

        } catch (Throwable t) {
            // Keep going silently so we never prevent game launch
        }
    }

    private static void renderProgressBar(ClassLoader cl) {
        try {
            Class<?> displayClass = MainMenuHelper.getDisplayClass(cl);
            if (displayClass == null) return;
            Method getWidthMethod = displayClass.getMethod("getWidth");
            Method getHeightMethod = displayClass.getMethod("getHeight");
            int width = ((Number) getWidthMethod.invoke(null)).intValue();
            int height = ((Number) getHeightMethod.invoke(null)).intValue();

            if (width <= 0 || height <= 0) return;

            Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11", true, cl);
            Method glPushAttrib = gl11.getMethod("glPushAttrib", int.class);
            Method glPopAttrib = gl11.getMethod("glPopAttrib");
            Method glMatrixMode = gl11.getMethod("glMatrixMode", int.class);
            Method glPushMatrix = gl11.getMethod("glPushMatrix");
            Method glPopMatrix = gl11.getMethod("glPopMatrix");
            Method glLoadIdentity = gl11.getMethod("glLoadIdentity");
            Method glOrtho = gl11.getMethod("glOrtho", double.class, double.class, double.class, double.class, double.class, double.class);
            Method glEnable = gl11.getMethod("glEnable", int.class);
            Method glDisable = gl11.getMethod("glDisable", int.class);
            Method glColor4f = gl11.getMethod("glColor4f", float.class, float.class, float.class, float.class);
            Method glBegin = gl11.getMethod("glBegin", int.class);
            Method glEnd = gl11.getMethod("glEnd");
            Method glVertex2f = gl11.getMethod("glVertex2f", float.class, float.class);
            Method glDepthMask = gl11.getMethod("glDepthMask", boolean.class);
            Method glBlendFunc = gl11.getMethod("glBlendFunc", int.class, int.class);

            int GL_ALL_ATTRIB_BITS = 0x000FFFFF;
            int GL_PROJECTION = 0x1701;
            int GL_MODELVIEW = 0x1700;
            int GL_TEXTURE_2D = 0x0DE1;
            int GL_DEPTH_TEST = 0x0B71;
            int GL_BLEND = 0x0BE2;
            int GL_SRC_ALPHA = 0x0302;
            int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
            int GL_QUADS = 0x0007;

            glPushAttrib.invoke(null, GL_ALL_ATTRIB_BITS);

            glMatrixMode.invoke(null, GL_PROJECTION);
            glPushMatrix.invoke(null);
            glLoadIdentity.invoke(null);
            glOrtho.invoke(null, 0.0, (double) width, (double) height, 0.0, -1000.0, 1000.0);

            glMatrixMode.invoke(null, GL_MODELVIEW);
            glPushMatrix.invoke(null);
            glLoadIdentity.invoke(null);

            glDisable.invoke(null, GL_TEXTURE_2D);
            glDisable.invoke(null, GL_DEPTH_TEST);
            glDepthMask.invoke(null, false);
            glEnable.invoke(null, GL_BLEND);
            glBlendFunc.invoke(null, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            // Progress bar dimensions
            float barW = Math.min(480.0f, width * 0.5f);
            float barH = 8.0f;
            float barX = (width - barW) / 2.0f;
            float barY = height * 0.72f;

            // Calculate progress (smoothly increments from 5% to 95%)
            long elapsed = System.currentTimeMillis() - startTime;
            float progress = Math.min(0.96f, 0.08f + (elapsed / 4500.0f) * 0.88f);

            // 1. Outer Dark Box / Track
            glColor4f.invoke(null, 0.06f, 0.08f, 0.14f, 0.85f);
            drawQuad(glBegin, glEnd, glVertex2f, GL_QUADS, barX - 2, barY - 2, barW + 4, barH + 4);

            // 2. Track Border
            glColor4f.invoke(null, 0.25f, 0.35f, 0.65f, 0.45f);
            drawHollowBox(glBegin, glEnd, glVertex2f, GL_QUADS, barX - 2, barY - 2, barW + 4, barH + 4, 1.0f);

            // 3. Glowing Progress Fill (Cosmic Cyan / Violet)
            float fillW = barW * progress;
            glColor4f.invoke(null, 0.22f, 0.75f, 1.0f, 0.95f);
            drawQuad(glBegin, glEnd, glVertex2f, GL_QUADS, barX, barY, fillW, barH);

            // 4. Glow Head / Indicator
            if (fillW > 4) {
                glColor4f.invoke(null, 1.0f, 1.0f, 1.0f, 0.9f);
                drawQuad(glBegin, glEnd, glVertex2f, GL_QUADS, barX + fillW - 3, barY, 3, barH);
            }

            glMatrixMode.invoke(null, GL_PROJECTION);
            glPopMatrix.invoke(null);
            glMatrixMode.invoke(null, GL_MODELVIEW);
            glPopMatrix.invoke(null);

            glPopAttrib.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private static void drawQuad(Method glBegin, Method glEnd, Method glVertex2f, int mode, float x, float y, float w, float h) throws Exception {
        glBegin.invoke(null, mode);
        glVertex2f.invoke(null, x, y);
        glVertex2f.invoke(null, x, y + h);
        glVertex2f.invoke(null, x + w, y + h);
        glVertex2f.invoke(null, x + w, y);
        glEnd.invoke(null);
    }

    private static void drawHollowBox(Method glBegin, Method glEnd, Method glVertex2f, int mode, float x, float y, float w, float h, float thickness) throws Exception {
        // Top
        drawQuad(glBegin, glEnd, glVertex2f, mode, x, y, w, thickness);
        // Bottom
        drawQuad(glBegin, glEnd, glVertex2f, mode, x, y + h - thickness, w, thickness);
        // Left
        drawQuad(glBegin, glEnd, glVertex2f, mode, x, y, thickness, h);
        // Right
        drawQuad(glBegin, glEnd, glVertex2f, mode, x + w - thickness, y, thickness, h);
    }
}
