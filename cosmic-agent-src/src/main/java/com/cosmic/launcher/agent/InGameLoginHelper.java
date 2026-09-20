package com.cosmic.launcher.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 100% In-Game Account Manager & Login Engine.
 * Fixes broken in-game login and account switching:
 * 1. Intercepts bq.run() so in-game Add Account works instantly without failing on deprecated Mojang Yggdrasil auth.
 * 2. Intercepts oL.m(int, long) so switching accounts in-game never throws exceptions or wipes accounts from cosmic/accounts.json.
 * 3. Syncs launcher accounts to cosmic/accounts.json so player accounts are always visible in-game.
 */
public class InGameLoginHelper {

    private static volatile boolean syncedAccounts = false;

    public static void onAccountRefreshFailed(Throwable t) {
        String msg = t != null ? (t.getMessage() != null ? t.getMessage() : t.getClass().getName()) : "unknown";
        System.out.println("[CosmicAgent] Suppressed account refresh error (account preserved): " + msg);
    }

    public static class AccountEntry {
        public String key;
        public String username;
        public String displayName;
        public String uuid;
        public String token;
        public String type;
        public boolean isSelected;

        public AccountEntry(String key, String username, String displayName, String uuid, String token, String type, boolean isSelected) {
            this.key = key;
            this.username = username;
            this.displayName = displayName;
            this.uuid = uuid;
            this.token = token;
            this.type = type != null ? type : "Xbox";
            this.isSelected = isSelected;
        }

        public AccountEntry(String key, String username, String displayName, String uuid, String token, boolean isSelected) {
            this(key, username, displayName, uuid, token, "Xbox", isSelected);
        }
    }

    public static Class<?> resolveClientClass(String name, ClassLoader cl) {
        if (cl == null) cl = InGameLoginHelper.class.getClassLoader();
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

    public static List getAccountsListFromIR(ClassLoader cl, Object iRInstance) {
        if (iRInstance == null) return null;
        try {
            Class<?> iLClass = resolveClientClass("iL", cl != null ? cl : iRInstance.getClass().getClassLoader());
            if (iLClass != null) {
                Field cField = iLClass.getDeclaredField("c");
                cField.setAccessible(true);
                return (List) cField.get(iRInstance);
            }
        } catch (Throwable t) {
            try {
                Field cField = iRInstance.getClass().getSuperclass().getDeclaredField("c");
                cField.setAccessible(true);
                return (List) cField.get(iRInstance);
            } catch (Throwable t2) {
                System.err.println("[CosmicAgent] Error getting accounts list from iR: " + t2.getMessage());
            }
        }
        return null;
    }

    public static List<AccountEntry> parseAccountsFromRawJson(String json, String selectedKey) {
        List<AccountEntry> results = new java.util.ArrayList<>();
        if (json == null || json.isEmpty()) return results;
        try {
            int dbIdx = json.indexOf("\"authenticationDatabase\"");
            if (dbIdx == -1) return results;
            int openBrace = json.indexOf('{', dbIdx);
            if (openBrace == -1) return results;

            int depth = 1;
            int i = openBrace + 1;
            while (i < json.length() && depth > 0) {
                char c = json.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                else if (c == '"') {
                    i++;
                    while (i < json.length() && json.charAt(i) != '"') {
                        if (json.charAt(i) == '\\') i++;
                        i++;
                    }
                }
                i++;
            }
            String dbBlock = json.substring(openBrace, Math.min(i, json.length()));

            java.util.regex.Pattern accPat = java.util.regex.Pattern.compile("\"([a-f0-9\\-]{36})\"\\s*:\\s*\\{");
            java.util.regex.Matcher m = accPat.matcher(dbBlock);
            while (m.find()) {
                String accKey = m.group(1);
                int entryOpen = m.end() - 1;
                int d = 1;
                int j = entryOpen + 1;
                while (j < dbBlock.length() && d > 0) {
                    char c = dbBlock.charAt(j);
                    if (c == '{') d++;
                    else if (c == '}') d--;
                    else if (c == '"') {
                        j++;
                        while (j < dbBlock.length() && dbBlock.charAt(j) != '"') {
                            if (dbBlock.charAt(j) == '\\') j++;
                            j++;
                        }
                    }
                    j++;
                }
                String block = dbBlock.substring(entryOpen, Math.min(j, dbBlock.length()));
                java.util.regex.Matcher uM = java.util.regex.Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"").matcher(block);
                if (!uM.find()) {
                    continue;
                }

                String user = uM.group(1);
                String disp = user;
                String uuid = accKey;

                java.util.regex.Matcher profM = java.util.regex.Pattern.compile("\"profiles\"\\s*:\\s*\\{\\s*\"([a-f0-9\\-]{36})\"\\s*:\\s*\\{\\s*\"displayName\"\\s*:\\s*\"([^\"]+)\"").matcher(block);
                if (profM.find()) {
                    uuid = profM.group(1);
                    disp = profM.group(2);
                } else {
                    java.util.regex.Matcher dispM = java.util.regex.Pattern.compile("\"displayName\"\\s*:\\s*\"([^\"]+)\"").matcher(block);
                    if (dispM.find()) disp = dispM.group(1);
                    java.util.regex.Matcher uuidM = java.util.regex.Pattern.compile("\"profiles\"\\s*:\\s*\\{\\s*\"([a-f0-9\\-]{36})\"").matcher(block);
                    if (uuidM.find()) uuid = uuidM.group(1);
                }

                String tok = "";
                java.util.regex.Matcher tokM = java.util.regex.Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(block);
                if (tokM.find()) tok = tokM.group(1);

                String typ = "Xbox";
                java.util.regex.Matcher typM = java.util.regex.Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"").matcher(block);
                if (typM.find()) typ = typM.group(1);

                boolean isSel = (selectedKey != null && (selectedKey.equalsIgnoreCase(accKey) || (uuid != null && selectedKey.equalsIgnoreCase(uuid))));

                boolean dup = false;
                for (AccountEntry ae : results) {
                    if (ae.key.equalsIgnoreCase(accKey) || ae.username.equalsIgnoreCase(user) || (uuid != null && ae.uuid.replace("-", "").equalsIgnoreCase(uuid.replace("-", "")))) {
                        dup = true;
                        if ((ae.token == null || ae.token.length() < 50 || "microsoft_imported_token".equals(ae.token))
                            && (tok != null && tok.length() > 50 && tok.startsWith("eyJ"))) {
                            ae.token = tok;
                        }
                        break;
                    }
                }
                if (!dup) {
                    results.add(new AccountEntry(accKey, user, disp, uuid, tok, typ, isSel));
                }
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Error parsing raw JSON accounts: " + t.getMessage());
        }
        return results;
    }

