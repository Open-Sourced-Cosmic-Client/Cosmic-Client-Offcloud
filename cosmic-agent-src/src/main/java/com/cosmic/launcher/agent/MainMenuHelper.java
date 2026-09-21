package com.cosmic.launcher.agent;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MainMenuHelper {
    private static boolean logged = false;

    public static void interceptResource(Object paramObject1, Object paramObject2) {
        String url = null;
        try {
            Method getURL = paramObject2.getClass().getMethod("getURL", new Class[0]);
            url = (String) getURL.invoke(paramObject2, new Object[0]);
        } catch (Exception ignored) {
        }

        if (url == null) {
            return;
        }

        try {
            String resourcePath = null;
            if (url.contains("cosmic/textures/")) {
                int idx = url.indexOf("cosmic/textures/");
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
                InputStream is = MainMenuHelper.class.getResourceAsStream(resourcePath);
                if (is != null) {
                    Field fieldC = paramObject1.getClass().getDeclaredField("c");
                    fieldC.setAccessible(true);
                    fieldC.set(paramObject1, is);

                    Field fieldA = paramObject1.getClass().getDeclaredField("a");
                    fieldA.setAccessible(true);
                    fieldA.setBoolean(paramObject1, true);

                    if (!logged) {
                        logged = true;
                        System.out.println("[CosmicAgent] In-Game Menu Interceptor Active: " + resourcePath);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }
}
