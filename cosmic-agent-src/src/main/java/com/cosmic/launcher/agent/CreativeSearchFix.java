package com.cosmic.launcher.agent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CreativeSearchFix {
    private static Field searchField;
    private static Method isFocused;
    private static boolean initDone;
    private static boolean initFailed;
    private static final int KEY_BACKSPACE = 14;
    private static final int KEY_DELETE = 211;
    private static final int KEY_LEFT = 203;
    private static final int KEY_RIGHT = 205;
    private static final int KEY_HOME = 199;
    private static final int KEY_END = 207;
    private static final int KEY_A = 30;
    private static final int KEY_C = 46;
    private static final int KEY_V = 47;
    private static final int KEY_X = 45;

    public static boolean handle(Object gui, char typedChar, int keyCode) {
        try {
            if (initFailed) {
                return false;
            }
            if (!initDone) {
                CreativeSearchFix.init(gui);
            }
            if (initFailed) {
                return false;
            }
            Object field = searchField.get(gui);
            if (field == null) {
                return false;
            }
            boolean focused = (Boolean)isFocused.invoke(field, new Object[0]);
            if (!focused) {
                return false;
            }
            if (keyCode == 0) {
                return false;
            }
            if (keyCode == 1) {
                return false;
            }
            return !CreativeSearchFix.isEditingKey(keyCode);
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent] CreativeSearchFix error: " + e.getMessage());
            initFailed = true;
            return false;
        }
    }

    private static boolean isEditingKey(int keyCode) {
        return keyCode == 14 || keyCode == 211 || keyCode == 203 || keyCode == 205 || keyCode == 199 || keyCode == 207 || keyCode == 30 || keyCode == 46 || keyCode == 47 || keyCode == 45;
    }

    private static void init(Object gui) {
        try {
            Class<?> f0Class = gui.getClass();
            searchField = f0Class.getDeclaredField("aa");
            searchField.setAccessible(true);
            Class<?> giClass = searchField.getType();
            isFocused = giClass.getMethod("f", new Class[0]);
            initDone = true;
            System.out.println("[CosmicAgent] CreativeSearchFix initialized");
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent] CreativeSearchFix init failed: " + e.getMessage());
            initFailed = true;
        }
    }

    static {
        initDone = false;
        initFailed = false;
    }
}

