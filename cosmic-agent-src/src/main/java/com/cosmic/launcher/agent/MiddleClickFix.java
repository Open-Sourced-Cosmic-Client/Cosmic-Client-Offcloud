package com.cosmic.launcher.agent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MiddleClickFix {
    private static boolean initDone = false;
    private static boolean initFailed = false;
    private static Field slotUnderMouseField;
    private static Method handleMouseClickMethod;

    public static boolean handleMiddleClick(Object gui, int button) {
        if (button != 2) {
            return false;
        }
        try {
            Object slot;
            if (initFailed) {
                return false;
            }
            if (!initDone) {
                Class<?> fuClass;
                Class<?> cls = fuClass = gui.getClass();
                while (cls != null && slotUnderMouseField == null) {
                    try {
                        slotUnderMouseField = cls.getDeclaredField("K");
                        slotUnderMouseField.setAccessible(true);
                    }
                    catch (NoSuchFieldException e) {
                        cls = cls.getSuperclass();
                    }
                }
                if (slotUnderMouseField == null) {
                    System.err.println("[CosmicAgent] MiddleClickFix: could not find slot field K");
                    initFailed = true;
                    return false;
                }
                Class<?> slotClass = slotUnderMouseField.getType();
                block7: for (cls = gui.getClass(); cls != null && handleMouseClickMethod == null; cls = cls.getSuperclass()) {
                    for (Method m : cls.getDeclaredMethods()) {
                        Class<?>[] params = m.getParameterTypes();
                        if (!m.getName().equals("a") || params.length != 7 || params[0] != Character.TYPE || params[1] != slotClass || params[2] != Integer.TYPE || params[3] != Integer.TYPE || params[4] != Short.TYPE || params[5] != Integer.TYPE || params[6] != Integer.TYPE) continue;
                        handleMouseClickMethod = m;
                        handleMouseClickMethod.setAccessible(true);
                        continue block7;
                    }
                }
                if (handleMouseClickMethod == null) {
                    System.err.println("[CosmicAgent] MiddleClickFix: could not find handleMouseClick method");
                    initFailed = true;
                    return false;
                }
                initDone = true;
                System.out.println("[CosmicAgent] MiddleClickFix initialized: slotField=" + slotUnderMouseField.getDeclaringClass().getSimpleName() + ".K handleClick=" + handleMouseClickMethod.getDeclaringClass().getSimpleName() + ".a");
            }
            if ((slot = slotUnderMouseField.get(gui)) == null) {
                return false;
            }
            int slotId = -1;
            try {
                Field eField = slot.getClass().getDeclaredField("e");
                eField.setAccessible(true);
                slotId = eField.getInt(slot);
            }
            catch (Exception e) {
                return false;
            }
            handleMouseClickMethod.invoke(gui, Character.valueOf('\u0000'), slot, slotId, 0, (short)0, 2, 3);
            System.out.println("[CosmicAgent] MiddleClickFix: sent pick-block click for slot " + slotId);
            return true;
        }
        catch (Exception e) {
            System.err.println("[CosmicAgent] MiddleClickFix error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

