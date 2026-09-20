package com.cosmic.launcher.agent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InGameMenuHelper {
    private static boolean lastMenuKeyDown = false;
    private static long lastTriggerTime = 0L;
    private static long screenOpenTime = 0L;

    // LWJGL 2 Key constants
    private static final int KEY_RETURN = 28;
    private static final int KEY_LSHIFT = 42;
    private static final int KEY_RSHIFT = 54;
    private static final int KEY_NUMPADENTER = 156;

    /**
     * Called on each client game tick from wb.B(JI)V.
     */
    public static void handleInGameTick(Object mc) {
        if (mc == null) return;
        try {
            ClassLoader cl = mc.getClass().getClassLoader();
            Class<?> keyboardClass = Class.forName("org.lwjgl.input.Keyboard", true, cl);
            Method isCreated = keyboardClass.getMethod("isCreated");
            if (!Boolean.TRUE.equals(isCreated.invoke(null))) {
                return;
            }

            Method isKeyDownMethod = keyboardClass.getMethod("isKeyDown", int.class);
            boolean lShift = Boolean.TRUE.equals(isKeyDownMethod.invoke(null, KEY_LSHIFT));
            boolean rShift = Boolean.TRUE.equals(isKeyDownMethod.invoke(null, KEY_RSHIFT));
            boolean enter = Boolean.TRUE.equals(isKeyDownMethod.invoke(null, KEY_RETURN)) || 
                            Boolean.TRUE.equals(isKeyDownMethod.invoke(null, KEY_NUMPADENTER));

            // Shift+Enter OR Right Shift (official key 54)
            boolean menuKeyDown = (rShift || ((lShift || rShift) && enter));
            long now = System.currentTimeMillis();

            if (menuKeyDown && !lastMenuKeyDown && (now - lastTriggerTime > 250L)) {
                lastTriggerTime = now;
                toggleModMenu(mc, cl);
            }
            lastMenuKeyDown = menuKeyDown;
        } catch (Throwable ignored) {
        }
    }

    private static void toggleModMenu(Object mc, ClassLoader cl) {
        try {
            Class<?> wbClass = mc.getClass();
            Field akField = wbClass.getDeclaredField("ak");
            akField.setAccessible(true);
            Object currentScreen = akField.get(mc);

            Class<?> evClass = Class.forName("cosmicclient.Ev", true, cl);
            Class<?> e9Class = Class.forName("cosmicclient.E9", true, cl);

            if (currentScreen != null && e9Class.isInstance(currentScreen)) {
                long now = System.currentTimeMillis();
                if (screenOpenTime == 0L) {
                    screenOpenTime = now;
                    return;
                }
                if (now - screenOpenTime < 250L) {
                    return;
                }
                // Currently open in E9: close it (return to in-game)
                screenOpenTime = 0L;
                displayScreen(mc, wbClass, evClass, null);
                System.out.println("[CosmicAgent] Shift/Menu in-game: Closed Mod Suite menu.");
            } else if (currentScreen == null) {
                // In-game: open the authentic Cosmic Client Mod Suite GUI (cosmicclient.E9)
                Constructor<?> ctor = e9Class.getDeclaredConstructor();
                ctor.setAccessible(true);
                Object e9Screen = ctor.newInstance();

                // Clean Shift menu: remove Forum & Store, rename Cosmic PVP to Help
                ShiftMenuHelper.cleanShiftMenu(e9Screen);

                screenOpenTime = System.currentTimeMillis();
                displayScreen(mc, wbClass, evClass, e9Screen);
                System.out.println("[CosmicAgent] Shift/Menu in-game: Opened authentic Cosmic Client Mod Suite & Settings GUI (E9)!");
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Error opening in-game mod edit menu: " + t.getMessage());
        }
    }

    private static void displayScreen(Object mc, Class<?> wbClass, Class<?> evClass, Object screen) {
        try {
            for (Method m : wbClass.getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(evClass)) {
                    m.setAccessible(true);
                    m.invoke(mc, screen);
                    return;
                }
            }

            for (Method m : wbClass.getMethods()) {
                if (m.getParameterCount() >= 1 && m.getParameterTypes()[0].isAssignableFrom(evClass)) {
                    m.setAccessible(true);
                    Object[] args = new Object[m.getParameterCount()];
                    args[0] = screen;
                    for (int i = 1; i < args.length; i++) {
                        Class<?> pt = m.getParameterTypes()[i];
                        if (pt == int.class) args[i] = 0;
                        else if (pt == char.class) args[i] = (char) 0;
                        else if (pt == byte.class) args[i] = (byte) 0;
                        else if (pt == short.class) args[i] = (short) 0;
                        else args[i] = null;
                    }
                    m.invoke(mc, args);
                    return;
                }
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] displayScreen error: " + t.getMessage());
        }
    }
}
