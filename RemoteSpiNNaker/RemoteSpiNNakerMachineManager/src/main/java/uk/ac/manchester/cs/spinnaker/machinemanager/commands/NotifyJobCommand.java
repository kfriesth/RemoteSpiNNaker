package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class NotifyJobCommand extends MapKwArgsCommand<int[], String> {

    public NotifyJobCommand(int jobId) {
        super("notify_job");
        setArgs(new int[]{jobId});
    }
}
