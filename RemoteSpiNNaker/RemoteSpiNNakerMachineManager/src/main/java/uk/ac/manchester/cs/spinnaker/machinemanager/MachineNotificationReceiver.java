package uk.ac.manchester.cs.spinnaker.machinemanager;

import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

public interface MachineNotificationReceiver {

    /**
    * Indicates that a machine is no longer allocated
    * @param machine The machine that is no longer allocated
    */
    public void machineUnallocated(SpinnakerMachine machine);

}
