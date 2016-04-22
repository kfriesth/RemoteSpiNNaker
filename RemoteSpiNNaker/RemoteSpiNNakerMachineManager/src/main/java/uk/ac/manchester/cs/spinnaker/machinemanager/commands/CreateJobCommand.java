package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class CreateJobCommand extends MapKwArgsCommand<int[], String> {

    public CreateJobCommand(int n_boards, String owner) {
        super("create_job");
        setArgs(new int[]{n_boards});
        addKwArg("owner", owner);
    }
}
