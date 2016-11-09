package uk.ac.manchester.cs.spinnaker.job;

public class JobMachineAllocated {
    private boolean allocated = false;

    public JobMachineAllocated() {
        // Does Nothing
    }

    public JobMachineAllocated(boolean allocated) {
        this.allocated = allocated;
    }

    public boolean isAllocated() {
        return this.allocated;
    }

    public void setAllocated(boolean allocated) {
        this.allocated = allocated;
    }
}
