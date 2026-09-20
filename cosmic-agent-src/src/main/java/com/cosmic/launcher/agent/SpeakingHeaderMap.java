package com.cosmic.launcher.agent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SpeakingHeaderMap
extends AbstractMap<String, List<String>> {
    private final Map<String, List<String>> delegate;
    private final Map<String, Boolean> speakingPlayers = new ConcurrentHashMap<String, Boolean>();
    private static final String SPEAKING_HEADER = "\u00a7a\u00a7l\u25cf \u00a7r\u00a7aSpeaking";

    public SpeakingHeaderMap(Map<String, List<String>> original) {
        this.delegate = new ConcurrentHashMap<String, List<String>>();
        if (original != null) {
            for (Map.Entry<String, List<String>> e : original.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                this.delegate.put(e.getKey(), e.getValue());
            }
        }
    }

    public void setSpeaking(String playerName, boolean speaking) {
        if (speaking) {
            this.speakingPlayers.put(playerName, true);
            this.injectSpeakingHeader(playerName);
        } else {
            this.speakingPlayers.remove(playerName);
            this.removeSpeakingHeader(playerName);
        }
    }

    private void injectSpeakingHeader(String playerName) {
        List<String> headers = this.delegate.get(playerName);
        if (headers == null) {
            headers = new ArrayList<String>();
            this.delegate.put(playerName, headers);
        }
        if (!this.containsSpeakingHeader(headers)) {
            headers.add(0, SPEAKING_HEADER);
        }
    }

    private void removeSpeakingHeader(String playerName) {
        List<String> headers = this.delegate.get(playerName);
        if (headers != null) {
            headers.removeIf(h -> h.contains("Speaking"));
            if (headers.isEmpty()) {
                this.delegate.remove(playerName);
            }
        }
    }

    private boolean containsSpeakingHeader(List<String> headers) {
        for (String h : headers) {
            if (!h.contains("Speaking")) continue;
            return true;
        }
        return false;
    }

    private void reInjectAll() {
        for (String name : this.speakingPlayers.keySet()) {
            this.injectSpeakingHeader(name);
        }
    }

    @Override
    public List<String> put(String key, List<String> value) {
        List<String> result = this.delegate.put(key, value);
        if (this.speakingPlayers.containsKey(key)) {
            this.injectSpeakingHeader(key);
        }
        return result;
    }

    @Override
    public void putAll(Map<? extends String, ? extends List<String>> m) {
        this.delegate.putAll(m);
        this.reInjectAll();
    }

    @Override
    public List<String> remove(Object key) {
        return this.delegate.remove(key);
    }

    @Override
    public void clear() {
        this.delegate.clear();
        this.reInjectAll();
    }

    @Override
    public List<String> get(Object key) {
        return this.delegate.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return this.delegate.containsKey(key);
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    @Override
    public Set<Map.Entry<String, List<String>>> entrySet() {
        return this.delegate.entrySet();
    }

    @Override
    public Set<String> keySet() {
        return this.delegate.keySet();
    }

    @Override
    public Collection<List<String>> values() {
        return this.delegate.values();
    }
}

