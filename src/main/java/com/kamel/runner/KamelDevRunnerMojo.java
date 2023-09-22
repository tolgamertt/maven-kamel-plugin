package com.kamel.runner;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "dev")
public class KamelDevRunnerMojo extends KamelRunnerMojo {

    @Override
    public void execute() throws MojoExecutionException {
        super.execute();  // call the parent class's execute method
    }

    @Override
    protected void appendAdditionalArgs(StringBuilder command) {
        command.append(" --dev");
    }
}
