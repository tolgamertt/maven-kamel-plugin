package com.kamel.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.util.*;

@Mojo(name = "run")
public class KamelRunnerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "configs")
    private List<String> configs;

    @Parameter(property = "traits")
    private Map<String, String> traits;

    private String PARENT_DIR;
    private String MODULE_DIR;
    private String[][] SEARCH_DIRS;

    public void execute() throws MojoExecutionException {
        MODULE_DIR = project.getBasedir().getAbsolutePath();
        File moduleDirFile = new File(MODULE_DIR);
        PARENT_DIR = moduleDirFile.getParentFile().getAbsolutePath();

        SEARCH_DIRS = new String[][]{{MODULE_DIR + "/src/main/resources", MODULE_DIR + "/src/main/java"}, {PARENT_DIR + "/generics/src/main/resources", PARENT_DIR + "/generics/src/main/java"}};

        List<String> allFiles = new ArrayList<>();
        List<String> connectorFiles = new ArrayList<>();

        for (String[] dirs : SEARCH_DIRS) {
            for (String dir : dirs) {
                allFiles.addAll(scanFiles(new File(dir)));
            }
        }

        StringBuilder command = new StringBuilder("kamel run \\");

        for (String file : allFiles) {
            if (file.contains(MODULE_DIR)) {
                String relativePath = "./" + file.substring(MODULE_DIR.length() + 1);

                if (file.endsWith(".yaml") && file.contains("/api/doc/") && !file.contains("/generics/")) {
                    command.append("\n--open-api file:").append(relativePath).append(" \\");
                } else if (file.endsWith(".json")) {
                    command.append("\n--resource=file:").append(relativePath).append(" \\");
                } else if (file.endsWith("Connector.java") && !file.contains("/generics/")) {
                    connectorFiles.add(relativePath);
                }
            } else if (file.contains(PARENT_DIR)) {
                String relativePath = "../" + file.substring(PARENT_DIR.length() + 1);

                if (file.endsWith(".json") && file.contains("/generics/")) {
                    command.append("\n--resource=file:").append(relativePath).append(" \\");
                } else if (file.endsWith(".java") && file.contains("/generics/")) {
                    connectorFiles.add(relativePath);
                }
            }
        }


        DefaultSettings defaultSettings = loadDefaultSettingsFromJson();

        if (configs != null) {
            for (String config : configs) {
                if (!config.startsWith("--")) {
                    config = "--" + config;
                }
                defaultSettings.getConfigs().add(config);
            }
        }

        if (traits != null) {
            defaultSettings.getTraits().putAll(traits);
        }

        for (String config : defaultSettings.getConfigs()) {
            command.append("\n").append(config).append(" \\");
        }

        for (Map.Entry<String, String> traitEntry : defaultSettings.getTraits().entrySet()) {
            command.append("\n--trait ").append(traitEntry.getKey()).append("=").append(traitEntry.getValue()).append(" \\");
        }

        Properties properties = loadProperties(MODULE_DIR + "/src/main/resources/application.properties");
        for (String propName : properties.stringPropertyNames()) {
            String propValue = properties.getProperty(propName);
            command.append("\n--property ").append(propName).append("=").append(propValue).append(" \\");
        }
        Set<String> backbaseDeps = getNonApacheCamelDependencies();
        for (String dep : backbaseDeps) {
            command.append("\n--dependency ").append(dep).append(" \\");
        }

        for (String connector : connectorFiles) {
            command.append("\n").append(connector);
        }

        appendAdditionalArgs(command);

        String[] cmdArray = {"/bin/sh", "-c", command.toString()};
        Process process;
        try {
            process = Runtime.getRuntime().exec(cmdArray);
            System.out.println(command);
            printStream(process.getInputStream());
            printStream(process.getErrorStream());
            process.waitFor();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<String> getNonApacheCamelDependencies() throws MojoExecutionException {
        Set<String> dependencies = new HashSet<>();
        Set<String> excludedDependencies = loadExcludedDependenciesFromJson();

        project.getDependencies().stream().filter(dependency -> {
            if (dependency.getGroupId().toLowerCase().contains("quarkus") || dependency.getGroupId().toLowerCase().contains("apache")) {
                return false;
            }
            String dependencyName = dependency.getArtifactId().toLowerCase();
            return !excludedDependencies.contains(dependencyName);
        }).forEach(dependency -> dependencies.add("mvn:" + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion()));

        return dependencies;
    }

    private DefaultSettings loadDefaultSettingsFromJson() throws MojoExecutionException {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            InputStream inputStream = getClass().getResourceAsStream("/default-settings.json");
            return objectMapper.readValue(inputStream, DefaultSettings.class);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to load default settings from JSON", e);
        }
    }

    private Properties loadProperties(String path) throws MojoExecutionException {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(path)) {
            properties.load(input);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to load properties", e);
        }
        return properties;
    }

    private Set<String> loadExcludedDependenciesFromJson() throws MojoExecutionException {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            InputStream inputStream = getClass().getResourceAsStream("/excluded-dependencies.json");
            DependencyExclusion dependencyExclusion = objectMapper.readValue(inputStream, DependencyExclusion.class);
            return new HashSet<>(dependencyExclusion.getExcludedDependencies());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to load excluded dependencies from JSON", e);
        }
    }

    private void printStream(InputStream inputStream) throws MojoExecutionException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                getLog().info(line);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to print command output", e);
        }
    }

    private List<String> scanFiles(File directory) {
        List<String> files = new ArrayList<>();
        File[] fList = directory.listFiles();
        if (fList != null) {
            for (File file : fList) {
                if (file.isFile()) {
                    files.add(file.getAbsolutePath());
                } else if (file.isDirectory()) {
                    files.addAll(scanFiles(file));
                }
            }
        }
        return files;
    }
    protected void appendAdditionalArgs(StringBuilder command) {
        // to add other goals
    }
}
