package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class GetJobMachineInfoCommand extends MapKwArgsCommand<int[], String> {

    public GetJobMachineInfoCommand(int jobId) {
        super("get_job_machine_info");
        setArgs(new int[]{jobId});
    }

}
