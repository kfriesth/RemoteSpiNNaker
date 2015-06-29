package uk.ac.manchester.cs.spinnaker.job.impl;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.JobParametersTypeName;

/**
 * Represents the parameters required for a PyNN job
 */
@JobParametersTypeName("PyNNJobParameters")
public class PyNNJobParameters implements JobParameters {

    private String workingDirectory = null;

    private String script = null;

    private String hardwareConfiguration = null;

    private boolean deleteOnCompletion = false;

    public PyNNJobParameters() {

        // Does Nothing
    }

    public PyNNJobParameters(String workingDirectory, String script,
            String hardwareConfiguration, boolean deleteOnCompletion) {
        this.workingDirectory = workingDirectory;
        this.script = script;
        this.hardwareConfiguration = hardwareConfiguration;
        this.deleteOnCompletion = deleteOnCompletion;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getHardwareConfiguration() {
        return hardwareConfiguration;
    }

    public void setHardwareConfiguration(String hardwareConfiguration) {
        this.hardwareConfiguration = hardwareConfiguration;
    }

    public boolean isDeleteOnCompletion() {
        return deleteOnCompletion;
    }

    public void setDeleteOnCompletion(boolean deleteOnCompletion) {
        this.deleteOnCompletion = deleteOnCompletion;
    }
}
