package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class NoNotifyMachineCommand extends Command<String> {
    public NoNotifyMachineCommand(String machine) {
        super("no_notify_machine");
        addArg(machine);
    }
}
