package uk.ac.manchester.cs.spinnaker.machinemanager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

/**
 * A manager of SpiNNaker machines
 */
public class FixedMachineManagerImpl implements MachineManager {
    /**
	 * The queue of available machines
	 */
	private Set<SpinnakerMachine> machinesAvailable = new HashSet<>();
	private Set<SpinnakerMachine> machinesAllocated = new HashSet<>();
    private boolean done = false;

    /**
     * Creates a new MachineManager for a set of machines
     * @param machinesAvailable The machines that are to be managed
     */
    public FixedMachineManagerImpl(List<SpinnakerMachine> machinesAvailable) {
    	this.machinesAvailable.addAll(machinesAvailable);
    }

    @Override
    public List<SpinnakerMachine> getMachines() {
        List<SpinnakerMachine> machines = new ArrayList<>();
        synchronized (machinesAvailable) {
            machines.addAll(machinesAvailable);
            machines.addAll(machinesAllocated);
        }
        return machines;
    }

    /**
     * Gets the next machine available, or waits if no machine is available
     *
     * @param nBoards The number of boards to request
     * @return The next machine available, or null if the manager is closed
     *         before a machine becomes available
     */
    @Override
    public SpinnakerMachine getNextAvailableMachine(int nBoards) {
        try {
			synchronized (machinesAvailable) {
				SpinnakerMachine machine = null;
				while ((machine == null) && !done) {
					machine = getLargeEnoughMachine(nBoards);
					// If no machine was found, wait for something to change
					if (machine == null)
						machinesAvailable.wait();
				}
				if (machine != null) {
					// Move the machine from available to allocated
					machinesAvailable.remove(machine);
					machinesAllocated.add(machine);
				}
				return machine;
			}
		} catch (InterruptedException e) {
			return null;
		}
    }

	private SpinnakerMachine getLargeEnoughMachine(int nBoards) {
		for (SpinnakerMachine nextMachine : machinesAvailable)
		    if (nextMachine.getnBoards() >= nBoards)
		        return nextMachine;
		return null;
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
