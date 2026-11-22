package com.sentia.ai.app.model;

/** A custom slash command of a project, for example /review that expands to a saved prompt. */
public class ProjectCommand {
    public String name;
    public String prompt;

    public ProjectCommand() {
    }

    public ProjectCommand(String name, String prompt) {
        this.name = name;
        this.prompt = prompt;
    }
}
