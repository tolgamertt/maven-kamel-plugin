package com.kamel.runner;

import java.util.List;
import java.util.Map;

class DefaultSettings {
    private List<String> configs;
    private Map<String, String> traits;

    // getters and setters
    public List<String> getConfigs() {
        return configs;
    }

    public void setConfigs(List<String> configs) {
        this.configs = configs;
    }

    public Map<String, String> getTraits() {
        return traits;
    }

    public void setTraits(Map<String, String> traits) {
        this.traits = traits;
    }
}