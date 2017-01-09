package uk.ac.manchester.cs.spinnaker.machinemanager.responses;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JobMachineInfo {
    private int width;
    private int height;
    private List<Connection> connections;
    private String machineName;

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public List<Connection> getConnections() {
        return connections;
    }

    public void setConnections(List<Connection> connections) {
        this.connections = connections;
    }

	@JsonProperty("machine_name")
    public String getMachineName() {
        return machineName;
    }

    public void setMachineName(String machineName) {
        this.machineName = machineName;
    }
}
