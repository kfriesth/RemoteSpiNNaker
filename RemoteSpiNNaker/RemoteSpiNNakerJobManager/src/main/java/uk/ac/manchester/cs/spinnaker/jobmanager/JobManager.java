package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import uk.ac.manchester.cs.spinnaker.job.RemoteStackTrace;
import uk.ac.manchester.cs.spinnaker.job.RemoteStackTraceElement;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;
import uk.ac.manchester.cs.spinnaker.machinemanager.MachineManager;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueListener;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueManager;

/**
 * The manager of jobs; synchronizes and manages all the ongoing and future
 * processes and machines.
 */
@Path("job")
public class JobManager implements NMPIQueueListener {

	private MachineManager machineManager = null;

	private NMPIQueueManager queueManager = null;

	public JobManager(MachineManager machineManager,
			NMPIQueueManager queueManager) {
		this.machineManager = machineManager;
		this.queueManager = queueManager;

		queueManager.addListener(this);
	}

	@Override
	public void addJob(int id, List<String> inputData, String hardwareConfig) {
		SpinnakerMachine machine = machineManager.getNextAvailableMachine();

	}

	@POST
	@Path("{id}/log")
	@Consumes("text/plain")
	public void appendLog(@PathParam("id") int id, String logToAppend) {
		queueManager.appendJobLog(id, logToAppend);
	}

	@POST
	@Path("{id}/finished")
	public void setJobFinished(@PathParam("id") int id, String logToAppend,
			@QueryParam("outputFilename") List<String> outputs) {
		List<File> outputFiles = new ArrayList<File>();
		for (String filename : outputs) {
			outputFiles.add(new File(filename));
		}
		try {
			queueManager.setJobFinished(id, logToAppend, outputFiles);
		} catch (MalformedURLException e) {
			System.err.println("Error creating URLs while updating job");
			e.printStackTrace();
		}
	}

	@POST
	@Path("{id}/error")
	public void setJobError(@PathParam("id") int id,
			@QueryParam("error") String error, RemoteStackTrace stackTrace) {
		StackTraceElement[] elements =
				new StackTraceElement[stackTrace.getElements().size()];
		int i = 0;
		for (RemoteStackTraceElement element : stackTrace.getElements()) {
			elements[i] = new StackTraceElement(element.getClassName(),
					element.getMethodName(), element.getFileName(),
					element.getLineNumber());
		}

		Exception exception = new Exception(error);
		exception.setStackTrace(elements);
		queueManager.setJobError(id, null, exception);
	}
}
