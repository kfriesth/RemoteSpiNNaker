package uk.ac.manchester.cs.spinnaker.job.nmpi;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import uk.ac.manchester.cs.spinnaker.rest.DateTimeDeserialiser;
import uk.ac.manchester.cs.spinnaker.rest.DateTimeSerialiser;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * A NMPI job
 */
public class Job implements QueueNextResponse {

    private String code = null;

    private Map<String, Object> hardwareConfig = null;

    private String hardwarePlatform = null;

    private Integer id = null;

    private List<DataItem> inputData = null;

    private List<DataItem> outputData = null;

    private String collabId = null;

    private String resourceUri = null;

    private String status = null;

    private String command = null;

    private String userId = null;

    private long resourceUsage = 0;

    @JsonSerialize(using=DateTimeSerialiser.class)
    @JsonDeserialize(using=DateTimeDeserialiser.class)
    private DateTime timestampCompletion = null;

    @JsonSerialize(using=DateTimeSerialiser.class)
    @JsonDeserialize(using=DateTimeDeserialiser.class)
    private DateTime timestampSubmission = null;

    private String user = null;

    private String provenance = null;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public List<DataItem> getOutputData() {
        return outputData;
    }

    public void setOutputData(List<DataItem> outputData) {
        this.outputData = outputData;
    }

    public String getCollabId() {
        return collabId;
    }

    public void setCollabId(String collabId) {
        this.collabId = collabId;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getResourceUsage() {
        return resourceUsage;
    }

    public void setResourceUsage(long resourceUsage) {
        this.resourceUsage = resourceUsage;
    }

    public String getProvenance() {
        return provenance;
    }

    public void setProvenance(String provenance) {
        this.provenance = provenance;
    }
}
