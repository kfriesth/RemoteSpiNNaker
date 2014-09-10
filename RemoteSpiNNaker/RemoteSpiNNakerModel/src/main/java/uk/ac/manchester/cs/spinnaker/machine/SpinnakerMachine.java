package uk.ac.manchester.cs.spinnaker.machine;

/**
 * Represents a SpiNNaker machine on which jobs can be executed
 */
public class SpinnakerMachine {

	/**
	 * The name of the machine
	 */
	private String machineName = null;

	/**
	 * Creates a new Spinnaker Machine
	 *
	 * @param machineName The name of the machine
	 */
	public SpinnakerMachine(String machineName) {
		this.machineName = machineName;
	}

	/**
	 * Gets the name of the machine
	 *
	 * @return The name of the machine
	 */
	public String getMachineName() {
		return machineName;
	}
}
