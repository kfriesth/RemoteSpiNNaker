package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class JobKeepAliveCommand extends MapKwArgsCommand<int[], String> {

    public JobKeepAliveCommand(int jobId) {
        super("job_keepalive");
        setArgs(new int[]{jobId});
    }
}
