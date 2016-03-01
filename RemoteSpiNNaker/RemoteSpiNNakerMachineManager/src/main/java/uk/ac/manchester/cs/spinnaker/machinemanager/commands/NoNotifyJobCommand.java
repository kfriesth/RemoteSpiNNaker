package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class NoNotifyJobCommand extends MapKwArgsCommand<int[], String> {

    public NoNotifyJobCommand(int jobId) {
        super("no_notify_job");
        setArgs(new int[]{jobId});
    }
}
