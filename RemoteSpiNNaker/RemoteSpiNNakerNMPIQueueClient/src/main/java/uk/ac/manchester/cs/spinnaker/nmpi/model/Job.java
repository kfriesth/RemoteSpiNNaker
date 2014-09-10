package uk.ac.manchester.cs.spinnaker.nmpi.model;

import java.util.Date;
import java.util.List;

/**
 * A NMPI job
 */
public class Job implements QueueNextResponse {

	private String experimentDescription = null;

	private String hardwareConfig = null;

	private String hardwarePlatform = null;

	private Integer id = null;

	private List<String> inputData = null;

	private String log = null;

	private List<String> outputData = null;

	private String project = null;

	private String resourceUri = null;

	private String status = null;

	private Date timestampCompletion = null;

	private Date timestampSubmission = null;

	private String user = null;

	public String getExperimentDescription() {
		return experimentDescription;
	}

	public void setExperimentDescription(String experimentDescription) {
		this.experimentDescription = experimentDescription;
	}

	public String getHardwareConfig() {
		return hardwareConfig;
	}

	public void setHardwareConfig(String hardwareConfig) {
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

	public List<String> getInputData() {
		return inputData;
	}

	public void setInputData(List<String> inputData) {
		this.inputData = inputData;
	}

	public String getLog() {
		return log;
	}

	public void setLog(String log) {
		this.log = log;
	}

	public List<String> getOutputData() {
		return outputData;
	}

	public void setOutputData(List<String> outputData) {
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

	public Date getTimestampCompletion() {
		return timestampCompletion;
	}

	public void setTimestampCompletion(Date timestampCompletion) {
		this.timestampCompletion = timestampCompletion;
	}

	public Date getTimestampSubmission() {
		return timestampSubmission;
	}

	public void setTimestampSubmission(Date timestampSubmission) {
		this.timestampSubmission = timestampSubmission;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}
}
