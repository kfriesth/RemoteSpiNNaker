package uk.ac.manchester.cs.spinnaker.job;

import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

public class JobSpecification {
	private SpinnakerMachine machine;
	private JobParameters parameters;
	private int id;
	private String url;

	public JobSpecification() {
		// Does Nothing
	}

	public JobSpecification(SpinnakerMachine machine, JobParameters parameters,
			int id, String url) {
		this.machine = machine;
		this.parameters = parameters;
		this.id = id;
		this.url = url;
	}

	public SpinnakerMachine getMachine() {
		return machine;
	}

	public void setMachine(SpinnakerMachine machine) {
		this.machine = machine;
	}

	public JobParameters getParameters() {
		return parameters;
	}

	public void setParameters(JobParameters parameters) {
		this.parameters = parameters;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}
