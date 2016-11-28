package uk.ac.manchester.cs.spinnaker.machinemanager;

import java.util.List;

import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

public interface MachineManager extends AutoCloseable {
	List<SpinnakerMachine> getMachines();

	SpinnakerMachine getNextAvailableMachine(int nBoards);

	boolean isMachineAvailable(SpinnakerMachine machine);

	boolean waitForMachineStateChange(SpinnakerMachine machine, int waitTime);

	void releaseMachine(SpinnakerMachine machine);
}
