package com.cosmic.launcher.agent;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class IconHelper {
    private static ByteBuffer[] cachedIcons = null;
    private static boolean applied = false;
    private static boolean win32Applied = false;

    public static synchronized ByteBuffer[] getOverrideIcons(ByteBuffer[] original) {
        if (cachedIcons != null && cachedIcons.length > 0) {
            return cachedIcons;
        }
        try {
            List<ByteBuffer> list = new ArrayList<>();
            int[] sizes = {16, 24, 32, 48, 64, 128, 256};
            for (int s : sizes) {
                BufferedImage img = loadIcon(s);
                if (img != null) {
                    list.add(toByteBuffer(img));
                }
            }

            if (!list.isEmpty()) {
                cachedIcons = list.toArray(new ByteBuffer[0]);
                System.out.println("[CosmicAgent] Applied authentic 3-Crystals window icon (" + cachedIcons.length + " sizes) to Display");
                return cachedIcons;
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Failed to prepare 3-crystals icon: " + t.getMessage());
        }
        return original;
    }

    public static void applyEarlyAppUserModelID() {
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                Shell32Lib.INSTANCE.SetCurrentProcessExplicitAppUserModelID(new WString("Cosmic.Client.1.8.9"));
                System.out.println("[CosmicAgent] Early AppUserModelID set: Cosmic.Client.1.8.9");
            }
        } catch (Throwable ignored) {}
    }

    public static void startWindowIconWatcher() {
        applyEarlyAppUserModelID();
        Thread watcher = new Thread(() -> {
            for (int i = 0; i < 100; i++) { // poll up to 30 seconds (faster at startup)
                try {
                    Thread.sleep(i < 30 ? 100 : 300);
                    if (applyWindowsTaskbarIcon()) {
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        }, "Cosmic-Icon-Watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static volatile boolean windowRestored = false;

    public static void restoreAndFocusWindow() {
        if (windowRestored) return;
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
            Pointer hwnd = User32Lib.INSTANCE.FindWindowA("LWJGL", null);
            if (hwnd == null) hwnd = User32Lib.INSTANCE.FindWindowA(null, "Cosmic Client 1.8.9");
            if (hwnd == null) hwnd = User32Lib.INSTANCE.FindWindowA(null, "Minecraft 1.8.9");
            if (hwnd != null) {
                // Strictly only un-minimize if the window is currently minimized (IsIconic).
                // Never call SW_RESTORE if the window is normal, maximized, or in fullscreen!
                if (User32Lib.INSTANCE.IsIconic(hwnd)) {
                    User32Lib.INSTANCE.ShowWindow(hwnd, 9 /* SW_RESTORE */);
                }
                User32Lib.INSTANCE.BringWindowToTop(hwnd);
                User32Lib.INSTANCE.SetForegroundWindow(hwnd);
                windowRestored = true;
            }
        } catch (Throwable ignored) {}
    }

    public static void applyWindowIcon() {
        if (applied && win32Applied) return;
        try {
            ByteBuffer[] icons = getOverrideIcons(null);
            if (icons != null && icons.length > 0) {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                Class<?> displayClass = MainMenuHelper.getDisplayClass(cl);
                if (displayClass != null) {
                    Method isCreated = displayClass.getMethod("isCreated");
                    if (Boolean.TRUE.equals(isCreated.invoke(null))) {
                        Method setIconMethod = displayClass.getMethod("setIcon", ByteBuffer[].class);
                        setIconMethod.invoke(null, (Object) icons);
                        if (!applied) {
                            applied = true;
                            System.out.println("[CosmicAgent] 3-Crystals application icon set on Display window!");
                        }
                    }
                }
            }

            // Apply native Win32 taskbar icon on Windows
            if (!win32Applied && System.getProperty("os.name", "").toLowerCase().contains("win")) {
                applyWindowsTaskbarIcon();
            }
        } catch (Throwable ignored) {
        }
    }

    private interface Shell32Lib extends Library {
        Shell32Lib INSTANCE = Native.load("shell32", Shell32Lib.class);
        int SetCurrentProcessExplicitAppUserModelID(WString appID);
    }

    private interface User32Lib extends Library {
        User32Lib INSTANCE = Native.load("user32", User32Lib.class);
        Pointer FindWindowA(String lpClassName, String lpWindowName);
        boolean PostMessageA(Pointer hWnd, int Msg, long wParam, long lParam);
        Pointer LoadImageA(Pointer hInst, String name, int type, int cx, int cy, int fuLoad);
        Pointer SetClassLongPtrA(Pointer hWnd, int nIndex, Pointer dwNewLong);
        int SetClassLongA(Pointer hWnd, int nIndex, int dwNewLong);
        boolean ShowWindow(Pointer hWnd, int nCmdShow);
        boolean SetForegroundWindow(Pointer hWnd);
        boolean BringWindowToTop(Pointer hWnd);
        boolean IsIconic(Pointer hWnd);
    }

    public static boolean applyWindowsTaskbarIcon() {
        if (win32Applied) return true;
        try {
            // 1. Detach process grouping from java.exe to custom Cosmic Client AppUserModelID
            try {
                Shell32Lib.INSTANCE.SetCurrentProcessExplicitAppUserModelID(new WString("Cosmic.Client.1.8.9"));
            } catch (Throwable ignored) {}

            // 2. Find the window handle
            Pointer hwnd = User32Lib.INSTANCE.FindWindowA("LWJGL", null);
            if (hwnd == null) {
                hwnd = User32Lib.INSTANCE.FindWindowA(null, "Cosmic Client 1.8.9");
            }
            if (hwnd == null) {
                hwnd = User32Lib.INSTANCE.FindWindowA(null, "Minecraft 1.8.9");
            }
            if (hwnd == null) {
                return false; // Window not created yet
            }

            // Only un-minimize if the window was launched minimized (IsIconic).
            // Never repeatedly force restore or steal foreground if the user is interacting or in fullscreen!
            try {
                if (User32Lib.INSTANCE.IsIconic(hwnd)) {
                    User32Lib.INSTANCE.ShowWindow(hwnd, 9 /* SW_RESTORE */);
                }
            } catch (Throwable ignored) {}

            // 3. Resolve the .ico file
            File icoFile = findIcoFile();
            if (icoFile == null || !icoFile.exists()) {
                return false;
            }

            int IMAGE_ICON = 1;
            int LR_LOADFROMFILE = 0x0010;
            int LR_DEFAULTSIZE = 0x0040;
            int WM_SETICON = 0x0080;
            int ICON_SMALL = 0;
            int ICON_BIG = 1;
            int GCLP_HICON = -14;
            int GCLP_HICONSM = -34;

            Pointer hIconBig = User32Lib.INSTANCE.LoadImageA(null, icoFile.getAbsolutePath(), IMAGE_ICON, 256, 256, LR_LOADFROMFILE);
            if (hIconBig == null) {
                hIconBig = User32Lib.INSTANCE.LoadImageA(null, icoFile.getAbsolutePath(), IMAGE_ICON, 0, 0, LR_LOADFROMFILE | LR_DEFAULTSIZE);
            }
            Pointer hIconSm = User32Lib.INSTANCE.LoadImageA(null, icoFile.getAbsolutePath(), IMAGE_ICON, 32, 32, LR_LOADFROMFILE);
            if (hIconSm == null) {
                hIconSm = hIconBig;
            }

            if (hIconBig != null) {
                long bigVal = Pointer.nativeValue(hIconBig);
                long smVal = (hIconSm != null) ? Pointer.nativeValue(hIconSm) : bigVal;

                // Post WM_SETICON messages to the window (asynchronous - NEVER deadlocks)
                User32Lib.INSTANCE.PostMessageA(hwnd, WM_SETICON, ICON_BIG, bigVal);
                User32Lib.INSTANCE.PostMessageA(hwnd, WM_SETICON, ICON_SMALL, smVal);

                // Set class icon
                try {
                    User32Lib.INSTANCE.SetClassLongPtrA(hwnd, GCLP_HICON, hIconBig);
                    if (hIconSm != null) {
                        User32Lib.INSTANCE.SetClassLongPtrA(hwnd, GCLP_HICONSM, hIconSm);
                    }
                } catch (Throwable ignored) {
                    try {
                        User32Lib.INSTANCE.SetClassLongA(hwnd, GCLP_HICON, (int) bigVal);
                    } catch (Throwable ignored2) {}
                }

                win32Applied = true;
                System.out.println("[CosmicAgent] Authentic Cosmic Client taskbar & window icon applied via Win32 native subsystem! Path: " + icoFile.getAbsolutePath());
                return true;
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Windows taskbar icon integration error: " + t.getMessage());
        }
        return false;
    }

    private static File findIcoFile() {
        try {
            String appData = System.getenv("APPDATA");
            List<File> candidates = new ArrayList<>();
            if (appData != null) {
                candidates.add(new File(appData, ".minecraft/cosmic/cosmic.ico"));
                candidates.add(new File(appData, ".minecraft/cosmic/cosmic_crystals.ico"));
            }
            candidates.add(new File("CosmicClient-x64/cosmic.ico"));
            candidates.add(new File("CosmicClient-x64/cosmic_crystals.ico"));
            candidates.add(new File("cosmic.ico"));
            candidates.add(new File("cosmic_crystals.ico"));
            candidates.add(new File("scratch/cosmic_crystals.ico"));

            for (File f : candidates) {
                if (f.exists() && f.length() > 0) return f;
            }

            // Extract from classpath resource if available
            InputStream is = IconHelper.class.getResourceAsStream("/cosmic.ico");
            if (is == null) is = IconHelper.class.getResourceAsStream("/cosmic_crystals.ico");
            if (is != null) {
                try {
                    File temp = File.createTempFile("cosmic_icon_", ".ico");
                    temp.deleteOnExit();
                    try (FileOutputStream fos = new FileOutputStream(temp)) {
                        byte[] buf = new byte[4096];
                        int r;
                        while ((r = is.read(buf)) != -1) {
                            fos.write(buf, 0, r);
                        }
                    }
                    return temp;
                } finally {
                    is.close();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static BufferedImage loadIcon(int size) {
        try {
            // Check direct crystal PNG files
            File f = new File("scratch/crystal_" + size + "x" + size + ".png");
            if (f.exists() && f.length() > 0) return ImageIO.read(f);

            File f2 = new File("scratch/ico_" + size + "x" + size + ".png");
            if (f2.exists() && f2.length() > 0) return ImageIO.read(f2);

            File f3 = new File("CosmicClient-x64/icons/icon_" + size + "x" + size + ".png");
            if (f3.exists() && f3.length() > 0) return ImageIO.read(f3);

            InputStream is = IconHelper.class.getResourceAsStream("/icons/icon_" + size + "x" + size + ".png");
            if (is == null) is = IconHelper.class.getResourceAsStream("/assets/minecraft/icons/icon_" + size + "x" + size + ".png");
            if (is == null) is = IconHelper.class.getResourceAsStream("/assets/minecraft/cosmic/icons/dock-lg-" + size + ".png");
            if (size == 16 && is == null) is = IconHelper.class.getResourceAsStream("/cosmic-icon-16.png");
            if (size == 32 && is == null) is = IconHelper.class.getResourceAsStream("/cosmic-icon-32.png");
            if (is != null) {
                try {
                    return ImageIO.read(is);
                } finally {
                    is.close();
                }
            }

            // Fallback: scale down largest available crystal icon
            File fLarge = new File("scratch/crystal_256x256.png");
            if (fLarge.exists() && fLarge.length() > 0) {
                BufferedImage big = ImageIO.read(fLarge);
                if (big != null) {
                    BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = scaled.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.drawImage(big, 0, 0, size, size, null);
                    g.dispose();
                    return scaled;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ByteBuffer toByteBuffer(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);
        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = pixels[y * w + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                buffer.put((byte) (pixel & 0xFF));         // B
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
            }
        }
        buffer.flip();
        return buffer;
    }
}
