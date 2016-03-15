package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class ListMachinesCommand extends MapKwArgsCommand<String[], String> {

    public ListMachinesCommand() {
        super("list_machines");
        setArgs(new String[]{});
    }

}
