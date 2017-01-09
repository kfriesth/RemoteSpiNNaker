package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class NotifyMachineCommand extends Command<String> {
	public NotifyMachineCommand(String machine) {
		super("notify_machine");
		addArg(machine);
	}
}
