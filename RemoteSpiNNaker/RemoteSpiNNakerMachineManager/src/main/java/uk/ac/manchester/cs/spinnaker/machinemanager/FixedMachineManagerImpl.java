package uk.ac.manchester.cs.spinnaker.machinemanager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

/**
 * A manager of SpiNNaker machines
 *
 */
public class FixedMachineManagerImpl implements MachineManager {

    /**
     * The queue of available machines
     */
    private Set<SpinnakerMachine> machinesAvailable =
            new HashSet<SpinnakerMachine>();

    private Set<SpinnakerMachine> machinesAllocated =
            new HashSet<SpinnakerMachine>();

    private boolean done = false;

    /**
     * Creates a new MachineManager for a set of machines
     * @param machinesAvailable The machines that are to be managed
     */
    public FixedMachineManagerImpl(List<SpinnakerMachine> machinesAvailable) {
        for (SpinnakerMachine machine : machinesAvailable) {
            this.machinesAvailable.add(machine);
        }
    }

    @Override
    public List<SpinnakerMachine> getMachines() {
        List<SpinnakerMachine> machines = new ArrayList<SpinnakerMachine>();
        synchronized (machinesAvailable) {
            machines.addAll(machinesAvailable);
            machines.addAll(machinesAllocated);
        }
        return machines;
    }

    /**
     * Gets the next machine available, or waits if no machine is available
     *
     * @param n_chips The number of chips required, or -1 for any machine
     * @return The next machine available, or null if the manager is closed
     *         before a machine becomes available
     */
    @Override
    public SpinnakerMachine getNextAvailableMachine(int nChips) {

        // TODO: Actually check the machine has n_chips!

        SpinnakerMachine machine = null;
        synchronized (machinesAvailable) {

            while ((machine == null) && !done) {

                for (SpinnakerMachine nextMachine : machinesAvailable) {
                    if ((nextMachine.getWidth() * nextMachine.getHeight()) >
                            nChips) {
                        machine = nextMachine;
                    }
                }

                // If no machine was found, wait for something to change
                if (machine == null) {
                    try {
                        machinesAvailable.wait();
                    } catch (InterruptedException e) {

                        // Does Nothing
                    }
                }
            }

            // Move the machine from available to allocated
            machinesAvailable.remove(machine);
            machinesAllocated.add(machine);
        }
        return machine;
    }

    /**
     * Releases a machine that was previously in use
     * @param machine The machine to release
     */
    @Override
    public void releaseMachine(SpinnakerMachine machine) {
        synchronized (machinesAvailable) {
            machinesAllocated.remove(machine);
            machinesAvailable.add(machine);
            machinesAvailable.notifyAll();
        }
    }

    /**
     * Closes the manager
     */
    @Override
    public void close() {
        synchronized (machinesAvailable) {
            done = true;
            machinesAvailable.notifyAll();
        }
    }

    @Override
    public boolean isMachineAvailable(SpinnakerMachine machine) {
        synchronized (machinesAvailable) {
            return !machinesAvailable.contains(machine);
        }
    }

    @Override
    public boolean waitForMachineStateChange(SpinnakerMachine machine,
            int waitTime) {

        synchronized (machinesAvailable) {
            boolean isAvailable = machinesAvailable.contains(machine);
            try {
                machinesAvailable.wait(waitTime);
            } catch (InterruptedException e) {

                // Does Nothing
            }
            return machinesAvailable.contains(machine) != isAvailable;
        }
    }
}
