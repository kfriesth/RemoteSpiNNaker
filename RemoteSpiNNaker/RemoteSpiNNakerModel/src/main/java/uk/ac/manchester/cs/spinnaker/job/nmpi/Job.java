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
    private String code;
    private Map<String, Object> hardwareConfig;
    private String hardwarePlatform;
    private Integer id;
    private List<DataItem> inputData;
    private List<DataItem> outputData;
    private String collabId;
    private String resourceUri;
    private String status;
    private String command;
    private String userId;
    private long resourceUsage;
	@JsonSerialize(using = DateTimeSerialiser.class)
	@JsonDeserialize(using = DateTimeDeserialiser.class)
	private DateTime timestampCompletion;
	@JsonSerialize(using = DateTimeSerialiser.class)
	@JsonDeserialize(using = DateTimeDeserialiser.class)
	private DateTime timestampSubmission;
	private Object provenance;

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

    public Object getProvenance() {
        return provenance;
    }

    public void setProvenance(Object provenance) {
        this.provenance = provenance;
    }
}
