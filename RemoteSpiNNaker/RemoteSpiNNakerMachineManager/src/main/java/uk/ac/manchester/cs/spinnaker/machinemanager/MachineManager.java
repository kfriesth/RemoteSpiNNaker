package uk.ac.manchester.cs.spinnaker.machinemanager;

import java.io.IOException;

import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

public interface MachineManager {

    public SpinnakerMachine getNextAvailableMachine(int nChips);

    public boolean isMachineAvailable(SpinnakerMachine machine);

    public void releaseMachine(SpinnakerMachine machine);

    public void close();

}
