package com.cosmic.launcher.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ShiftMenuHelper {

    private static Field aField = null;
    private static Field nField = null;
    private static Field oField = null;

    /**
     * Sanitizes category name string (e.g. "Cosmic PvP" -> "Help").
     */
    public static String sanitizeCategoryName(String name) {
        if (isCosmicPvP(name)) {
            return "Help";
        }
        return name;
    }

    /**
     * Filters a subcategory array before ImmutableList.copyOf is invoked.
     */
    public static Object[] filterSubcategoryArray(Object[] subs) {
        if (subs == null) return null;
        List<Object> result = new ArrayList<Object>();
        for (Object sub : subs) {
            if (sub == null) continue;
            ensureFields(sub.getClass());
            String name = getCategoryName(sub);
            Object action = getCategoryAction(sub);

            if (isStoreOrForumItem(name, action)) {
                System.out.println("[CosmicAgent] ShiftMenu: Filtered out subcategory '" + name + "' (Forum/Store)");
                continue;
            }

            if (isCosmicPvP(name)) {
                setCategoryName(sub, "Help");
            }
            result.add(sub);
        }
        Object[] outArray = (Object[]) Array.newInstance(subs.getClass().getComponentType(), result.size());
        return result.toArray(outArray);
    }

    /**
     * Cleans the shift menu (E9) instance:
     * 1. Removes top-level categories for Store and Forums.
     * 2. Renames "Cosmic PvP" / "Cosmic PVP" category to "Help".
     * 3. Removes Store and Forums subcategories within remaining categories.
     */
    public static void cleanShiftMenu(Object e9Screen) {
        if (e9Screen == null) return;
        try {
            Class<?> e9Class = e9Screen.getClass();
            Field gField = null;
            for (Class<?> c = e9Class; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    gField = c.getDeclaredField("G");
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            if (gField == null) return;
            gField.setAccessible(true);
            List<?> categories = (List<?>) gField.get(e9Screen);
            if (categories == null) return;

            Iterator<?> it = categories.iterator();
            while (it.hasNext()) {
                Object cat = it.next();
                if (cat == null) continue;

                ensureFields(cat.getClass());

                String name = getCategoryName(cat);
                Object action = getCategoryAction(cat);

                if (isStoreOrForumItem(name, action)) {
                    System.out.println("[CosmicAgent] ShiftMenu: Removed category tab '" + name + "' (Forum/Store)");
                    it.remove();
                    continue;
                }

                if (isCosmicPvP(name)) {
                    System.out.println("[CosmicAgent] ShiftMenu: Renamed category '" + name + "' -> 'Help'");
                    setCategoryName(cat, "Help");
                }

                // Filter subcategories list (kR.n)
                if (nField != null) {
                    List<?> subcategories = (List<?>) nField.get(cat);
                    if (subcategories != null && !subcategories.isEmpty()) {
                        List<Object> filteredSubs = new ArrayList<Object>();
                        boolean modified = false;

                        for (Object sub : subcategories) {
                            if (sub == null) continue;
                            String subName = getCategoryName(sub);
                            Object subAction = getCategoryAction(sub);

                            if (isStoreOrForumItem(subName, subAction)) {
                                System.out.println("[CosmicAgent] ShiftMenu: Removed subcategory '" + subName + "' from '" + name + "'");
                                modified = true;
                                continue;
                            }

                            if (isCosmicPvP(subName)) {
                                System.out.println("[CosmicAgent] ShiftMenu: Renamed subcategory '" + subName + "' -> 'Help'");
                                setCategoryName(sub, "Help");
                                modified = true;
                            }
                            filteredSubs.add(sub);
                        }

                        if (modified || filteredSubs.size() != subcategories.size()) {
                            try {
                                Class<?> immListClass = Class.forName("com.google.common.collect.ImmutableList", true, cat.getClass().getClassLoader());
                                Method copyOfMethod = immListClass.getMethod("copyOf", Collection.class);
                                Object newImmList = copyOfMethod.invoke(null, filteredSubs);
                                nField.set(cat, newImmList);
                            } catch (Throwable t) {
                                nField.set(cat, Collections.unmodifiableList(filteredSubs));
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] ShiftMenu clean error: " + t.getMessage());
        }
    }

    private static void ensureFields(Class<?> kRClass) {
        if (aField == null && kRClass != null) {
            try { aField = kRClass.getDeclaredField("a"); aField.setAccessible(true); } catch (Throwable ignored) {}
            try { nField = kRClass.getDeclaredField("n"); nField.setAccessible(true); } catch (Throwable ignored) {}
            try { oField = kRClass.getDeclaredField("o"); oField.setAccessible(true); } catch (Throwable ignored) {}
        }
    }

    private static String getCategoryName(Object cat) {
        if (cat == null || aField == null) return null;
        try {
            return (String) aField.get(cat);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setCategoryName(Object cat, String name) {
        if (cat == null || aField == null) return;
        try {
            aField.set(cat, name);
        } catch (Throwable ignored) {}
    }

    private static Object getCategoryAction(Object cat) {
        if (cat == null || oField == null) return null;
        try {
            return oField.get(cat);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isStoreOrForumItem(String name, Object action) {
        if (name != null) {
            String lower = name.trim().toLowerCase();
            if (lower.contains("store") || lower.contains("forum")) {
                return true;
            }
        }
        if (action != null) {
            String actClass = action.getClass().getName();
            if (actClass.endsWith(".D7") || actClass.equals("D7")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCosmicPvP(String name) {
        if (name == null) return false;
        String clean = name.trim();
        return clean.equalsIgnoreCase("Cosmic PvP") || clean.equalsIgnoreCase("Cosmic PVP");
    }
}
