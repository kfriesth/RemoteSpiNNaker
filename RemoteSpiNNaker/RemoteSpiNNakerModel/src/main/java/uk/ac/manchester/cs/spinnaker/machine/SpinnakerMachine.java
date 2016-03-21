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
    * The version of the machine
    */
    private String version = null;

    /**
    * The width of the machine
    */
    private int width = 0;

    /**
    * The height of the machine
    */
    private int height = 0;

    /**
     * The number of boards in the machine
     */
    private int nBoards = 0;

    /**
    * The BMP details of the machine
    */
    private String bmpDetails = null;

    /**
    * Creates an empty machine
    */
    public SpinnakerMachine() {

        // Does Nothing
    }

    /**
    * Creates a new Spinnaker Machine
    *
    * @param machineName The name of the machine
    */
    public SpinnakerMachine(String machineName, String version, int width,
            int height, int nBoards, String bmpDetails) {
        this.machineName = machineName;
        this.version = version;
        this.width = width;
        this.height = height;
        this.nBoards = nBoards;
        this.bmpDetails = bmpDetails;
    }

    /**
    * Gets the name of the machine
    *
    * @return The name of the machine
    */
    public String getMachineName() {
        return machineName;
    }

    /**
    * Sets the name of the machine
    * @param machineName The name of the machine
    */
    public void setMachineName(String machineName) {
        this.machineName = machineName;
    }

    /**
    * Gets the version of the machine
    * @return The version of the machine
    */
    public String getVersion() {
        return version;
    }

    /**
    * Sets the version of the machine
    * @param version The version of the machine
    */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
    * Gets the width of the machine
    * @return The width of the machine
    */
    public int getWidth() {
        return width;
    }

    /**
    * Sets the width of the machine
    * @param width The width of the machine
    */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
    * Gets the height of the machine
    * @return The height of the machine
    */
    public int getHeight() {
        return height;
    }

    /**
    * Sets the height of the machine
    * @param height The height of the machine
    */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Gets the number of boards in the machine
     * @return The number of boards in the machine
     */
    public int getnBoards() {
        return nBoards;
    }

    /**
     * Sets the number of boards in the machine
     * @param nBoards The number of boards in the machine
     */
    public void setnBoards(int nBoards) {
        this.nBoards = nBoards;
    }

    /**
     * Gets the BMP details of the machine
     * @return The BMP details of the machine
     */
    public String getBmpDetails() {
        return bmpDetails;
    }

    /**
     * Sets the BMP details of the machine
     * @param bmpDetails The BMP details of the machine
     */
    public void setBmpDetails(String bmpDetails) {
        this.bmpDetails = bmpDetails;
    }
}
