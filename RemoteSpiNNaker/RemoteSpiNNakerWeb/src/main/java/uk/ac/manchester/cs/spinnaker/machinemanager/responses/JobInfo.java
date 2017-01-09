package uk.ac.manchester.cs.spinnaker.machinemanager.responses;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JobInfo {
	private int jobId;
	private String owner;
	private Object startTime;
	private Float keepalive;
	private JobState.State state;
	private Boolean power;
	private List<Object> args;
	private Map<String, Object> kwargs;
	private String allocatedMachineName;
	private List<Object> boards;

	@JsonProperty("job_id")
	public int getJobId() {
		return jobId;
	}

	public void setJobId(int jobId) {
		this.jobId = jobId;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	@JsonProperty("start_time")
	public Object getStartTime() {
		return startTime;
	}

	public void setStartTime(Object startTime) {
		this.startTime = startTime;
	}

	public Float getKeepalive() {
		return keepalive;
	}

	public void setKeepalive(Float keepalive) {
		this.keepalive = keepalive;
	}

	public JobState.State getState() {
		return state;
	}

	public void setState(int state) {
		this.state = JobState.State.get(state);
	}

	public Boolean getPower() {
		return power;
	}

	public void setPower(Boolean power) {
		this.power = power;
	}

	public List<Object> getArgs() {
		return args;
	}

	public void setArgs(List<Object> args) {
		this.args = args;
	}

	public Map<String, Object> getKwargs() {
		return kwargs;
	}

	public void setKwargs(Map<String, Object> kwargs) {
		this.kwargs = kwargs;
	}

	@JsonProperty("allocated_machine_name")
	public String getAllocatedMachineName() {
		return allocatedMachineName;
	}

	public void setAllocatedMachineName(String allocatedMachineName) {
		this.allocatedMachineName = allocatedMachineName;
	}

	public List<Object> getBoards() {
		return boards;
	}

	public void setBoards(List<Object> boards) {
		this.boards = boards;
	}
}
