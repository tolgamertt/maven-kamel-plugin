package com.kamel.runner;

import java.util.List;

public class DependencyExclusion {
    private List<String> excludedDependencies;

    public List<String> getExcludedDependencies() {
        return excludedDependencies;
    }

    public void setExcludedDependencies(List<String> excludedDependencies) {
        this.excludedDependencies = excludedDependencies;
    }
}
