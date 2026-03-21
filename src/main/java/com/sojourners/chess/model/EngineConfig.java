package com.sojourners.chess.model;

import java.io.Serializable;
import java.util.LinkedHashMap;

public class EngineConfig implements Serializable {

    private static final long serialVersionUID = 1323134234;

    private String name;

    private String workDir;

    private String command;

    private String protocol;

    private LinkedHashMap<String, String> options;

    public EngineConfig(String name, String workDir, String command, String protocol, LinkedHashMap<String, String> options) {
        this.name = name;
        this.workDir = workDir;
        this.command = command;
        this.protocol = protocol;
        this.options = options;
    }

    public LinkedHashMap<String, String> getOptions() {
        return options;
    }

    public void setOptions(LinkedHashMap<String, String> options) {
        this.options = options;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}
