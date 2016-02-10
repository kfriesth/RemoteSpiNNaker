package uk.ac.manchester.cs.spinnaker.job.nmpi;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * A NMPI job
 */
public class Job implements QueueNextResponse {

    private String experimentDescription = null;

    private Map<String, Object> hardwareConfig = null;

    private String hardwarePlatform = null;

    private Integer id = null;

    private List<DataItem> inputData = null;

    private String log = null;

    private List<DataItem> outputData = null;

    private String project = null;

    private String resourceUri = null;

    private String status = null;

    private String command = null;

    @JsonSerialize(using=NMPIDateSerialiser.class)
    @JsonDeserialize(using=NMPIDateDeserialiser.class)
    private DateTime timestampCompletion = null;

    @JsonSerialize(using=NMPIDateSerialiser.class)
    @JsonDeserialize(using=NMPIDateDeserialiser.class)
    private DateTime timestampSubmission = null;

    private String user = null;

    public String getExperimentDescription() {
        return experimentDescription;
    }

    public void setExperimentDescription(String experimentDescription) {
        this.experimentDescription = experimentDescription;
    }

    public Map<String, Object> getHardwareConfig() {
        return hardwareConfig;
    }

    public void setHardwareConfig(Map<String, Object> hardwareConfig) {
        this.hardwareConfig = hardwareConfig;
    }

    public String getHardwarePlatform() {
        return hardwarePlatform;
    }

    public void setHardwarePlatform(String hardwarePlatform) {
        this.hardwarePlatform = hardwarePlatform;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public List<DataItem> getInputData() {
        return inputData;
    }

    public void setInputData(List<DataItem> inputData) {
        this.inputData = inputData;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public List<DataItem> getOutputData() {
        return outputData;
    }

    public void setOutputData(List<DataItem> outputData) {
        this.outputData = outputData;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public DateTime getTimestampCompletion() {
        return timestampCompletion;
    }

    public void setTimestampCompletion(DateTime timestampCompletion) {
        this.timestampCompletion = timestampCompletion;
    }

    public DateTime getTimestampSubmission() {
        return timestampSubmission;
    }

    public void setTimestampSubmission(DateTime timestampSubmission) {
        this.timestampSubmission = timestampSubmission;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