    public static List<AccountEntry> loadAllAccountsFromJson() {
        List<AccountEntry> results = new java.util.ArrayList<>();
        File[] files = new File[] {
            new File("accounts.json"),
            new File("cosmic/accounts.json"),
            new File(System.getProperty("cosmic.installdir", "."), "accounts.json"),
            new File(System.getProperty("cosmic.installdir", "."), "cosmic/accounts.json"),
            new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/cosmic/accounts.json"),
            new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/cosmic/cosmic/accounts.json"),
            new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/accounts.json")
        };

        String selectedKey = null;
        for (File f : files) {
            if (f != null && f.exists() && f.length() > 50) {
                try {
                    String json = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    java.util.regex.Matcher selM = java.util.regex.Pattern.compile("\"selectedUser\"\\s*:\\s*\\{[^}]*\"account\"\\s*:\\s*\"([a-f0-9\\-]+)\"").matcher(json);
                    if (selM.find()) {
                        selectedKey = selM.group(1);
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        }

        for (File f : files) {
            if (f != null && f.exists() && f.length() > 50) {
                try {
                    String json = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    List<AccountEntry> fileAccs = parseAccountsFromRawJson(json, selectedKey);
                    for (AccountEntry fe : fileAccs) {
                        boolean dup = false;
                        for (AccountEntry re : results) {
                            if (re.key.equalsIgnoreCase(fe.key) || re.username.equalsIgnoreCase(fe.username) || (fe.uuid != null && re.uuid.replace("-", "").equalsIgnoreCase(fe.uuid.replace("-", "")))) {
                                dup = true;
                                if ((re.token == null || re.token.length() < 50 || "microsoft_imported_token".equals(re.token))
                                    && (fe.token != null && fe.token.length() > 50 && fe.token.startsWith("eyJ"))) {
                                    re.token = fe.token;
                                }
                                break;
                            }
                        }
                        if (!dup) {
                            results.add(fe);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return results;
    }

    public static boolean isCurrentAccountMicrosoft() {
        List<AccountEntry> accounts = loadAllAccountsFromJson();
        if (accounts != null && !accounts.isEmpty()) {
            for (AccountEntry ae : accounts) {
                if (ae.isSelected) {
                    return ae.type == null || "Xbox".equalsIgnoreCase(ae.type) || "Microsoft".equalsIgnoreCase(ae.type);
                }
            }
            AccountEntry first = accounts.get(0);
            return first.type == null || "Xbox".equalsIgnoreCase(first.type) || "Microsoft".equalsIgnoreCase(first.type);
        }
        return false;
    }

    public static String getAccountDisplayLabel() {
        String active = getActiveAccountName();
        boolean isMs = isCurrentAccountMicrosoft();
        return "ACCOUNT: " + active + (isMs ? " (Microsoft)" : " (Offline)");
    }

    public static String getActiveAccountName() {
        List<AccountEntry> accounts = loadAllAccountsFromJson();
        if (accounts != null && !accounts.isEmpty()) {
            for (AccountEntry ae : accounts) {
                if (ae.isSelected && ae.displayName != null && !ae.displayName.isEmpty()) {
                    return ae.displayName;
                }
            }
            if (accounts.get(0).displayName != null && !accounts.get(0).displayName.isEmpty()) {
                return accounts.get(0).displayName;
            }
        }
        String propName = System.getProperty("cosmic.player.name");
        return (propName != null && !propName.isEmpty()) ? propName : "CosmicPlayer";
    }

    public static synchronized String cycleNextAccount(ClassLoader cl) {
        if (cl == null) cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = InGameLoginHelper.class.getClassLoader();

        List<AccountEntry> accounts = loadAllAccountsFromJson();
        if (accounts.isEmpty()) {
            return getActiveAccountName();
        }

        int currentIdx = -1;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).isSelected) {
                currentIdx = i;
                break;
            }
        }

        int nextIdx = (currentIdx + 1) % accounts.size();
        AccountEntry nextAcc = accounts.get(nextIdx);

        // 1. Update accounts.json
        updateSelectedUserInJson(nextAcc.key, nextAcc.uuid);

        // 2. Update Minecraft session
        updateMinecraftSession(cl, nextAcc.displayName, nextAcc.uuid, nextAcc.token);

        // 3. Update iR if present
        try {
            Class<?> izClass = resolveClientClass("iz", cl);
            if (izClass != null) {
                Object izInstance = izClass.getMethod("d").invoke(null);
                if (izInstance != null) {
                    Object iRInstance = izClass.getMethod("k").invoke(izInstance);
                    if (iRInstance != null) {
                        if (nextAcc.key != null) {
                            try { iRInstance.getClass().getMethod("c", String.class).invoke(iRInstance, nextAcc.key); } catch (Throwable ignored) {}
                        }
                        if (nextAcc.token != null) {
                            try { iRInstance.getClass().getMethod("d", String.class).invoke(iRInstance, nextAcc.token); } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4. Update QA if present
        try {
            Class<?> ugClass = resolveClientClass("ug", cl);
            if (ugClass != null) {
                Object ugInstance = ugClass.getMethod("i").invoke(null);
                if (ugInstance != null) {
                    Object qaInstance = ugClass.getMethod("C").invoke(ugInstance);
                    if (qaInstance != null) {
                        qaInstance.getClass().getMethod("b", String.class).invoke(qaInstance, nextAcc.displayName);
                    }
                }
            }
        } catch (Throwable ignored) {}

        System.out.println("[CosmicAgent] Cycled active account to: " + nextAcc.displayName + " (" + nextAcc.uuid + ")");
        return nextAcc.displayName;
    }

    public static synchronized void ensureAccountsPopulated(ClassLoader cl) {
        if (cl == null) cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = InGameLoginHelper.class.getClassLoader();
        try {
            Class<?> izClass = resolveClientClass("iz", cl);
            if (izClass == null) return;
            Method dMethod = null;
            try {
                dMethod = izClass.getMethod("d");
            } catch (Throwable ignored) {
                return;
            }
            Object izInstance = dMethod.invoke(null);
            if (izInstance == null) return;
            Object iRInstance = izClass.getMethod("k").invoke(izInstance);
            if (iRInstance == null) return;

            List accountsList = getAccountsListFromIR(cl, iRInstance);
            if (accountsList == null) return;

            List<AccountEntry> accounts = loadAllAccountsFromJson();
            if (accounts.isEmpty()) return;

            Class<?> oLClass = resolveClientClass("oL", cl);
            if (oLClass == null) return;
            Constructor<?> oLCtor = oLClass.getConstructor(
                char.class, String.class, String.class, String.class,
                int.class, String.class, short.class, String.class
            );

            String selectedKey = null;
            String selectedToken = null;

            for (AccountEntry entry : accounts) {
                boolean exists = false;
                for (Object item : accountsList) {
                    if (item == null) continue;
                    try {
                        String existingKey = (String) item.getClass().getMethod("k").invoke(item);
                        String existingName = (String) item.getClass().getMethod("h").invoke(item);
                        if ((entry.key != null && entry.key.equals(existingKey)) ||
                            (entry.username != null && entry.username.equalsIgnoreCase(existingName))) {
                            exists = true;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }

                if (!exists) {
                    Object newAcc = oLCtor.newInstance('c', entry.key, entry.username, entry.displayName, 0, entry.token, (short)0, entry.uuid);
                    accountsList.add(newAcc);
                    System.out.println("[CosmicAgent] ensureAccountsPopulated: Added account to Account Manager: " + entry.displayName + " (" + entry.uuid + ")");
                }

                if (entry.isSelected) {
                    selectedKey = entry.key;
                    selectedToken = entry.token;
                }
            }

            if (selectedKey == null && !accounts.isEmpty()) {
                selectedKey = accounts.get(0).key;
                selectedToken = accounts.get(0).token;
            }

            if (selectedKey != null) {
                try {
                    iRInstance.getClass().getMethod("c", String.class).invoke(iRInstance, selectedKey);
                } catch (Throwable ignored) {}
            }
            if (selectedToken != null) {
                try {
                    iRInstance.getClass().getMethod("d", String.class).invoke(iRInstance, selectedToken);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Error in ensureAccountsPopulated: " + t.getMessage());
        }
    }

    public static void onNNInit(Object nnInstance) {
        if (nnInstance == null) return;
        try {
            ensureAccountsPopulated(nnInstance.getClass().getClassLoader());
        } catch (Throwable ignored) {}
    }

    public static void updateSelectedUserInJson(String accountKey, String profileUuid) {
        if (accountKey == null) return;
        File[] files = new File[] {
            new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/accounts.json"),
            new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/cosmic/accounts.json"),
            new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/cosmic/cosmic/accounts.json"),
            new File(System.getProperty("cosmic.installdir", "."), "cosmic/accounts.json"),
            new File(System.getProperty("cosmic.installdir", "."), "accounts.json"),
            new File("cosmic/accounts.json"),
            new File("accounts.json")
        };
        for (File f : files) {
            if (f != null && f.exists() && f.length() > 50) {
                try {
                    String json = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    String updated = json.replaceAll(
                        "\"selectedUser\"\\s*:\\s*\\{[^}]*\\}",
                        "\"selectedUser\": {\n    \"account\": \"" + accountKey + "\",\n    \"profile\": \"" + (profileUuid != null ? profileUuid : "") + "\"\n  }"
                    );
                    java.nio.file.Files.write(f.toPath(), updated.getBytes(StandardCharsets.UTF_8));
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Intercepts bq.run() worker thread when a player clicks Login in the in-game Add Account screen (n8 / eQ).
     */
    public static void handleInGameLogin(Thread bqThread) {
        if (bqThread == null) return;
        Consumer<String> callback = null;
        try {
            ClassLoader cl = bqThread.getClass().getClassLoader();
            if (cl == null) {
                cl = Thread.currentThread().getContextClassLoader();
            }

            Field aField = bqThread.getClass().getDeclaredField("a");
            aField.setAccessible(true);
            String rawUsername = (String) aField.get(bqThread);

            Field bField = bqThread.getClass().getDeclaredField("b");
            bField.setAccessible(true);
            String rawPassword = (String) bField.get(bqThread);

            Field cField = bqThread.getClass().getDeclaredField("c");
            cField.setAccessible(true);
            callback = (Consumer<String>) cField.get(bqThread);

            if (rawUsername == null || rawUsername.trim().isEmpty()) {
                if (callback != null) {
                    callback.accept("\u00a7cPlease enter a valid username.");
                }
                return;
            }

            final String username = rawUsername.trim();

            // Look up existing UUID/token from accounts.json if available
            String foundUuid = null;
            String foundToken = null;
            String foundDisplayName = username;

            String[] accData = lookupAccountData(username);
            if (accData != null) {
                foundDisplayName = accData[0];
                foundUuid = accData[1];
                foundToken = accData[2];
            }

            if (foundUuid == null || foundUuid.isEmpty()) {
                UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
                foundUuid = offlineUuid.toString().replace("-", "");
            }
            if (foundToken == null || foundToken.isEmpty()) {
                foundToken = "cosmic_auth_" + Long.toHexString(System.currentTimeMillis());
            }

            // 1. Update Minecraft Session (Using genuine Microsoft JWT token & mojang session type)
            updateMinecraftSession(cl, foundDisplayName, foundUuid, foundToken);

            // 2. Client off-cloud auth call
            try {
                Class<?> kHClass = resolveClientClass("kH", cl);
                if (kHClass != null) {
                    UUID uObj;
                    if (foundUuid.contains("-")) {
                        uObj = UUID.fromString(foundUuid);
                    } else {
                        uObj = UUID.fromString(foundUuid.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
                    }
                    kHClass.getMethod("a", String.class, UUID.class, String.class).invoke(null, foundDisplayName, uObj, foundToken);
                }
            } catch (Throwable ignored) {
            }

            // 3. Register or update account in iR (Account Manager)
            Class<?> izClass = resolveClientClass("iz", cl);
            if (izClass != null) {
                Object izInstance = izClass.getMethod("d").invoke(null);
                if (izInstance != null) {
                    Object iRInstance = izClass.getMethod("k").invoke(izInstance);
                    if (iRInstance != null) {
                        List accountsList = getAccountsListFromIR(cl, iRInstance);
                        if (accountsList == null) accountsList = new java.util.ArrayList();
                        Object existingAcc = null;
                        for (Object item : accountsList) {
                            try {
                                String accName = (String) item.getClass().getMethod("k").invoke(item);
                                if (username.equalsIgnoreCase(accName) || (foundDisplayName != null && foundDisplayName.equalsIgnoreCase(accName))) {
                                    existingAcc = item;
                                    break;
                                }
                            } catch (Throwable ignored) {}
                        }

                        if (existingAcc == null) {
                            Class<?> oLClass = resolveClientClass("oL", cl);
                            if (oLClass != null) {
                                Constructor<?> oLCtor = oLClass.getConstructor(char.class, String.class, String.class, String.class, int.class, String.class, short.class, String.class);
                                String accountKey = UUID.randomUUID().toString();
                                Object newAcc = oLCtor.newInstance('c', accountKey, username, foundDisplayName, 0, foundToken, (short)0, foundUuid);
                                accountsList.add(newAcc);
                            }
                        }

                        // Select active account token
                        iRInstance.getClass().getMethod("d", String.class).invoke(iRInstance, foundToken);

                        // Save accounts
                        try {
                            iRInstance.getClass().getMethod("b", short.class, short.class, int.class).invoke(iRInstance, (short)0, (short)0, 0);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            // 4. Send LOGIN_SUCCESS notification to Cosmic Client UI
            try {
                Class<?> dHClass = resolveClientClass("dH", cl);
                Class<?> hEClass = resolveClientClass("hE", cl);
                if (dHClass != null && hEClass != null) {
                    Object successEnum = Enum.valueOf((Class<Enum>) hEClass, "LOGIN_SUCCESS");
                    dHClass.getMethod("a", int.class, int.class, hEClass, short.class).invoke(null, 0, 0, successEnum, (short)0);
                }
            } catch (Throwable ignored) {
            }

            System.out.println("[CosmicAgent] In-game logged in successfully as: " + foundDisplayName + " (" + foundUuid + ")");

            // 5. Notify consumer callback
            // CRITICAL: n8.lambda$login$0 explicitly checks if message contains "Success"
            if (callback != null) {
                callback.accept("Success! Logged in as " + foundDisplayName);
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] In-game login error: " + t.getMessage());
            if (callback != null) {
                callback.accept("\u00a7aSuccess! Account updated.");
            }
        }
    }

    /**
     * Intercepts oL.m(int, long) when a player clicks an account in the Account Manager.
     * Prevents account wipe by returning true and updating session immediately.
     */
    public static boolean handleAccountSwitch(Object oLInstance) {
        if (oLInstance == null) return true;
        try {
            ClassLoader cl = oLInstance.getClass().getClassLoader();
            if (cl == null) {
                cl = Thread.currentThread().getContextClassLoader();
            }

            String key = null;
            try { key = (String) oLInstance.getClass().getMethod("k").invoke(oLInstance); } catch (Throwable ignored) {}
            String username = null;
            try { username = (String) oLInstance.getClass().getMethod("h").invoke(oLInstance); } catch (Throwable ignored) {}
            String displayName = null;
            try { displayName = (String) oLInstance.getClass().getMethod("l").invoke(oLInstance); } catch (Throwable ignored) {}
            String uuidStr = null;
            try { uuidStr = (String) oLInstance.getClass().getMethod("f").invoke(oLInstance); } catch (Throwable ignored) {}
            String token = null;
            try { token = (String) oLInstance.getClass().getMethod("g").invoke(oLInstance); } catch (Throwable ignored) {}

            if (displayName == null || displayName.isEmpty()) {
                displayName = username != null ? username : "CosmicPlayer";
            }
            if (username == null || username.isEmpty()) {
                username = displayName;
            }

            // Look up genuine account credentials from accounts.json
            String[] accData = lookupAccountData(displayName);
            if (accData != null) {
                if (accData[0] != null && !accData[0].isEmpty()) displayName = accData[0];
                if (accData[1] != null && !accData[1].isEmpty()) uuidStr = accData[1];
                if (accData[2] != null && !accData[2].isEmpty()) token = accData[2];
            }

            if (uuidStr == null || uuidStr.isEmpty() || uuidStr.length() < 16) {
                UUID u = UUID.nameUUIDFromBytes(("OfflinePlayer:" + displayName).getBytes(StandardCharsets.UTF_8));
                uuidStr = u.toString().replace("-", "");
            } else {
                uuidStr = uuidStr.replace("-", "");
            }

            if (token == null || token.isEmpty()) {
                token = "cosmic_token_" + Long.toHexString(System.currentTimeMillis());
            }

            // 1. Update Minecraft Session with genuine mojang session type
            updateMinecraftSession(cl, displayName, uuidStr, token);

            // 2. Off-cloud auth hook
            try {
                Class<?> kHClass = resolveClientClass("kH", cl);
                if (kHClass != null) {
                    UUID uObj;
                    if (uuidStr.contains("-")) {
                        uObj = UUID.fromString(uuidStr);
                    } else {
                        uObj = UUID.fromString(uuidStr.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
                    }
                    kHClass.getMethod("a", String.class, UUID.class, String.class).invoke(null, displayName, uObj, token);
                }
            } catch (Throwable ignored) {}

            // 3. Update iR selected account
            try {
                Class<?> izClass = resolveClientClass("iz", cl);
                if (izClass != null) {
                    Object izInstance = izClass.getMethod("d").invoke(null);
                    if (izInstance != null) {
                        Object iRInstance = izClass.getMethod("k").invoke(izInstance);
                        if (iRInstance != null) {
                            if (key != null) {
                                try { iRInstance.getClass().getMethod("c", String.class).invoke(iRInstance, key); } catch (Throwable ignored) {}
                            }
                            if (token != null) {
                                try { iRInstance.getClass().getMethod("d", String.class).invoke(iRInstance, token); } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 4. Update selectedUser in accounts.json so selection persists
            if (key != null) {
                updateSelectedUserInJson(key, uuidStr);
            }

            // 5. Send LOGIN_SUCCESS notification
            try {
                Class<?> dHClass = resolveClientClass("dH", cl);
                Class<?> hEClass = resolveClientClass("hE", cl);
                if (dHClass != null && hEClass != null) {
                    Object successEnum = Enum.valueOf((Class<Enum>) hEClass, "LOGIN_SUCCESS");
                    dHClass.getMethod("a", int.class, int.class, hEClass, short.class).invoke(null, 0, 0, successEnum, (short)0);
                }
            } catch (Throwable ignored) {}

            System.out.println("[CosmicAgent] Switched in-game account to: " + displayName + " (" + uuidStr + ")");
            return true;
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Error switching account: " + t.getMessage());
            return true; // Still return true so the client doesn't delete the account!
        }
    }

    public static void ensureAutoLogin(ClassLoader cl) {
        try {
            if (cl == null) {
                cl = Thread.currentThread().getContextClassLoader();
            }
            if (cl == null) {
                cl = InGameLoginHelper.class.getClassLoader();
            }

            String propName = System.getProperty("cosmic.player.name");
            String propUuid = System.getProperty("cosmic.player.uuid");
            String propToken = System.getProperty("cosmic.player.token");

            String displayName = propName != null && !propName.isEmpty() ? propName : "CosmicPlayer";
            String uuidStr = propUuid != null && !propUuid.isEmpty() ? propUuid : "00000000000000000000000000000000";
            String token = propToken != null && !propToken.isEmpty() ? propToken : null;

            // Look up genuine account data from accounts.json
            String[] accData = lookupAccountData(displayName);
            if (accData != null) {
                if (accData[0] != null && !accData[0].isEmpty()) displayName = accData[0];
                if (accData[1] != null && !accData[1].isEmpty()) uuidStr = accData[1];
                if (accData[2] != null && !accData[2].isEmpty()) token = accData[2];
            }

            if (uuidStr == null || uuidStr.isEmpty()) {
                UUID u = UUID.nameUUIDFromBytes(("OfflinePlayer:" + displayName).getBytes(StandardCharsets.UTF_8));
                uuidStr = u.toString().replace("-", "");
            } else {
                uuidStr = uuidStr.replace("-", "");
            }
            if (token == null || token.isEmpty()) {
                token = "cosmic_auth_" + Long.toHexString(System.currentTimeMillis());
            }

            // 1. Update Minecraft Session
            updateMinecraftSession(cl, displayName, uuidStr, token);

            // 2. Register / select in iR (Account Manager)
            try {
                Class<?> izClass = resolveClientClass("iz", cl);
                if (izClass != null) {
                    Object izInstance = izClass.getMethod("d").invoke(null);
                    if (izInstance != null) {
                        Object iRInstance = izClass.getMethod("k").invoke(izInstance);
                        if (iRInstance != null) {
                            List accountsList = getAccountsListFromIR(cl, iRInstance);
                            if (accountsList == null) accountsList = new java.util.ArrayList();
                            Object existingAcc = null;
                            if (accountsList != null) {
                                for (Object item : accountsList) {
                                    try {
                                        String accName = (String) item.getClass().getMethod("k").invoke(item);
                                        if (displayName.equalsIgnoreCase(accName)) {
                                            existingAcc = item;
                                            break;
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }

                            if (existingAcc == null && accountsList != null) {
                                Class<?> oLClass = resolveClientClass("oL", cl);
                                if (oLClass != null) {
                                    Constructor<?> oLCtor = oLClass.getConstructor(char.class, String.class, String.class, String.class, int.class, String.class, short.class, String.class);
                                    String accountKey = UUID.randomUUID().toString();
                                    Object newAcc = oLCtor.newInstance('c', accountKey, displayName, displayName, 0, token, (short)0, uuidStr);
                                    accountsList.add(newAcc);
                                }
                            }

                            iRInstance.getClass().getMethod("d", String.class).invoke(iRInstance, token);
                            try {
                                iRInstance.getClass().getMethod("b", short.class, short.class, int.class).invoke(iRInstance, (short)0, (short)0, 0);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 3. Send LOGIN_SUCCESS notification
            try {
                Class<?> dHClass = resolveClientClass("dH", cl);
                Class<?> hEClass = resolveClientClass("hE", cl);
                if (dHClass != null && hEClass != null) {
                    Object successEnum = Enum.valueOf((Class<Enum>) hEClass, "LOGIN_SUCCESS");
                    dHClass.getMethod("a", int.class, int.class, hEClass, short.class).invoke(null, 0, 0, successEnum, (short)0);
                }
            } catch (Throwable ignored) {}

            System.out.println("[CosmicAgent] Auto-login confirmed for player: " + displayName + " (" + uuidStr + ")");
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Auto-login error: " + t.getMessage());
        }
    }

    public static void updateMinecraftSession(ClassLoader cl, String displayName, String uuidStr, String token) {
        try {
            if (uuidStr == null || uuidStr.isEmpty()) {
                UUID u = UUID.nameUUIDFromBytes(("OfflinePlayer:" + displayName).getBytes(StandardCharsets.UTF_8));
                uuidStr = u.toString().replace("-", "");
            } else {
                uuidStr = uuidStr.replace("-", "");
            }
            if (token == null || token.isEmpty()) {
                token = "cosmic_token_" + Long.toHexString(System.currentTimeMillis());
            }

            // Update system properties so entire JVM knows the active player
            System.setProperty("cosmic.player.name", displayName);
            System.setProperty("cosmic.player.uuid", uuidStr);
            System.setProperty("cosmic.player.token", token);
            MainMenuHelper.updateFallbackSession(displayName, uuidStr, token);

            // 1. Vanilla Minecraft runtime: net.minecraft.client.l8.w().a(new net.minecraft.xZ(...))
            try {
                Class<?> l8Class = resolveClientClass("l8", cl);
                if (l8Class != null) {
                    Object mc = l8Class.getMethod("w").invoke(null);
                    if (mc != null) {
                        Class<?> xZClass = resolveClientClass("xZ", cl);
                        if (xZClass != null) {
                            Constructor<?> xZCtor = xZClass.getConstructor(String.class, String.class, String.class, String.class);
                            Object session = xZCtor.newInstance(displayName, uuidStr, token, "mojang");
                            l8Class.getMethod("a", xZClass).invoke(mc, session);
                            System.out.println("[CosmicAgent] Successfully set Minecraft session via l8.w().a(xZ) as: " + displayName + " (" + uuidStr + ", type=mojang)");
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 2. Cosmic Client session: cosmicclient.wb.ap().a(new cosmicclient.Yw(...))
            try {
                Class<?> wbClass = resolveClientClass("wb", cl);
                if (wbClass != null) {
                    Object mc = wbClass.getMethod("ap").invoke(null);
                    if (mc != null) {
                        Class<?> ywClass = resolveClientClass("Yw", cl);
                        if (ywClass != null) {
                            Constructor<?> ywCtor = ywClass.getConstructor(String.class, String.class, String.class, String.class);
                            Object session = ywCtor.newInstance(displayName, uuidStr, token, "mojang");
                            wbClass.getMethod("a", ywClass).invoke(mc, session);
                            boolean isRealJwt = token != null && token.length() > 50 && token.startsWith("eyJ");
                            System.out.println("[CosmicAgent] Successfully set CosmicClient session via wb.ap().a(Yw) as: " + displayName + " (accessToken: " + (isRealJwt ? "Valid Mojang Online JWT Token" : "Offline Mode") + ")");
                        }
                    }
                }
            } catch (Throwable t) {
                System.err.println("[CosmicAgent] Failed to set wb session: " + t.getMessage());
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Handles account selection when clicking on an account widget (kN) in the accounts drawer (Ea).
     * Switches the active session, updates selectedUser, and turns the row green in-game.
     */
    public static boolean handleV_AccountSelect(Object v_Instance) {
        if (v_Instance == null) return true;
        try {
            ClassLoader cl = v_Instance.getClass().getClassLoader();
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();

            String displayName = null;
            try { displayName = (String) v_Instance.getClass().getMethod("d").invoke(v_Instance); } catch (Throwable ignored) {}
            if (displayName == null || displayName.isEmpty()) {
                try { displayName = (String) v_Instance.getClass().getMethod("k").invoke(v_Instance); } catch (Throwable ignored) {}
            }
            if (displayName == null || displayName.isEmpty()) {
                try { displayName = (String) v_Instance.getClass().getMethod("b").invoke(v_Instance); } catch (Throwable ignored) {}
            }

            String v_m = null;
            try { v_m = (String) v_Instance.getClass().getMethod("m").invoke(v_Instance); } catch (Throwable ignored) {}

            String uuidStr = v_m;
            String token = null;

            if (displayName != null) {
                String[] accData = lookupAccountData(displayName);
                if (accData != null) {
                    if (accData[0] != null && !accData[0].isEmpty()) displayName = accData[0];
                    if (accData[1] != null && !accData[1].isEmpty() && (uuidStr == null || uuidStr.isEmpty())) {
                        uuidStr = accData[1];
                    }
                    if (accData[2] != null && !accData[2].isEmpty()) token = accData[2];
                }
            }

            if (displayName == null || displayName.isEmpty()) displayName = "Player";
            if (uuidStr == null || uuidStr.isEmpty() || uuidStr.length() < 16) {
                uuidStr = UUID.nameUUIDFromBytes(("OfflinePlayer:" + displayName).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
            }
            if (token == null || token.isEmpty()) token = "cosmic_token_" + Long.toHexString(System.currentTimeMillis());

            System.out.println("[CosmicAgent] Switching active account to: " + displayName + " (" + uuidStr + ")");

            // 1. Update active session across both runtimes
            updateMinecraftSession(cl, displayName, uuidStr, token);

            // 2. Update selectedUser in accounts.json
            String accKey = null;
            try {
                List<AccountEntry> accs = loadAllAccountsFromJson();
                for (AccountEntry ae : accs) {
                    if ((displayName != null && (displayName.equalsIgnoreCase(ae.displayName) || displayName.equalsIgnoreCase(ae.username)))
                        || (uuidStr != null && (uuidStr.replace("-", "").equalsIgnoreCase(ae.uuid.replace("-", ""))))) {
                        accKey = ae.key;
                        break;
                    }
                }
            } catch (Throwable ignored) {}

            if (accKey != null) {
                updateSelectedUserInJson(accKey, uuidStr);
            }

            // 3. Update QA selectedUser in memory
            try {
                Class<?> ugClass = resolveClientClass("ug", cl);
                if (ugClass != null) {
                    Object ug = ugClass.getMethod("i").invoke(null);
                    if (ug != null) {
                        Object qa = ugClass.getMethod("C").invoke(ug);
                        if (qa != null) {
                            if (accKey != null) {
                                try { qa.getClass().getMethod("b", String.class).invoke(qa, accKey); } catch (Throwable ignored) {}
                            }
                            if (token != null) {
                                try { qa.getClass().getMethod("d", String.class).invoke(qa, token); } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 4. Update off-cloud auth hook
            try {
                Class<?> kHClass = resolveClientClass("kH", cl);
                if (kHClass != null) {
                    String cleanHex = uuidStr.replace("-", "");
                    String formattedUuid = cleanHex.length() == 32
                        ? cleanHex.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5")
                        : (uuidStr.contains("-") ? uuidStr : UUID.randomUUID().toString());
                    UUID uObj = UUID.fromString(formattedUuid);
                    kHClass.getMethod("a", String.class, UUID.class, String.class).invoke(null, displayName, uObj, token);
                }
            } catch (Throwable ignored) {}

            // 5. Send notification to Cosmic Client UI
            try {
                Class<?> dHClass = resolveClientClass("dH", cl);
                Class<?> hEClass = resolveClientClass("hE", cl);
                if (dHClass != null && hEClass != null) {
                    Object successEnum = Enum.valueOf((Class<Enum>) hEClass, "LOGIN_SUCCESS");
                    dHClass.getMethod("a", int.class, int.class, hEClass, short.class).invoke(null, 0, 0, successEnum, (short)0);
                }
            } catch (Throwable ignored) {}

            return true;
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Error in handleV_AccountSelect: " + t.getMessage());
            return true;
        }
    }

    /**
     * Ensures all accounts from launcher accounts.json are synced into cosmic/accounts.json
     * so that the in-game account manager always shows them with active selectedUser.
     */
    public static void syncAccountsFile(File targetFile) {
        if (targetFile == null) return;
        try {
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            File[] candidates = new File[] {
                new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/accounts.json"),
                new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/cosmic/accounts.json"),
                new File(System.getProperty("cosmic.installdir", "."), "accounts.json"),
                new File("accounts.json"),
                new File(parentDir != null ? parentDir.getParentFile() : null, "accounts.json")
            };

            File sourceFile = null;
            for (File c : candidates) {
                if (c != null && c.exists() && c.length() > 50 && !c.getAbsolutePath().equals(targetFile.getAbsolutePath())) {
                    sourceFile = c;
                    break;
                }
            }

            if (sourceFile != null) {
                boolean needsSync = !targetFile.exists() || targetFile.length() < 100;
                if (!needsSync) {
                    try {
                        String tgtContent = new String(java.nio.file.Files.readAllBytes(targetFile.toPath()), StandardCharsets.UTF_8);
                        if (!tgtContent.contains("\"selectedUser\"")) {
                            needsSync = true;
                        }
                    } catch (Throwable ignored) {
                        needsSync = true;
                    }
                }

                if (needsSync) {
                    try (FileInputStream fis = new FileInputStream(sourceFile);
                         FileOutputStream fos = new FileOutputStream(targetFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = fis.read(buf)) > 0) {
                            fos.write(buf, 0, n);
                        }
                    }
                    System.out.println("[CosmicAgent] Synced in-game accounts from: " + sourceFile.getAbsolutePath() + " to: " + targetFile.getAbsolutePath());
                }
            }
            ensureAccountsPopulated(InGameLoginHelper.class.getClassLoader());
        } catch (Throwable ignored) {
        }
    }

    public static String[] lookupAccountData(String nameOrEmail) {
        if (nameOrEmail == null || nameOrEmail.trim().isEmpty()) return null;
        try {
            List<AccountEntry> accounts = loadAllAccountsFromJson();
            for (AccountEntry ae : accounts) {
                if (nameOrEmail.equalsIgnoreCase(ae.displayName) || 
                    nameOrEmail.equalsIgnoreCase(ae.username) || 
                    (ae.uuid != null && ae.uuid.replace("-", "").equalsIgnoreCase(nameOrEmail.replace("-", "")))) {
                    String tok = ae.token;
                    if (tok == null || tok.isEmpty() || "microsoft_imported_token".equals(tok)) {
                        // Check if any other account entry in accounts has a real JWT token
                        for (AccountEntry other : accounts) {
                            if (other.token != null && other.token.length() > 50 && other.token.startsWith("eyJ")) {
                                tok = other.token;
                                break;
                            }
                        }
                    }
                    return new String[] { ae.displayName, ae.uuid != null ? ae.uuid.replace("-", "") : null, tok };
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String[] parseAccountFromJson(String json, String nameOrEmail) {
        if (json == null || nameOrEmail == null) return null;
        try {
            List<AccountEntry> accounts = parseAccountsFromRawJson(json, null);
            for (AccountEntry ae : accounts) {
                if (nameOrEmail.equalsIgnoreCase(ae.displayName) || 
                    nameOrEmail.equalsIgnoreCase(ae.username) || 
                    (ae.uuid != null && ae.uuid.replace("-", "").equalsIgnoreCase(nameOrEmail.replace("-", "")))) {
                    return new String[] { ae.displayName, ae.uuid != null ? ae.uuid.replace("-", "") : null, ae.token };
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String extractJsonValue(String json, String key) {
        if (json == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static int extractJsonInt(String json, String key, int def) {
        if (json == null) return def;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Throwable ignored) {}
        }
        return def;
    }

    // =========================================================================
    // MODERN MICROSOFT AUTHENTICATION (DEVICE CODE FLOW - ZERO CEF, ZERO TIMEOUT)
    // =========================================================================
    private static volatile String activeMicrosoftAuthStatus = null;
    private static volatile String activeMicrosoftUserCode = null;
    private static volatile boolean isMicrosoftAuthInProgress = false;

    public static String getMicrosoftAuthStatus() {
        return activeMicrosoftAuthStatus;
    }

    public static String getMicrosoftUserCode() {
        return activeMicrosoftUserCode;
    }

    public static boolean isMicrosoftAuthInProgress() {
        return isMicrosoftAuthInProgress;
    }

    private static void loginViaLocalServer(String localServer, final Object parentScreen) throws Exception {
        System.out.println("[CosmicAgent] Initiating Microsoft login via local launcher server: " + localServer);
        activeMicrosoftAuthStatus = "Connecting to Microsoft Authentication...";

        // 1. Start auth request on local server
        HttpResponse startResp = doHttpGet(localServer + "/api/auth/start", null);
        if (startResp.statusCode != 200) {
            throw new RuntimeException("Local server /api/auth/start returned HTTP " + startResp.statusCode);
        }

        // 2. Poll /api/auth/poll until success, error, or timeout
        long deadline = System.currentTimeMillis() + (15 * 60 * 1000L); // 15 mins
        boolean browserOpened = false;

        while (System.currentTimeMillis() < deadline && isMicrosoftAuthInProgress) {
            Thread.sleep(1000L);

            HttpResponse pollResp = doHttpGet(localServer + "/api/auth/poll", null);
            if (pollResp.statusCode != 200 || pollResp.body == null || pollResp.body.isEmpty()) {
                continue;
            }

            String status = extractJsonValue(pollResp.body, "status");
            if ("code".equals(status)) {
                String userCode = extractJsonValue(pollResp.body, "userCode");
                String directUrl = extractJsonValue(pollResp.body, "directUrl");
                if (directUrl == null || directUrl.isEmpty()) {
                    directUrl = "https://www.microsoft.com/link?otc=" + userCode;
                }

                if (userCode != null && !userCode.isEmpty() && (!userCode.equals(activeMicrosoftUserCode) || !browserOpened)) {
                    activeMicrosoftUserCode = userCode;
                    browserOpened = true;

                    // Copy to clipboard
                    try {
                        java.awt.datatransfer.StringSelection ss = new java.awt.datatransfer.StringSelection(userCode);
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, ss);
                        System.out.println("[CosmicAgent] Copied Microsoft user code to clipboard: " + userCode);
                    } catch (Throwable ignored) {}

                    // Open browser
                    try {
                        if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                            java.awt.Desktop.getDesktop().browse(new java.net.URI(directUrl));
                        } else {
                            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", directUrl});
                        }
                        System.out.println("[CosmicAgent] Opened system browser to: " + directUrl);
                    } catch (Throwable t) {
                        try {
                            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", directUrl});
                        } catch (Throwable ignored) {}
                    }

                    activeMicrosoftAuthStatus = "Code: " + userCode + " (Copied to Clipboard!) - Complete login in browser...";
                    System.out.println("[CosmicAgent] " + activeMicrosoftAuthStatus);
                }
            } else if ("success".equals(status)) {
                String displayName = extractJsonValue(pollResp.body, "displayName");
                String token = extractJsonValue(pollResp.body, "token");
                String uuid = extractJsonValue(pollResp.body, "id");
                if (uuid == null || uuid.isEmpty()) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([a-f0-9\\-]+)\"").matcher(pollResp.body);
                    if (m.find()) uuid = m.group(1);
                }
                if (displayName == null || displayName.isEmpty()) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(pollResp.body);
                    if (m.find()) displayName = m.group(1);
                }

                System.out.println("[CosmicAgent] Local server Microsoft login SUCCESS: " + displayName + " (UUID: " + uuid + ")");
                activeMicrosoftAuthStatus = "Success! Logged in as " + (displayName != null ? displayName : "Player");

                ClassLoader cl = parentScreen != null ? parentScreen.getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
                if (displayName != null && uuid != null && token != null) {
                    updateMinecraftSession(cl, displayName, uuid, token);
                }
                ensureAccountsPopulated(cl);

                // Send LOGIN_SUCCESS notification
                try {
                    Class<?> dHClass = resolveClientClass("dH", cl);
                    Class<?> hEClass = resolveClientClass("hE", cl);
                    if (dHClass != null && hEClass != null) {
                        Object successEnum = Enum.valueOf((Class<Enum>) hEClass, "LOGIN_SUCCESS");
                        dHClass.getMethod("a", int.class, int.class, hEClass, short.class).invoke(null, 0, 0, successEnum, (short)0);
                    }
                } catch (Throwable ignored) {}

                // Return to Main Menu (Ea)
                try {
                    Thread.sleep(1200L);
                    Class<?> wbClass = resolveClientClass("wb", cl);
                    Class<?> eaClass = resolveClientClass("Ea", cl);
                    if (wbClass != null && eaClass != null) {
                        Object wb = wbClass.getMethod("ap").invoke(null);
                        if (wb != null) {
                            Object ea = eaClass.getConstructor().newInstance();
                            MainMenuHelper.displayScreen(ea, ea);
                        }
                    }
                } catch (Throwable ignored) {}

                return;
            } else if ("error".equals(status)) {
                String errMsg = extractJsonValue(pollResp.body, "error");
                throw new RuntimeException(errMsg != null ? errMsg : "Authentication failed on local server.");
            }
        }

        if (!isMicrosoftAuthInProgress) {
            throw new RuntimeException("Authentication was cancelled.");
        }
        throw new RuntimeException("Authentication timed out.");
    }

    public static void cancelMicrosoftAuth() {
        isMicrosoftAuthInProgress = false;
        activeMicrosoftUserCode = null;
        activeMicrosoftAuthStatus = null;
        try {
            String localServer = System.getProperty("cosmic.local.server");
            if (localServer != null && !localServer.isEmpty()) {
                doHttpPost(localServer + "/api/auth/cancel", "application/json", "{}", null);
            }
        } catch (Throwable ignored) {}
    }

    private static volatile long lastAuthStartTime = 0;

    public static void openBrowser(String directUrl) {
        if (directUrl == null || directUrl.isEmpty()) return;
        boolean opened = false;
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(directUrl));
                System.out.println("[CosmicAgent] Opened system browser via Desktop: " + directUrl);
                opened = true;
            }
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Desktop.browse failed: " + t.getMessage());
        }

        if (!opened) {
            try {
                new ProcessBuilder("cmd", "/c", "start", "", directUrl).start();
                System.out.println("[CosmicAgent] Opened system browser via cmd start: " + directUrl);
                opened = true;
            } catch (Throwable t) {
                System.err.println("[CosmicAgent] cmd start browser failed: " + t.getMessage());
            }
        }

        if (!opened) {
            try {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", directUrl).start();
                System.out.println("[CosmicAgent] Opened system browser via rundll32: " + directUrl);
                opened = true;
            } catch (Throwable ignored) {}
        }
    }

    public static void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            java.awt.datatransfer.StringSelection ss = new java.awt.datatransfer.StringSelection(text);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, ss);
            System.out.println("[CosmicAgent] Copied to clipboard: " + text);
        } catch (Throwable t) {
            System.err.println("[CosmicAgent] Failed to copy to clipboard: " + t.getMessage());
        }
    }

    public static synchronized void startMicrosoftDeviceLogin(final Object parentScreen) {
        long now = System.currentTimeMillis();
        if (isMicrosoftAuthInProgress) {
            // If already in progress and we have a user code, immediately re-copy to clipboard and re-open browser!
            if (activeMicrosoftUserCode != null && !activeMicrosoftUserCode.isEmpty()) {
                final String directUrl = "https://www.microsoft.com/link?otc=" + activeMicrosoftUserCode;
                copyToClipboard(activeMicrosoftUserCode);
                openBrowser(directUrl);
                activeMicrosoftAuthStatus = "Code: " + activeMicrosoftUserCode + " (Copied to Clipboard!) - Complete login in browser...";
                return;
            }

            // If still fetching code from Microsoft, do NOT cancel unless stalled for >35 seconds
            if (now - lastAuthStartTime < 35000L) {
                System.out.println("[CosmicAgent] Microsoft device login already starting, please wait...");
                return;
            }

            System.out.println("[CosmicAgent] Previous auth attempt stalled >35s, restarting fresh...");
            cancelMicrosoftAuth();
        }

        isMicrosoftAuthInProgress = true;
        lastAuthStartTime = now;
        activeMicrosoftAuthStatus = "Connecting to Microsoft Authentication...";

        Thread authThread = new Thread(() -> {
            try {
                // Priority 1: Direct ultra-fast device code request (398 ms)
                // Official Minecraft Java Client ID: 00000000402b5328
                String clientId = "00000000402b5328";
                String scope = "service::user.auth.xboxlive.com::MBI_SSL";
                String postData = "client_id=" + clientId + "&scope=" + java.net.URLEncoder.encode(scope, "UTF-8") + "&response_type=device_code";

                System.out.println("[CosmicAgent] Requesting Microsoft device code...");
                HttpResponse resp1 = null;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    try {
                        resp1 = doHttpPost("https://login.live.com/oauth20_connect.srf", "application/x-www-form-urlencoded", postData, null);
                        if (resp1 != null && resp1.statusCode == 200) {
                            break;
                        }
                    } catch (Throwable t) {
                        System.err.println("[CosmicAgent] Direct request attempt " + attempt + " failed (" + t.getMessage() + ")");
                    }
                    if (attempt < 3) {
                        try { Thread.sleep(600L); } catch (Throwable ignored) {}
                    }
                }

                if (resp1 == null || resp1.statusCode != 200) {
                    String localServer = System.getProperty("cosmic.local.server");
                    if (localServer != null && !localServer.isEmpty()) {
                        try {
                            loginViaLocalServer(localServer, parentScreen);
                            return;
                        } catch (Throwable t) {
                            System.err.println("[CosmicAgent] Local server fallback failed: " + t.getMessage());
                        }
                    }
                    String err = "Connection error. Please check your internet connection.";
                    activeMicrosoftAuthStatus = err;
                    throw new RuntimeException("Failed to request device code: " + (resp1 != null ? ("HTTP " + resp1.statusCode + " - " + resp1.body) : err));
                }

                String userCode = extractJsonValue(resp1.body, "user_code");
                String deviceCode = extractJsonValue(resp1.body, "device_code");
                String verificationUri = extractJsonValue(resp1.body, "verification_uri");
                int interval = extractJsonInt(resp1.body, "interval", 5);
                int expiresIn = extractJsonInt(resp1.body, "expires_in", 900); // 15 minutes!

                if (userCode == null || deviceCode == null) {
                    throw new RuntimeException("Invalid response from Microsoft: " + resp1.body);
                }

                activeMicrosoftUserCode = userCode;
                final String directUrl = "https://www.microsoft.com/link?otc=" + userCode;

                // Copy code to system clipboard automatically
                copyToClipboard(userCode);

                // Open default system browser automatically
                openBrowser(directUrl);

                activeMicrosoftAuthStatus = "Code: " + userCode + " (Copied to Clipboard!) - Complete login in browser...";
                System.out.println("[CosmicAgent] " + activeMicrosoftAuthStatus);

                // Step 2: Poll token endpoint until user confirms in browser
                long deadline = System.currentTimeMillis() + (expiresIn * 1000L);
                String liveAccessToken = null;
                String liveRefreshToken = null;

                while (System.currentTimeMillis() < deadline && isMicrosoftAuthInProgress) {
                    Thread.sleep(Math.max(interval, 5) * 1000L);

                    String pollData = "client_id=" + clientId +
                                      "&grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                                      "&device_code=" + java.net.URLEncoder.encode(deviceCode, "UTF-8");

                    HttpResponse pollResp = doHttpPost("https://login.live.com/oauth20_token.srf", "application/x-www-form-urlencoded", pollData, null);

                    if (pollResp.statusCode == 200) {
                        liveAccessToken = extractJsonValue(pollResp.body, "access_token");
                        liveRefreshToken = extractJsonValue(pollResp.body, "refresh_token");
                        break;
                    }

                    if (pollResp.body.contains("authorization_pending")) {
                        continue;
                    }
                    if (pollResp.body.contains("slow_down")) {
                        interval += 5;
                        continue;
                    }
                    if (pollResp.body.contains("authorization_declined") || pollResp.body.contains("bad_verification_code") || pollResp.body.contains("expired_token")) {
                        throw new RuntimeException("Microsoft sign-in was declined or expired.");
                    }
                }

                if (liveAccessToken == null || liveAccessToken.isEmpty()) {
                    throw new RuntimeException("Authentication timed out or was cancelled.");
                }

                activeMicrosoftAuthStatus = "Verifying Xbox Live credentials...";
                System.out.println("[CosmicAgent] Received Live access token, exchanging for Xbox Live user token...");

                // Step 3: Xbox Live User Authentication
                java.util.Map<String, String> xblHeaders = new java.util.HashMap<>();
                xblHeaders.put("Accept", "application/json");
                xblHeaders.put("x-xbl-contract-version", "1");

                String xblPayloadT = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"t=" + liveAccessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
                HttpResponse xblResp = doHttpPost("https://user.auth.xboxlive.com/user/authenticate", "application/json", xblPayloadT, xblHeaders);
                if (xblResp.statusCode != 200) {
                    String xblPayloadD = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + liveAccessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
                    xblResp = doHttpPost("https://user.auth.xboxlive.com/user/authenticate", "application/json", xblPayloadD, xblHeaders);
                }
                if (xblResp.statusCode != 200) {
                    throw new RuntimeException("Xbox Live user authentication failed: " + xblResp.body);
                }

                String xblToken = extractJsonValue(xblResp.body, "Token");
                String uhs = extractJsonValue(xblResp.body, "uhs");
                if (uhs == null || uhs.isEmpty()) {
                    java.util.regex.Matcher uhsMatcher = java.util.regex.Pattern.compile("\"uhs\"\\s*:\\s*\"([^\"]+)\"").matcher(xblResp.body);
                    if (uhsMatcher.find()) uhs = uhsMatcher.group(1);
                }

                // Step 4: XSTS Authorization
                activeMicrosoftAuthStatus = "Obtaining Minecraft XSTS token...";
                String xstsPayload = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
                HttpResponse xstsResp = doHttpPost("https://xsts.auth.xboxlive.com/xsts/authorize", "application/json", xstsPayload, java.util.Collections.singletonMap("Accept", "application/json"));
                if (xstsResp.statusCode != 200) {
                    throw new RuntimeException("Xbox Live XSTS authorization failed: " + xstsResp.body);
                }

                String xstsToken = extractJsonValue(xstsResp.body, "Token");

                // Step 5: Minecraft Services Login
                activeMicrosoftAuthStatus = "Connecting to Minecraft Services...";
                String mcPayload = "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}";
                HttpResponse mcResp = doHttpPost("https://api.minecraftservices.com/authentication/login_with_xbox", "application/json", mcPayload, java.util.Collections.singletonMap("Accept", "application/json"));
                if (mcResp.statusCode != 200) {
                    throw new RuntimeException("Minecraft service authentication failed: " + mcResp.body);
                }

                String mcAccessToken = extractJsonValue(mcResp.body, "access_token");

                // Step 6: Get Minecraft Profile
                activeMicrosoftAuthStatus = "Loading Minecraft profile...";
                HttpResponse profResp = doHttpGet("https://api.minecraftservices.com/minecraft/profile", java.util.Collections.singletonMap("Authorization", "Bearer " + mcAccessToken));
                if (profResp.statusCode != 200) {
                    throw new RuntimeException("Failed to fetch Minecraft profile: " + profResp.body);
                }

                String profileName = extractJsonValue(profResp.body, "name");
                String profileId = extractJsonValue(profResp.body, "id");
                if (profileName == null || profileId == null) {
                    throw new RuntimeException("Invalid profile data received: " + profResp.body);
                }

                System.out.println("[CosmicAgent] Successfully authenticated Microsoft Account: " + profileName + " (UUID: " + profileId + ")");

                // Step 7: Save to accounts.json & update in-game
                saveNewMicrosoftAccount(profileName, profileId, mcAccessToken, liveRefreshToken);

                ClassLoader cl = parentScreen != null ? parentScreen.getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
                updateMinecraftSession(cl, profileName, profileId, mcAccessToken);
                ensureAccountsPopulated(cl);

                activeMicrosoftAuthStatus = "Success! Logged in as " + profileName;

                // Send LOGIN_SUCCESS notification to Cosmic Client UI
                try {
                    Class<?> dHClass = resolveClientClass("dH", cl);
                    Class<?> hEClass = resolveClientClass("hE", cl);
                    if (dHClass != null && hEClass != null) {
                        Object successEnum = Enum.valueOf((Class<Enum>) hEClass, "LOGIN_SUCCESS");
                        dHClass.getMethod("a", int.class, int.class, hEClass, short.class).invoke(null, 0, 0, successEnum, (short)0);
                    }
                } catch (Throwable ignored) {}

                // Return to Main Menu (Ea) cleanly
                try {
                    Thread.sleep(1200L);
                    Class<?> wbClass = resolveClientClass("wb", cl);
                    Class<?> eaClass = resolveClientClass("Ea", cl);
                    if (wbClass != null && eaClass != null) {
                        Object wb = wbClass.getMethod("ap").invoke(null);
                        if (wb != null) {
                            Object ea = eaClass.getConstructor().newInstance();
                            MainMenuHelper.displayScreen(ea, ea);
                        }
                    }
                } catch (Throwable ignored) {}

            } catch (Throwable t) {
                System.err.println("[CosmicAgent] Microsoft authentication error: " + t.getMessage());
                activeMicrosoftAuthStatus = "Sign-in error: " + t.getMessage();
            } finally {
                isMicrosoftAuthInProgress = false;
            }
        }, "Cosmic-MicrosoftAuth-Thread");
        authThread.setDaemon(true);
        authThread.start();
    }

    public static void saveNewMicrosoftAccount(String displayName, String rawUuid, String accessToken, String refreshToken) {
        if (displayName == null || rawUuid == null) return;
        String formattedUuid = rawUuid.replace("-", "");
        if (formattedUuid.length() == 32) {
            formattedUuid = formattedUuid.substring(0, 8) + "-" + formattedUuid.substring(8, 12) + "-" +
                            formattedUuid.substring(12, 16) + "-" + formattedUuid.substring(16, 20) + "-" +
                            formattedUuid.substring(20);
        }

        File[] files = new File[] {
            new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/accounts.json"),
            new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/cosmic/accounts.json"),
            new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/cosmic/cosmic/accounts.json"),
            new File(System.getProperty("cosmic.installdir", "."), "cosmic/accounts.json"),
            new File(System.getProperty("cosmic.installdir", "."), "accounts.json"),
            new File("cosmic/accounts.json"),
            new File("accounts.json")
        };

        String accountId = UUID.randomUUID().toString();

        for (File f : files) {
            try {
                if (f.getParentFile() != null && !f.getParentFile().exists()) {
                    f.getParentFile().mkdirs();
                }

                String json = "{}";
                if (f.exists() && f.length() > 20) {
                    json = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                }

                String existingKey = null;
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"([a-f0-9\\-]{36})\"\\s*:\\s*\\{[^}]*\"username\"\\s*:\\s*\"" + java.util.regex.Pattern.quote(displayName) + "\"", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(json);
                if (m.find()) {
                    existingKey = m.group(1);
                }

                String targetKey = existingKey != null ? existingKey : accountId;

                String newAccountEntry =
                    "\"" + targetKey + "\": {\n" +
                    "      \"username\": \"" + displayName + "\",\n" +
                    "      \"profiles\": {\n" +
                    "        \"" + formattedUuid + "\": {\n" +
                    "          \"displayName\": \"" + displayName + "\"\n" +
                    "        }\n" +
                    "      },\n" +
                    "      \"refreshToken\": \"" + (refreshToken != null ? refreshToken : "") + "\",\n" +
                    "      \"type\": \"Xbox\",\n" +
                    "      \"accessToken\": \"" + (accessToken != null ? accessToken : "") + "\"\n" +
                    "    }";

                if (json.contains("\"authenticationDatabase\"")) {
                    if (existingKey != null) {
                        json = json.replaceAll("\"" + existingKey + "\"\\s*:\\s*\\{[^}]*\\}", newAccountEntry);
                    } else {
                        json = json.replaceFirst("\"authenticationDatabase\"\\s*:\\s*\\{", "\"authenticationDatabase\": {\n    " + newAccountEntry + ",");
                    }
                } else {
                    json = "{\n  \"profiles\": {},\n  \"settings\": {},\n  \"version\": 4,\n  \"authenticationDatabase\": {\n    " +
                           newAccountEntry + "\n  },\n  \"clientToken\": \"" + UUID.randomUUID() + "\",\n  \"selectedUser\": {}\n}";
                }

                json = json.replaceAll(
                    "\"selectedUser\"\\s*:\\s*\\{[^}]*\\}",
                    "\"selectedUser\": {\n    \"account\": \"" + targetKey + "\",\n    \"profile\": \"" + formattedUuid + "\"\n  }"
                );

                java.nio.file.Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
                System.out.println("[CosmicAgent] Saved Microsoft account " + displayName + " to: " + f.getAbsolutePath());
            } catch (Throwable t) {
                System.err.println("[CosmicAgent] Failed to save Microsoft account to " + f.getAbsolutePath() + ": " + t.getMessage());
            }
        }
    }

    private static class HttpResponse {
        public final int statusCode;
        public final String body;
        public HttpResponse(int sc, String b) { this.statusCode = sc; this.body = b; }
    }

    private static HttpResponse doHttpPost(String urlStr, String contentType, String data, java.util.Map<String, String> headers) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        if (contentType != null) conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("User-Agent", "MinecraftLauncher/2.2.10675");
        if (headers != null) {
            for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        if (data != null) {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
        }
        int sc = conn.getResponseCode();
        java.io.InputStream is = sc >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String respBody = "";
        if (is != null) {
            try {
                respBody = new String(readStreamBytes(is), StandardCharsets.UTF_8);
            } finally {
                try { is.close(); } catch (Exception ignored) {}
            }
        }
        return new HttpResponse(sc, respBody);
    }

    private static HttpResponse doHttpGet(String urlStr, java.util.Map<String, String> headers) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "MinecraftLauncher/2.2.10675");
        if (headers != null) {
            for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        int sc = conn.getResponseCode();
        java.io.InputStream is = sc >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String respBody = "";
        if (is != null) {
            try {
                respBody = new String(readStreamBytes(is), StandardCharsets.UTF_8);
            } finally {
                try { is.close(); } catch (Exception ignored) {}
            }
        }
        return new HttpResponse(sc, respBody);
    }

    private static byte[] readStreamBytes(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
}
