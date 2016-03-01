package uk.ac.manchester.cs.spinnaker.machinemanager.responses;

import java.util.List;

public class JobsChangedResponse implements Response {

    private List<Integer> jobsChanged;

    public List<Integer> getJobsChanged() {
        return jobsChanged;
    }

    public void setJobsChanged(List<Integer> jobsChanged) {
        this.jobsChanged = jobsChanged;
    }
}
