package uk.ac.manchester.cs.spinnaker.jobprocess;

import java.io.File;
import java.util.List;

import uk.ac.manchester.cs.spinnaker.job.JobParameters;
import uk.ac.manchester.cs.spinnaker.job.Status;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

/**
 * An interface to an executable job process type
 *
 * @param <J> This type
 * @param <P> The type of the parameters
 */
public interface JobProcess<P extends JobParameters> {

    /**
    * Executes the job
    *
    * @param machineUrl The URL to request a machine using (or null if machine
    *                   is given)
    * @param machine The machine to execute the job on (
    *                or null if a URL is given)
    * @param parameters The parameters of the job
    * @param logWriter Somewhere to write logs to
    */
    public void execute(
        String machineUrl, SpinnakerMachine machine, P parameters,
        LogWriter logWriter);

    /**
    * Gets the status of the job
    *
    * @return The status
    */
    public Status getStatus();

    /**
    * Gets any errors returned by the job.  If the status is not Error, this
    * should return null
    *
    * @return An error, or null if no error
    */
    public Throwable getError();

    /**
    * Gets any outputs from the job.  Should always return a list, but this
    * list can be empty if there are no outputs.
    *
    * @return A list of output files.
    */
    public List<File> getOutputs();

    /**
    * Cleans up the job, removing any associated files
    */
    public void cleanup();
}
