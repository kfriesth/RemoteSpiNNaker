package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class DestroyJobCommand extends MapKwArgsCommand<int[], String> {

    public DestroyJobCommand(int jobId) {
        super("destroy_job");
        setArgs(new int[]{jobId});
    }
}
