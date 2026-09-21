package com.cosmic.launcher.agent;

public class ServerRedirectHelper {
    public static String maybeRedirect(String originalHost) {
        return originalHost;
    }

    public static String replaceInString(String input) {
        return input;
    }

    public static String rebrand(String input) {
        if (input == null) {
            return input;
        }
        if (input.contains("Cosmic Prisons")) {
            return input.replace("Cosmic Prisons", "Cosmic Client");
        }
        if (input.contains("CosmicPrisons")) {
            return input.replace("CosmicPrisons", "CosmicClient");
        }
        return input;
    }

    public static String rebrandURI(String uri) {
        return uri;
    }
}
