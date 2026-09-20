package com.cosmic.launcher.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArgFilter {
    public static List<String> filterAgentArgs(List<String> args) {
        if (args == null) {
            return Collections.emptyList();
        }
        ArrayList<String> filtered = new ArrayList<String>();
        for (String arg : args) {
            if (arg == null || arg.startsWith("-javaagent")) continue;
            filtered.add(arg);
        }
        return Collections.unmodifiableList(filtered);
    }
}

