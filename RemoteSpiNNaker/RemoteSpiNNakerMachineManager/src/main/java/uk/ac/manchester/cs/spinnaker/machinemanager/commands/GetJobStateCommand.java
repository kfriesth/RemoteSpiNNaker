package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class GetJobStateCommand extends Command<Integer> {
	public GetJobStateCommand(int jobId) {
		super("get_job_state");
		addArg(jobId);
	}
}
