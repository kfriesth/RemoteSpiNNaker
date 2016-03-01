package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class GetJobStateCommand extends MapKwArgsCommand<int[], String> {

    public GetJobStateCommand(int jobId) {
        super("get_job_state");
        setArgs(new int[]{jobId});
    }
}
