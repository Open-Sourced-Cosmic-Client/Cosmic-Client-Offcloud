package com.cosmic.launcher.agent;

import com.cosmic.launcher.agent.VoiceAudioEngine;
import com.cosmic.launcher.asm.ClassWriter;
import com.cosmic.launcher.asm.MethodVisitor;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VoiceModuleHelper {
    private static boolean injected = false;
    private static Object savedVoiceMod = null;
    private static Class<?> savedWgClass = null;

    private static Object theUnsafe = null;
    private static Method allocateInstanceMethod = null;
    private static Method defineClassMethod = null;

    static {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field f = unsafeClass.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            theUnsafe = f.get(null);
            allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class.class);
            try {
                defineClassMethod = unsafeClass.getMethod("defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE, ClassLoader.class, ProtectionDomain.class);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static Object allocateInstance(Class<?> clazz) throws Exception {
        if (theUnsafe != null && allocateInstanceMethod != null) {
            return allocateInstanceMethod.invoke(theUnsafe, clazz);
        }
        Constructor<?> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    public static void injectVoiceModule(ClassLoader ccLoader) {
        try {
            Class<?> ugClass = Class.forName("cosmicclient.ug", true, ccLoader);
            Object ug = ugClass.getMethod("i", new Class[0]).invoke(null, new Object[0]);
            Object qbInstance = ugClass.getMethod("E", new Class[0]).invoke(ug, new Object[0]);
            if (qbInstance == null) {
                VoiceModuleHelper.log("QB is null");
                return;
            }
            VoiceModuleHelper.onModuleManagerInit(qbInstance);
        }
        catch (Exception e) {
            VoiceModuleHelper.log("injectVoiceModule error: " + e.getMessage());
        }
    }

    /*
     * WARNING - void declaration
     */
    public static void onModuleManagerInit(Object qbInstance) {
        if (injected) {
            return;
        }
        injected = true;
        VoiceModuleHelper.log("onModuleManagerInit called");
        try {
            Object voiceModule;
            Class<?> wgClass;
            List moduleList;
            block83: {
                Class voiceClass;
                ClassLoader ccLoader = qbInstance.getClass().getClassLoader();
                VoiceModuleHelper.log("CC classloader: " + ccLoader.getClass().getName());
                Field listField = null;
                Class<?> cls = qbInstance.getClass();
                while (cls != null && listField == null) {
                    try {
                        listField = cls.getDeclaredField("c");
                    }
                    catch (NoSuchFieldException e) {
                        cls = cls.getSuperclass();
                    }
                }
                if (listField == null) {
                    VoiceModuleHelper.log("ERROR: Could not find QE.c field");
                    return;
                }
                listField.setAccessible(true);
                moduleList = (List)listField.get(qbInstance);
                if (moduleList == null || moduleList.isEmpty()) {
                    VoiceModuleHelper.log("ERROR: Module list empty");
                    return;
                }
                VoiceModuleHelper.log("Module list has " + moduleList.size() + " modules");
                wgClass = Class.forName("cosmicclient.Wg", true, ccLoader);
                Object templateModule = moduleList.get(0);
                byte[] classBytes = VoiceModuleHelper.generateVoiceModuleClass();
                try {
                    if (theUnsafe != null && defineClassMethod != null) {
                        voiceClass = (Class<?>) defineClassMethod.invoke(theUnsafe, "cosmicclient.VoiceChatModule", classBytes, 0, classBytes.length, ccLoader, null);
                        VoiceModuleHelper.log("Defined VoiceChatModule class via Unsafe.defineClass");
                    } else {
                        throw new NoSuchMethodException("defineClass not available on Unsafe");
                    }
                }
                catch (Exception e) {
                    VoiceModuleHelper.log("Unsafe.defineClass failed: " + e.getMessage() + ", trying ClassLoader.defineClass");
                    try {
                        Method defMethod = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE);
                        defMethod.setAccessible(true);
                        voiceClass = (Class<?>) defMethod.invoke(ccLoader, "cosmicclient.VoiceChatModule", classBytes, 0, classBytes.length);
                        VoiceModuleHelper.log("Defined VoiceChatModule class via ClassLoader.defineClass");
                    }
                    catch (Exception e2) {
                        VoiceModuleHelper.log("ClassLoader.defineClass failed: " + e2.getMessage());
                        VoiceModuleHelper.log("Falling back to allocateInstance with unique approach");
                        voiceClass = Class.forName("cosmicclient.WN", true, ccLoader);
                        VoiceModuleHelper.log("Using WN as voice module class");
                    }
                }
                voiceModule = allocateInstance(voiceClass);
                VoiceModuleHelper.log("Created voice module instance: " + voiceModule.getClass().getName());
                VoiceModuleHelper.copyFields(templateModule, voiceModule, wgClass);
                VoiceModuleHelper.initListField(wgClass, voiceModule, "h");
                VoiceModuleHelper.initListField(wgClass, voiceModule, "j");
                VoiceModuleHelper.initListField(wgClass, voiceModule, "g");
                VoiceModuleHelper.initListField(wgClass, voiceModule, "f");
                VoiceModuleHelper.setField(wgClass, voiceModule, "q", "Voice Chat");
                VoiceModuleHelper.setField(wgClass, voiceModule, "d", "voicechat");
                VoiceModuleHelper.setBoolField(wgClass, voiceModule, "k", true);
                try {
                    Field lField = wgClass.getDeclaredField("l");
                    lField.setAccessible(true);
                    lField.set(voiceModule, new String[]{"voicechat"});
                }
                catch (Exception e) {
                    VoiceModuleHelper.log("Could not set l field: " + e.getMessage());
                }
                VoiceModuleHelper.log("Set fields: q='Voice Chat', d='voicechat'");
                try {
                    Class<?> qbC = qbInstance.getClass();
                    Field pField = qbC.getDeclaredField("P");
                    pField.setAccessible(true);
                    Object templateMod = pField.get(qbInstance);
                    if (templateMod != null) {
                        VoiceModuleHelper.log("Using " + templateMod.getClass().getName() + " as rendering template");
                        Object voiceMod = allocateInstance(voiceClass);
                        VoiceModuleHelper.copyFields(templateMod, voiceMod, wgClass);
                        VoiceModuleHelper.setField(wgClass, voiceMod, "q", "Voice Chat");
                        VoiceModuleHelper.setField(wgClass, voiceMod, "d", "voicechat");
                        VoiceModuleHelper.setBoolField(wgClass, voiceMod, "k", true);
                        VoiceModuleHelper.initListField(wgClass, voiceMod, "h");
                        VoiceModuleHelper.initListField(wgClass, voiceMod, "j");
                        VoiceModuleHelper.initListField(wgClass, voiceMod, "g");
                        VoiceModuleHelper.initListField(wgClass, voiceMod, "f");
                        try {
                            Field lField = wgClass.getDeclaredField("l");
                            lField.setAccessible(true);
                            lField.set(voiceMod, new String[]{"voicechat"});
                        }
                        catch (Exception lField) {
                            // empty catch block
                        }
                        moduleList.add(voiceMod);
                        VoiceModuleHelper.log("ADDED Voice Chat! Class=" + voiceMod.getClass().getName() + " Total=" + moduleList.size());
                        try {
                            Field hf;
                            Field hField = wgClass.getDeclaredField("h");
                            hField.setAccessible(true);
                            List templateH = (List)hField.get(templateMod);
                            Field gFieldWg = wgClass.getDeclaredField("g");
                            gFieldWg.setAccessible(true);
                            List templateG = (List)gFieldWg.get(templateMod);
                            Field fFieldWg = wgClass.getDeclaredField("f");
                            fFieldWg.setAccessible(true);
                            List templateF = (List)fFieldWg.get(templateMod);
                            if (templateH != null && templateH.size() > 0) {
                                Object templateCheckbox = templateH.get(0);
                                Class<?> apxClass = templateCheckbox.getClass();
                                Object enableToggle = allocateInstance(apxClass);
                                for (Class<?> walk = apxClass; walk != null && !walk.getName().equals("java.lang.Object"); walk = walk.getSuperclass()) {
                                    for (Field ff : walk.getDeclaredFields()) {
                                        if (Modifier.isStatic(ff.getModifiers())) continue;
                                        ff.setAccessible(true);
                                        try {
                                            ff.set(enableToggle, ff.get(templateCheckbox));
                                        }
                                        catch (Exception exception) {
                                            // empty catch block
                                        }
                                    }
                                }
                                VoiceModuleHelper.setFieldOnHierarchy(apxClass, enableToggle, "c", "Enable Voice Chat");
                                VoiceModuleHelper.setFieldOnHierarchy(apxClass, enableToggle, "g", "voicechat_enabled");
                                VoiceModuleHelper.setFieldOnHierarchy(apxClass, enableToggle, "b", "");
                                boolean savedEnabled = VoiceAudioEngine.getSavedEnabled();
                                try {
                                    Class<?> walk = apxClass;
                                    while (walk != null && !walk.getName().equals("java.lang.Object")) {
                                        for (Field ff : walk.getDeclaredFields()) {
                                            if (Modifier.isStatic(ff.getModifiers()) || !ff.getType().getName().contains("Comparable")) continue;
                                            ff.setAccessible(true);
                                            ff.set(enableToggle, savedEnabled);
                                        }
                                        walk = walk.getSuperclass();
                                    }
                                }
                                catch (Exception exception) {
                                    // empty catch block
                                }
                                VoiceModuleHelper.log("Enable toggle set to saved value: " + savedEnabled);
                                ArrayList<Object> arrayList = new ArrayList<Object>();
                                arrayList.add(enableToggle);
                                hField.set(voiceMod, arrayList);
                                VoiceModuleHelper.log("Created Enable Voice Chat toggle (aPX)");
                                Object safeToggle = allocateInstance(apxClass);
                                Class<?> walkT = apxClass;
                                while (walkT != null && !walkT.getName().equals("java.lang.Object")) {
                                    for (Field ff : walkT.getDeclaredFields()) {
                                        if (Modifier.isStatic(ff.getModifiers())) continue;
                                        ff.setAccessible(true);
                                        try {
                                            ff.set(safeToggle, ff.get(templateCheckbox));
                                        }
                                        catch (Exception exception) {
                                            // empty catch block
                                        }
                                    }
                                    walkT = walkT.getSuperclass();
                                }
                                VoiceModuleHelper.setComparableFields(safeToggle, Boolean.TRUE);
                                VoiceModuleHelper.setModuleOwner(safeToggle, voiceMod);
                                Field uField = wgClass.getDeclaredField("u");
                                uField.setAccessible(true);
                                uField.set(voiceMod, safeToggle);
                                VoiceAudioEngine.setMainToggle(safeToggle);
                                VoiceModuleHelper.log("Replaced main toggle with safe aPX (class=" + apxClass.getSimpleName() + ", not aPZ)");
                            }
                            if (templateG != null && templateG.size() > 0) {
                                Object templateKeybind = templateG.get(0);
                                Class<?> apiClass = templateKeybind.getClass();
                                int savedKey = VoiceAudioEngine.getSavedKeyCode();
                                Object pttKeybind = null;
                                try {
                                    Constructor<?> apiCtor = apiClass.getDeclaredConstructor(String.class, Integer.TYPE, String.class);
                                    apiCtor.setAccessible(true);
                                    pttKeybind = apiCtor.newInstance("Push to Talk", savedKey, "Cosmic Client - Voice Chat");
                                    VoiceModuleHelper.log("Created aPI via constructor (key=" + savedKey + ") - registered in aPg.m!");
                                }
                                catch (Exception ctorErr) {
                                    VoiceModuleHelper.log("aPI constructor failed (" + ctorErr.getMessage() + "), trying other constructors...");
                                    for (Constructor<?> c : apiClass.getDeclaredConstructors()) {
                                        VoiceModuleHelper.log("  aPI ctor: " + Arrays.toString(c.getParameterTypes()));
                                    }
                                    pttKeybind = allocateInstance(apiClass);
                                    Class<?> walk = apiClass;
                                    while (walk != null && !walk.getName().equals("java.lang.Object")) {
                                        for (Field ff : walk.getDeclaredFields()) {
                                            if (Modifier.isStatic(ff.getModifiers())) continue;
                                            ff.setAccessible(true);
                                            try {
                                                ff.set(pttKeybind, ff.get(templateKeybind));
                                            }
                                            catch (Exception exception) {
                                                // empty catch block
                                            }
                                        }
                                        walk = walk.getSuperclass();
                                    }
                                    VoiceModuleHelper.setFieldOnHierarchy(apiClass, pttKeybind, "c", "Push to Talk");
                                }
                                if (savedKey != 0) {
                                    VoiceModuleHelper.setIntFieldOnHierarchy(apiClass, pttKeybind, "p", savedKey);
                                    VoiceModuleHelper.setIntFieldOnHierarchy(apiClass, pttKeybind, "k", savedKey);
                                    VoiceModuleHelper.setComparableFields(pttKeybind, savedKey);
                                    VoiceModuleHelper.log("Set all key fields to " + savedKey + " (p, k, and aPj Comparables)");
                                }
                                try {
                                    Method bMethod = null;
                                    for (Method m : pttKeybind.getClass().getMethods()) {
                                        if (!m.getName().equals("b") || m.getParameterCount() != 1 || m.getParameterTypes()[0] != Integer.TYPE) continue;
                                        bMethod = m;
                                        break;
                                    }
                                    if (bMethod != null && savedKey != 0) {
                                        bMethod.invoke(pttKeybind, savedKey);
                                        VoiceModuleHelper.log("Called aPI.b(" + savedKey + ") for aPg sync");
                                    }
                                }
                                catch (Exception e) {
                                    VoiceModuleHelper.log("b(int) call error: " + e.getMessage());
                                }
                                try {
                                    Method dMethod = null;
                                    for (Method m : pttKeybind.getClass().getMethods()) {
                                        if (!m.getName().equals("d") || m.getParameterCount() != 0 || m.getReturnType() == Void.TYPE || m.getReturnType() == Boolean.TYPE) continue;
                                        dMethod = m;
                                        break;
                                    }
                                    Method lMethod = null;
                                    for (Method m : pttKeybind.getClass().getMethods()) {
                                        if (!m.getName().equals("l") || m.getParameterCount() != 0 || (m.getReturnType() != Integer.TYPE && m.getReturnType() != Integer.class)) continue;
                                        lMethod = m;
                                        break;
                                    }
                                    String dVal = dMethod != null ? String.valueOf(dMethod.invoke(pttKeybind, new Object[0])) : "N/A";
                                    String lVal = lMethod != null ? String.valueOf(lMethod.invoke(pttKeybind, new Object[0])) : "N/A";
                                    VoiceModuleHelper.log("Keybind state: d()=" + String.valueOf(dVal) + " l()=" + String.valueOf(lVal) + " (should both be " + savedKey + ")");
                                }
                                catch (Exception e) {
                                    VoiceModuleHelper.log("Debug read error: " + e.getMessage());
                                }
                                ArrayList<Object> voiceG = new ArrayList<Object>();
                                voiceG.add(pttKeybind);
                                gFieldWg.set(voiceMod, voiceG);
                                VoiceAudioEngine.setPrimaryPttKeybind(pttKeybind);
                                VoiceModuleHelper.log("Created Push to Talk keybind (aPI) - shows in MC controls + CC GUI");
                            }
                            if (templateF != null) {
                                fFieldWg.set(voiceMod, new ArrayList(templateF));
                            }
                            try {
                                Field rField = qbInstance.getClass().getDeclaredField("r");
                                rField.setAccessible(true);
                                Object roamMod = rField.get(qbInstance);
                                if (roamMod != null) {
                                    Field xField = roamMod.getClass().getDeclaredField("x");
                                    xField.setAccessible(true);
                                    Object templateSlider = xField.get(roamMod);
                                    if (templateSlider != null) {
                                        Object volumeSlider = allocateInstance(templateSlider.getClass());
                                        Class<?> walk = templateSlider.getClass();
                                        while (walk != null && !walk.getName().equals("java.lang.Object")) {
                                            for (Field ff : walk.getDeclaredFields()) {
                                                if (Modifier.isStatic(ff.getModifiers())) continue;
                                                ff.setAccessible(true);
                                                try {
                                                    ff.set(volumeSlider, ff.get(templateSlider));
                                                }
                                                catch (Exception exception) {
                                                    // empty catch block
                                                }
                                            }
                                            walk = walk.getSuperclass();
                                        }
                                        VoiceModuleHelper.setFieldOnHierarchy(templateSlider.getClass(), volumeSlider, "c", "Volume");
                                        VoiceModuleHelper.setFieldOnHierarchy(templateSlider.getClass(), volumeSlider, "g", "voicechat_volume");
                                        VoiceModuleHelper.setFieldOnHierarchy(templateSlider.getClass(), volumeSlider, "b", "");
                                        ArrayList<Field> numFields = new ArrayList<Field>();
                                        for (Field ff : volumeSlider.getClass().getDeclaredFields()) {
                                            if (Modifier.isStatic(ff.getModifiers())) continue;
                                            ff.setAccessible(true);
                                            if (!(ff.get(volumeSlider) instanceof Number)) continue;
                                            numFields.add(ff);
                                        }
                                        if (numFields.size() >= 2) {
                                            ((Field)numFields.get(0)).set(volumeSlider, 0.0);
                                            ((Field)numFields.get(1)).set(volumeSlider, 100.0);
                                        }
                                        float savedVol = VoiceAudioEngine.getSavedVolume();
                                        VoiceModuleHelper.setComparableFields(volumeSlider, savedVol);
                                        Field hField2 = wgClass.getDeclaredField("h");
                                        hField2.setAccessible(true);
                                        ArrayList<Object> voiceH2 = (ArrayList<Object>)hField2.get(voiceMod);
                                        if (voiceH2 == null) {
                                            voiceH2 = new ArrayList<Object>();
                                        }
                                        voiceH2.add(volumeSlider);
                                        hField2.set(voiceMod, voiceH2);
                                        VoiceAudioEngine.setVoiceModule(null, volumeSlider);
                                        VoiceModuleHelper.log("Added Volume slider (0-100, val=" + savedVol + ")");
                                    }
                                }
                            }
                            catch (Exception e) {
                                VoiceModuleHelper.log("Volume slider error: " + e.getMessage());
                            }
                            try {
                                int di;
                                Class<?> apxComboClass = Class.forName("cosmicclient.aPx", true, ccLoader);
                                Constructor<?> apxCtor = apxComboClass.getDeclaredConstructor(String.class, String.class, String.class, String[].class);
                                apxCtor.setAccessible(true);
                                int inCount = VoiceAudioEngine.getInputDeviceCount();
                                int outCount = VoiceAudioEngine.getOutputDeviceCount();
                                String[] inDevices = new String[Math.max(1, inCount)];
                                String[] stringArray = new String[Math.max(1, outCount)];
                                for (di = 0; di < inCount; ++di) {
                                    inDevices[di] = VoiceAudioEngine.getInputDeviceName(di);
                                }
                                for (di = 0; di < outCount; ++di) {
                                    stringArray[di] = VoiceAudioEngine.getOutputDeviceName(di);
                                }
                                if (inCount == 0) {
                                    inDevices[0] = "Default";
                                }
                                if (outCount == 0) {
                                    stringArray[0] = "Default";
                                }
                                String inDefault = VoiceAudioEngine.getInputDeviceName(VoiceAudioEngine.getSavedInputIndex());
                                String outDefault = VoiceAudioEngine.getOutputDeviceName(VoiceAudioEngine.getSavedOutputIndex());
                                Object micCombo = apxCtor.newInstance("Microphone", "Microphone", inDefault, inDevices);
                                Object spkCombo = apxCtor.newInstance("Speaker", "Speaker", outDefault, stringArray);
                                Field hField3 = wgClass.getDeclaredField("h");
                                hField3.setAccessible(true);
                                ArrayList voiceH3 = (ArrayList)hField3.get(voiceMod);
                                if (voiceH3 == null) {
                                    voiceH3 = new ArrayList();
                                    hField3.set(voiceMod, voiceH3);
                                }
                                voiceH3.add(micCombo);
                                voiceH3.add(spkCombo);
                                VoiceAudioEngine.setDeviceComboboxes(micCombo, spkCombo);
                                VoiceModuleHelper.log("Created aPx comboboxes via constructor: mic (" + inCount + " devices), spk (" + outCount + " devices)");
                            }
                            catch (Exception e) {
                                VoiceModuleHelper.log("Device combobox error: " + e.getMessage());
                                e.printStackTrace();
                            }
                            try {
                                hf = wgClass.getDeclaredField("h");
                                hf.setAccessible(true);
                                List hItems = (List)hf.get(voiceMod);
                                if (hItems != null) {
                                    for (Object item : hItems) {
                                        VoiceModuleHelper.setModuleOwner(item, voiceMod);
                                    }
                                }
                                Field gf = wgClass.getDeclaredField("g");
                                gf.setAccessible(true);
                                List gItems = (List)gf.get(voiceMod);
                                if (gItems != null) {
                                    for (Object e : gItems) {
                                        VoiceModuleHelper.setModuleOwner(e, voiceMod);
                                    }
                                }
                                VoiceModuleHelper.log("Set module owner on all " + (hItems != null ? hItems.size() : 0) + " h items");
                            }
                            catch (Exception e) {
                                VoiceModuleHelper.log("Set owner error: " + e.getMessage());
                            }
                            VoiceModuleHelper.log("Voice settings created successfully!");
                            try {
                                hf = wgClass.getDeclaredField("h");
                                hf.setAccessible(true);
                                List hList = (List)hf.get(voiceMod);
                                Object enableRef = hList != null && hList.size() > 0 ? hList.get(0) : null;
                                VoiceAudioEngine.setVoiceModule(enableRef, null);
                            }
                            catch (Exception e2) {
                                VoiceModuleHelper.log("UI link error: " + e2.getMessage());
                            }
                            savedVoiceMod = voiceMod;
                            savedWgClass = wgClass;
                            try {
                                VoiceModuleHelper.loadConfigFromProfile(voiceMod, wgClass);
                            }
                            catch (Exception e2) {
                                VoiceModuleHelper.log("Config load error: " + e2.getMessage());
                            }
                        }
                        catch (Exception e) {
                            VoiceModuleHelper.log("Settings creation error: " + e.getMessage());
                        }
                        Object fVoiceMod = voiceMod;
                        Class<?> fWgClass = wgClass;
                        new Thread(() -> {
                            try {
                                Thread.sleep(5000L);
                                VoiceModuleHelper.log("=== DELAYED: Adding PTT to MC keybind arrays ===");
                                try {
                                    Class<?> wbClass = Class.forName("cosmicclient.wb", true, ccLoader);
                                    Object wbInst = wbClass.getMethod("ap", new Class[0]).invoke(null, new Object[0]);
                                    if (wbInst != null) {
                                        Field aaField = wbClass.getDeclaredField("aa");
                                        aaField.setAccessible(true);
                                        Object jyInst = aaField.get(wbInst);
                                        if (jyInst != null) {
                                            Field gFieldWg2 = fWgClass.getDeclaredField("g");
                                            gFieldWg2.setAccessible(true);
                                            List voiceKeybinds = (List)gFieldWg2.get(fVoiceMod);
                                            if (voiceKeybinds != null && voiceKeybinds.size() > 0) {
                                                Object pttKey = voiceKeybinds.get(0);
                                                for (String arrayFieldName : new String[]{"aA", "ak"}) {
                                                    try {
                                                        Field arrField = jyInst.getClass().getDeclaredField(arrayFieldName);
                                                        arrField.setAccessible(true);
                                                        Object[] oldArr = (Object[])arrField.get(jyInst);
                                                        if (oldArr == null) continue;
                                                        boolean found = false;
                                                        for (Object k : oldArr) {
                                                            if (k != pttKey) continue;
                                                            found = true;
                                                            break;
                                                        }
                                                        if (found) continue;
                                                        Object[] newArr = Arrays.copyOf(oldArr, oldArr.length + 1);
                                                        newArr[oldArr.length] = pttKey;
                                                        arrField.set(jyInst, newArr);
                                                        VoiceModuleHelper.log("Added PTT to jy." + arrayFieldName + " (now " + newArr.length + " keybinds)");
                                                    }
                                                    catch (NoSuchFieldException arrField) {
                                                    }
                                                    catch (Exception e) {
                                                        VoiceModuleHelper.log("Add to jy." + arrayFieldName + " error: " + e.getMessage());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                catch (Exception e) {
                                    VoiceModuleHelper.log("MC keybind array error: " + e.getMessage());
                                }
                                VoiceModuleHelper.log("=== DELAYED SETUP COMPLETE ===");
                            }
                            catch (Exception e) {
                                VoiceModuleHelper.log("Delayed setup error: " + e.getMessage());
                            }
                        }, "VoiceSetup").start();
                        break block83;
                    }
                    VoiceModuleHelper.log("QB.P is null, scheduling delayed add");
                    Class fVoiceClass = voiceClass;
                    new Thread(() -> {
                        try {
                            Thread.sleep(5000L);
                            Object delayed = pField.get(qbInstance);
                            if (delayed != null) {
                                Object vm = allocateInstance(fVoiceClass);
                                VoiceModuleHelper.copyFields(delayed, vm, wgClass);
                                VoiceModuleHelper.setField(wgClass, vm, "q", "Voice Chat");
                                VoiceModuleHelper.setField(wgClass, vm, "d", "voicechat");
                                VoiceModuleHelper.setBoolField(wgClass, vm, "k", true);
                                VoiceModuleHelper.initListField(wgClass, vm, "h");
                                VoiceModuleHelper.initListField(wgClass, vm, "j");
                                VoiceModuleHelper.initListField(wgClass, vm, "g");
                                VoiceModuleHelper.initListField(wgClass, vm, "f");
                                moduleList.add(vm);
                                VoiceModuleHelper.log("Delayed ADD! Total=" + moduleList.size());
                            }
                        }
                        catch (Exception e) {
                            VoiceModuleHelper.log("Delayed add error: " + e.getMessage());
                        }
                    }).start();
                }
                catch (Exception e) {
                    VoiceModuleHelper.log("Voice module add error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            VoiceModuleHelper.log("Module class: " + voiceModule.getClass().getName());
            for (int i = 0; i < moduleList.size(); ++i) {
                Object mod = moduleList.get(i);
                try {
                    Field qField = wgClass.getDeclaredField("q");
                    qField.setAccessible(true);
                    String name = (String)qField.get(mod);
                    Field dField = wgClass.getDeclaredField("d");
                    dField.setAccessible(true);
                    String id = (String)dField.get(mod);
                    if (i < moduleList.size() - 3) continue;
                    VoiceModuleHelper.log("  Module[" + i + "] class=" + mod.getClass().getSimpleName() + " q='" + name + "' d='" + id + "'");
                    continue;
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            System.out.println("[CosmicAgent/Voice] Voice Chat module injected! (" + moduleList.size() + " total modules)");
        }
        catch (Exception e) {
            VoiceModuleHelper.log("ERROR: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static byte[] generateVoiceModuleClass() {
        ClassWriter cw = new ClassWriter(1);
        cw.visit(52, 1, "cosmicclient/VoiceChatModule", null, "cosmicclient/Wg", null);
        MethodVisitor mv = cw.visitMethod(1, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(25, 0);
        mv.visitMethodInsn(183, "cosmicclient/Wg", "<init>", "()V", false);
        mv.visitInsn(177);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        MethodVisitor mv2 = cw.visitMethod(4, "a", "()Lcom/google/common/collect/ImmutableList;", null, null);
        mv2.visitCode();
        mv2.visitMethodInsn(184, "com/google/common/collect/ImmutableList", "of", "()Lcom/google/common/collect/ImmutableList;", false);
        mv2.visitInsn(176);
        mv2.visitMaxs(1, 1);
        mv2.visitEnd();
        MethodVisitor mv3 = cw.visitMethod(4, "g", "(IIS)Ljava/util/List;", null, null);
        mv3.visitCode();
        mv3.visitMethodInsn(184, "java/util/Collections", "emptyList", "()Ljava/util/List;", false);
        mv3.visitInsn(176);
        mv3.visitMaxs(1, 3);
        mv3.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void copyFields(Object src, Object dst, Class<?> cls) {
        try {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                try {
                    f.set(dst, f.get(src));
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private static void setField(Class<?> cls, Object obj, String name, String value) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        }
        catch (Exception e) {
            VoiceModuleHelper.log("setField " + name + ": " + e.getMessage());
        }
    }

    private static void setBoolField(Class<?> cls, Object obj, String name, boolean value) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(obj, value);
        }
        catch (Exception e) {
            VoiceModuleHelper.log("setBoolField " + name + ": " + e.getMessage());
        }
    }

    private static void setFieldOnHierarchy(Class<?> startClass, Object obj, String fieldName, String value) {
        for (Class<?> cls = startClass; cls != null && !cls.getName().equals("java.lang.Object"); cls = cls.getSuperclass()) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(obj, value);
                return;
            }
            catch (NoSuchFieldException e) {
                continue;
            }
            catch (Exception e) {
                return;
            }
        }
    }

    private static void setIntFieldOnHierarchy(Class<?> startClass, Object obj, String fieldName, int value) {
        for (Class<?> cls = startClass; cls != null && !cls.getName().equals("java.lang.Object"); cls = cls.getSuperclass()) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.setInt(obj, value);
                return;
            }
            catch (NoSuchFieldException e) {
                continue;
            }
            catch (Exception e) {
                return;
            }
        }
    }

    private static void setModuleOwner(Object setting, Object module) {
        for (Class<?> cls = setting.getClass(); cls != null && !cls.getName().equals("java.lang.Object"); cls = cls.getSuperclass()) {
            try {
                Field f = cls.getDeclaredField("f");
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Class<?> ft = f.getType();
                if (!ft.isInterface() && !ft.getName().equals("cosmicclient._o") && ft != Object.class) continue;
                f.set(setting, module);
                return;
            }
            catch (NoSuchFieldException f) {
                continue;
            }
            catch (Exception e) {
                return;
            }
        }
    }

    private static void setComparableFields(Object obj, Object value) {
        for (Class<?> cls = obj.getClass(); cls != null && !cls.getName().equals("java.lang.Object"); cls = cls.getSuperclass()) {
            for (Field ff : cls.getDeclaredFields()) {
                if (Modifier.isStatic(ff.getModifiers())) continue;
                try {
                    String typeName = ff.getType().getName();
                    if (!typeName.contains("Comparable") && !typeName.equals("java.lang.Object")) continue;
                    ff.setAccessible(true);
                    Object existing = ff.get(obj);
                    if (existing != null && existing.getClass() != value.getClass()) continue;
                    ff.set(obj, value);
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }
    }

    private static void initListField(Class<?> cls, Object obj, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            if (f.get(obj) == null) {
                f.set(obj, new ArrayList());
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private static File getProfileDir() {
        try {
            File[] dirs;
            File profilesDir;
            String content;
            int idx;
            File mcDir = new File(System.getProperty("user.dir"));
            File cosmicDir = new File(mcDir, "cosmic");
            if (!cosmicDir.exists()) {
                cosmicDir = new File(System.getenv("APPDATA") + "/.minecraft/cosmic");
            }
            if (!cosmicDir.exists()) {
                return null;
            }
            File profileJson = new File(cosmicDir, "profile.json");
            if (profileJson.exists() && (idx = (content = new String(Files.readAllBytes(profileJson.toPath()))).indexOf("\"name\"")) >= 0) {
                String profileName;
                File profileDir;
                int start = content.indexOf(34, idx + 6);
                int end = content.indexOf(34, start + 1);
                if (start >= 0 && end > start && (profileDir = new File(cosmicDir, "profiles/" + (profileName = content.substring(start + 1, end)))).exists()) {
                    return profileDir;
                }
            }
            if ((profilesDir = new File(cosmicDir, "profiles")).exists() && (dirs = profilesDir.listFiles(File::isDirectory)) != null && dirs.length > 0) {
                Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                return dirs[0];
            }
        }
        catch (Exception e) {
            VoiceModuleHelper.log("getProfileDir error: " + e.getMessage());
        }
        return null;
    }

    private static void loadConfigFromProfile(Object voiceMod, Class<?> wgClass) {
        File profileDir = VoiceModuleHelper.getProfileDir();
        if (profileDir == null) {
            VoiceModuleHelper.log("No profile directory found");
            return;
        }
        File configFile = new File(profileDir, "voicechat.json");
        if (!configFile.exists()) {
            VoiceModuleHelper.log("No voicechat.json in " + profileDir.getName() + " - will create on first save");
            VoiceModuleHelper.saveConfigToProfile(voiceMod, wgClass);
            return;
        }
        try {
            String propsBlock;
            String content;
            block17: {
                String keycodeStr;
                content = new String(Files.readAllBytes(configFile.toPath()));
                VoiceModuleHelper.log("Loading config from " + configFile.getAbsolutePath());
                String enabled = VoiceModuleHelper.jsonGetString(content, "enabled");
                if (enabled != null) {
                    boolean en = Boolean.parseBoolean(enabled);
                    VoiceModuleHelper.setBoolField(wgClass, voiceMod, "k", en);
                    try {
                        Field uField = wgClass.getDeclaredField("u");
                        uField.setAccessible(true);
                        Object toggle = uField.get(voiceMod);
                        if (toggle != null) {
                            VoiceModuleHelper.setComparableFields(toggle, en);
                        }
                    }
                    catch (Exception uField) {
                        // empty catch block
                    }
                    VoiceModuleHelper.log("  enabled=" + en);
                }
                if ((keycodeStr = VoiceModuleHelper.jsonGetString(content, "keycode")) != null) {
                    try {
                        int keycode = Integer.parseInt(keycodeStr.trim());
                        if (keycode == 0) break block17;
                        Field gf = wgClass.getDeclaredField("g");
                        gf.setAccessible(true);
                        List gList = (List)gf.get(voiceMod);
                        if (gList == null || gList.size() <= 0) break block17;
                        Object kb = gList.get(0);
                        VoiceModuleHelper.setIntFieldOnHierarchy(kb.getClass(), kb, "p", keycode);
                        VoiceModuleHelper.setIntFieldOnHierarchy(kb.getClass(), kb, "k", keycode);
                        VoiceModuleHelper.setComparableFields(kb, keycode);
                        try {
                            for (Method m : kb.getClass().getMethods()) {
                                if (!m.getName().equals("b") || m.getParameterCount() != 1 || m.getParameterTypes()[0] != Integer.TYPE) continue;
                                m.invoke(kb, keycode);
                                break;
                            }
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                        VoiceAudioEngine.setPrimaryPttKeybind(kb);
                        VoiceModuleHelper.log("  keycode=" + keycode);
                    }
                    catch (NumberFormatException keycode) {
                        // empty catch block
                    }
                }
            }
            if ((propsBlock = VoiceModuleHelper.jsonGetObject(content, "properties")) != null) {
                Field hf = wgClass.getDeclaredField("h");
                hf.setAccessible(true);
                List hList = (List)hf.get(voiceMod);
                if (hList != null) {
                    for (Object setting : hList) {
                        String value;
                        String key = VoiceModuleHelper.getSettingKey(setting);
                        if (key == null || (value = VoiceModuleHelper.jsonGetString(propsBlock, key)) == null) continue;
                        VoiceModuleHelper.applySettingValue(setting, value);
                        VoiceModuleHelper.log("  " + key + "=" + value);
                    }
                }
            }
            VoiceModuleHelper.log("Config loaded from profile: " + profileDir.getName());
        }
        catch (Exception e) {
            VoiceModuleHelper.log("Config load error: " + e.getMessage());
        }
    }

    public static void saveConfigToProfile(Object voiceMod, Class<?> wgClass) {
        File profileDir = VoiceModuleHelper.getProfileDir();
        if (profileDir == null) {
            return;
        }
        try {
            int keybindCode;
            StringBuilder json;
            block13: {
                json = new StringBuilder();
                json.append("{\n");
                json.append("  \"mod\": \"voicechat\",\n");
                keybindCode = 0;
                try {
                    Field gf = wgClass.getDeclaredField("g");
                    gf.setAccessible(true);
                    List gList = (List)gf.get(voiceMod);
                    if (gList == null || gList.size() <= 0) break block13;
                    Object kb = gList.get(0);
                    for (Class<?> kbCls = kb.getClass(); kbCls != null && !kbCls.getName().equals("java.lang.Object"); kbCls = kbCls.getSuperclass()) {
                        try {
                            Field pf = kbCls.getDeclaredField("p");
                            if (pf.getType() != Integer.TYPE) continue;
                            pf.setAccessible(true);
                            keybindCode = pf.getInt(kb);
                            break;
                        }
                        catch (NoSuchFieldException pf) {
                            // empty catch block
                        }
                    }
                }
                catch (Exception gf) {
                    // empty catch block
                }
            }
            json.append("  \"keycode\": ").append(keybindCode).append(",\n");
            boolean enabled = true;
            try {
                Field kField = wgClass.getDeclaredField("k");
                kField.setAccessible(true);
                enabled = kField.getBoolean(voiceMod);
            }
            catch (Exception kField) {
                // empty catch block
            }
            json.append("  \"enabled\": \"").append(enabled).append("\",\n");
            json.append("  \"position\": \"NONE\",\n");
            json.append("  \"x\": 0.0,\n");
            json.append("  \"y\": 0.0,\n");
            json.append("  \"properties\": {\n");
            Field hf = wgClass.getDeclaredField("h");
            hf.setAccessible(true);
            List hList = (List)hf.get(voiceMod);
            boolean first = true;
            if (hList != null) {
                for (Object setting : hList) {
                    String key = VoiceModuleHelper.getSettingKey(setting);
                    String value = VoiceModuleHelper.getSettingValue(setting);
                    if (key == null || value == null) continue;
                    if (!first) {
                        json.append(",\n");
                    }
                    json.append("    \"").append(key).append("\": \"").append(VoiceModuleHelper.escapeJson(value)).append("\"");
                    first = false;
                }
            }
            json.append("\n  },\n");
            json.append("  \"advancedProperties\": {}\n");
            json.append("}\n");
            File configFile = new File(profileDir, "voicechat.json");
            Files.write(configFile.toPath(), json.toString().getBytes(), new OpenOption[0]);
            VoiceModuleHelper.log("Saved config to " + configFile.getAbsolutePath());
        }
        catch (Exception e) {
            VoiceModuleHelper.log("Config save error: " + e.getMessage());
        }
    }

    private static String getSettingKey(Object setting) {
        try {
            for (Method m : setting.getClass().getMethods()) {
                if (!m.getName().equals("m") || m.getParameterCount() != 0 || m.getReturnType() != String.class) continue;
                return (String)m.invoke(setting, new Object[0]);
            }
            for (Class<?> cls = setting.getClass(); cls != null && !cls.getName().equals("java.lang.Object"); cls = cls.getSuperclass()) {
                try {
                    Field f = cls.getDeclaredField("g");
                    if (f.getType() != String.class) continue;
                    f.setAccessible(true);
                    return (String)f.get(setting);
                }
                catch (NoSuchFieldException noSuchFieldException) {
                    // empty catch block
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    private static String getSettingValue(Object setting) {
        try {
            for (Method m : setting.getClass().getMethods()) {
                Class<?> rt;
                if (!m.getName().equals("d") || m.getParameterCount() != 0 || (rt = m.getReturnType()) == Void.TYPE || rt == Boolean.TYPE || rt == List.class) continue;
                Object val = m.invoke(setting, new Object[0]);
                return val != null ? val.toString() : null;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    private static void applySettingValue(Object setting, String value) {
        try {
            Class<?> cls = setting.getClass();
            String className = cls.getSimpleName();
            if (className.equals("aPX") || className.equals("aPq")) {
                VoiceModuleHelper.setComparableFields(setting, Boolean.parseBoolean(value));
            } else if (className.equals("aPi") || className.equals("aPp")) {
                try {
                    VoiceModuleHelper.setComparableFields(setting, Double.parseDouble(value));
                }
                catch (NumberFormatException e) {
                    VoiceModuleHelper.setComparableFields(setting, value);
                }
            } else {
                VoiceModuleHelper.setComparableFields(setting, value);
            }
        }
        catch (Exception e) {
            VoiceModuleHelper.log("applySettingValue error: " + e.getMessage());
        }
    }

    private static String jsonGetString(String json, String key) {
        int end;
        int start;
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(58, idx + search.length());
        if (colon < 0) {
            return null;
        }
        for (start = colon + 1; start < json.length() && json.charAt(start) == ' '; ++start) {
        }
        if (start >= json.length()) {
            return null;
        }
        if (json.charAt(start) == '\"' && (end = json.indexOf(34, start + 1)) > start) {
            return json.substring(start + 1, end);
        }
        for (end = start; end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != '\n'; ++end) {
        }
        return json.substring(start, end).trim();
    }

    private static String jsonGetObject(String json, String key) {
        int pos;
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) {
            return null;
        }
        int braceStart = json.indexOf(123, idx);
        if (braceStart < 0) {
            return null;
        }
        int depth = 1;
        for (pos = braceStart + 1; pos < json.length() && depth > 0; ++pos) {
            if (json.charAt(pos) == '{') {
                ++depth;
                continue;
            }
            if (json.charAt(pos) != '}') continue;
            --depth;
        }
        return json.substring(braceStart, pos);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static void saveConfig() {
        if (savedVoiceMod != null && savedWgClass != null) {
            VoiceModuleHelper.saveConfigToProfile(savedVoiceMod, savedWgClass);
        }
    }

    public static void log(String msg) {
        System.out.println("[CosmicAgent/VoiceModule] " + msg);
    }
}

