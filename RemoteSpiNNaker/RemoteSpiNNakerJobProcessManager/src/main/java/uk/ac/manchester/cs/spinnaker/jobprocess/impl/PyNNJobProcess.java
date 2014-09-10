package uk.ac.manchester.cs.spinnaker.jobprocess.impl;

import java.io.File;
import java.util.List;

import uk.ac.manchester.cs.spinnaker.job.Status;
import uk.ac.manchester.cs.spinnaker.job.impl.PyNNJobParameters;
import uk.ac.manchester.cs.spinnaker.jobprocess.JobProcess;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

/**
 * A process for running PyNN jobs
 */
public class PyNNJobProcess implements JobProcess<PyNNJobParameters> {

	@Override
	public void execute(SpinnakerMachine machine,
			PyNNJobParameters parameters) {
		// TODO Auto-generated method stub

	}

	@Override
	public Status getStatus() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Throwable getError() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<File> getOutputs() {
		// TODO Auto-generated method stub
		return null;
	}
}
