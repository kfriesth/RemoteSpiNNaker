package uk.ac.manchester.cs.spinnaker.machinemanager;

import java.util.List;

import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

public interface MachineManager {

    public List<SpinnakerMachine> getMachines();

    public SpinnakerMachine getNextAvailableMachine(int nChips);

    public boolean isMachineAvailable(SpinnakerMachine machine);

    public boolean waitForMachineStateChange(
        SpinnakerMachine machine, int waitTime);

    public void releaseMachine(SpinnakerMachine machine);

    public void close();

}
