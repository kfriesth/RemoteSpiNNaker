package uk.ac.manchester.cs.spinnaker.machinemanager.responses;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.ARRAY;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = ARRAY)
public class ListJobsResponse {

	private List<JobInfo> jobs;

	public List<JobInfo> getJobs() {
		return jobs;
	}

	public void setMachines(List<JobInfo> machines) {
		this.jobs = machines;
	}
}
