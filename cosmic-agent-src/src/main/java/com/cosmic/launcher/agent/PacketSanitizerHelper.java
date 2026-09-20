package com.cosmic.launcher.agent;

public class PacketSanitizerHelper {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object safeValueOf(Class<?> enumClass, String name) {
        if (enumClass == null || name == null) {
            return null;
        }
        try {
            return Enum.valueOf((Class<Enum>) enumClass, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String getSafeCosmicVersion() {
        try {
            Class<?> ugClass = Class.forName("cosmicclient.ug");
            java.lang.reflect.Method m = ugClass.getMethod("i");
            Object ugInst = m.invoke(null);
            if (ugInst != null) {
                java.lang.reflect.Method zMethod = ugClass.getMethod("z");
                Object ver = zMethod.invoke(ugInst);
                if (ver instanceof String && !((String) ver).isEmpty()) {
                    return (String) ver;
                }
            }
        } catch (Throwable ignored) {
        }
        String prop = System.getProperty("cosmic.version");
        if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
        }
        return "2.7.0.b84ff";
    }
}
