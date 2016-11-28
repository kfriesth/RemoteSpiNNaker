package uk.ac.manchester.cs.spinnaker.machinemanager;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES;
import static java.util.Arrays.asList;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.ac.manchester.cs.spinnaker.machinemanager.responses.JobState.DESTROYED;
import static uk.ac.manchester.cs.spinnaker.machinemanager.responses.JobState.READY;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;

import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;
import uk.ac.manchester.cs.spinnaker.machinemanager.commands.Command;
import uk.ac.manchester.cs.spinnaker.machinemanager.commands.CreateJobCommand;
import uk.ac.manchester.cs.spinnaker.machinemanager.commands.DestroyJobCommand;
import uk.ac.manchester.cs.spinnaker.machinemanager.commands.GetJobMachineInfoCommand;
import uk.ac.manchester.cs.spinnaker.machinemanager.commands.GetJobStateCommand;
import uk.ac.manchester.cs.spinnaker.machinemanager.commands.JobKeepAliveCommand;
import uk.ac.manchester.cs.spinnaker.machinemanager.commands.ListMachinesCommand;
import uk.ac.manchester.cs.spinnaker.machinemanager.commands.NoNotifyJobCommand;
import uk.ac.manchester.cs.spinnaker.machinemanager.commands.NotifyJobCommand;
import uk.ac.manchester.cs.spinnaker.machinemanager.responses.JobMachineInfo;
import uk.ac.manchester.cs.spinnaker.machinemanager.responses.JobState;
import uk.ac.manchester.cs.spinnaker.machinemanager.responses.JobsChangedResponse;
import uk.ac.manchester.cs.spinnaker.machinemanager.responses.Machine;
import uk.ac.manchester.cs.spinnaker.machinemanager.responses.Response;
import uk.ac.manchester.cs.spinnaker.machinemanager.responses.ReturnResponse;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class SpallocMachineManagerImpl implements MachineManager, Runnable {
    private static final String MACHINE_VERSION = "5";
    private static final String DEFAULT_TAG = "default";

    private final String ipAddress;
    private final int port;
    private final String owner;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ObjectMapper mapper = new ObjectMapper();
    private final BlockingQueue<ReturnResponse> responses = new LinkedBlockingQueue<>();
    private final BlockingQueue<JobsChangedResponse> notifications = new LinkedBlockingQueue<>();
    private Map<Integer, SpinnakerMachine> machinesAllocated = new HashMap<>();
    private Map<SpinnakerMachine, Integer> jobByMachine = new HashMap<>();
    private Map<Integer, JobState> machineState = new HashMap<>();
    private Map<Integer, MachineNotificationReceiver> callbacks = new HashMap<>();
    private Logger logger = getLogger(getClass());
    private final Object connectSync = new Object();

    private boolean connected = false;
    private boolean done = false;
    private MachineNotificationReceiver callback = null;

    public SpallocMachineManagerImpl(
            String ipAddress, int port, String owner) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Response.class, new ResponseBasedDeserializer());
        mapper.registerModule(module);
		mapper.setPropertyNamingStrategy(CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
		mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.ipAddress = ipAddress;
        this.port = port;
        this.owner = owner;
    }

    // ------------------------------ COMMS ------------------------------

    private static void waitfor(Object obj) {
        try {
            obj.wait();
        } catch (InterruptedException e) {
            // Does Nothing
        }
    }
    private <T> T getNextResponse(Class<T> responseType) throws IOException {
        ReturnResponse response;
        try {
			response = responses.take();
		} catch (InterruptedException e) {
			return null;
		}
		if (responseType == null)
			return null;
		return mapper.readValue(response.getReturnValue(), responseType);
    }

    private void waitForConnection() {
        synchronized (connectSync) {
            while (!connected) {
                logger.debug("Waiting for connection");
                waitfor(connectSync);
            }
        }
    }

	private synchronized <T> T sendRequest(Command<?> request,
			Class<T> responseType) throws IOException {
        waitForConnection();
        logger.trace("Sending message of type " + request.getCommand());
        writer.println(mapper.writeValueAsString(request));
        writer.flush();
        return getNextResponse(responseType);
    }

    private synchronized void sendRequest(Command<?> request)
            throws IOException {
        waitForConnection();
        logger.trace("Sending message of type " + request.getCommand());
        String line = mapper.writeValueAsString(request);
        logger.trace("Sending message: " + line);
        writer.println(line);
        writer.flush();
        getNextResponse(null);
    }

    @Override
	public void close() {
        done = true;
        connected = false;
        closeQuietly(socket);
    }

    private void updateJobState(int job) throws IOException {
    	logger.debug("Getting state of " + job);
    	JobState state = getJobState(job);
    	logger.debug("Job " + job + " is in state " + state.getState());
    	synchronized (machineState) {
    		machineState.put(job, state);
    		machineState.notifyAll();
    	}
    	
    	if (state.getState() == DESTROYED) {
    		SpinnakerMachine machine = machinesAllocated.remove(job);
    		if (machine == null) {
    			logger.error("Unrecognized job: " + job);
    			return;
    		}
    		jobByMachine.remove(machine);
    		MachineNotificationReceiver callback = callbacks.get(job);
    		if (callback != null)
    			callback.machineUnallocated(machine);
    	}
    }
    
    @Override
	public void run() {
        Runnable handler = new Runnable() {
            @Override
    		public void run() {
    			try {
					updateStateOfJobs();
    			} catch (InterruptedException e) {
    				// Stop thread on interruption
    			}
            }
        };

        Thread t = new Thread(handler, "JobState Update Notification Handler");
        t.setDaemon(true);
        t.start();

		ScheduledExecutorService scheduler = newScheduledThreadPool(1);
		try {
			scheduler.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					keepAllJobsAlive();
				}
			}, 5, 5, SECONDS);
			mainCommunicationsLoop();
		} finally {
			scheduler.shutdownNow();
		}
        // Send an empty JCR over
        notifications.offer(new JobsChangedResponse());
    }

	private void mainCommunicationsLoop() {
		while (!done) {
            try {
                connect();
            } catch (IOException e) {
                if (!done)
                    logger.error("Could not connect to machine server", e);
            }
			try {
				while (connected)
					readResponse();
			} catch (IOException e) {
				logger.error("Error receiving", e);
				if (!done)
					disconnect();
			}
			try {
				if (!done) {
					logger.warn("Disconnected from machine server...");
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
        }
	}

	private void readResponse() throws IOException, JsonParseException,
			JsonMappingException {
		String line = reader.readLine();
		if (line == null) {
			synchronized (connectSync) {
				connected = false;
				connectSync.notifyAll();
			}
			return;
		}

		logger.trace("Received response: " + line);
		Response response = mapper.readValue(line, Response.class);
		logger.trace("Received response of type " + response);
		if (response instanceof ReturnResponse)
			responses.offer((ReturnResponse) response);
		else if (response instanceof JobsChangedResponse)
			notifications.offer((JobsChangedResponse) response);
		else
			logger.error("Unrecognized response: " + response);
	}

	private void connect() throws UnknownHostException, IOException {
		synchronized (connectSync) {
			socket = new Socket(ipAddress, port);
			reader = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			writer = new PrintWriter(socket.getOutputStream());

			connected = true;
			connectSync.notifyAll();
		}
	}

	private void disconnect() {
		connected = false;
		closeQuietly(writer);
		closeQuietly(reader);
		closeQuietly(socket);
	}

    // ------------------------------ WIRE API ------------------------------

    JobMachineInfo getJobMachineInfo(int jobId) throws IOException {
		return sendRequest(new GetJobMachineInfoCommand(jobId),
				JobMachineInfo.class);
	}

	Machine[] listMachines() throws IOException {
		return sendRequest(new ListMachinesCommand(), Machine[].class);
	}

	int createJob(int nBoards) throws IOException {
		return sendRequest(new CreateJobCommand(nBoards, owner), Integer.class);
	}

	JobState getJobState(int jobId) throws IOException {
		return sendRequest(new GetJobStateCommand(jobId), JobState.class);
	}

	void notifyJob(int jobId) throws IOException {
		sendRequest(new NotifyJobCommand(jobId));
	}

	void noNotifyJob(int jobId) throws IOException {
		sendRequest(new NoNotifyJobCommand(jobId));
	}

	void jobKeepAlive(int jobId) throws IOException {
		sendRequest(new JobKeepAliveCommand(jobId));
	}

	void destroyJob(int jobId) throws IOException {
		sendRequest(new DestroyJobCommand(jobId));
	}

    // ------------------------------ API ------------------------------

	private SpinnakerMachine getMachineForJob(int jobId) throws IOException {
		JobMachineInfo info = getJobMachineInfo(jobId);
		return new SpinnakerMachine(info.getConnections().get(0).getHostname(),
				MACHINE_VERSION, info.getWidth(), info.getHeight(), 1, null);
	}

    private JobState waitForStates(int jobId, Integer... states) {
    	Set<Integer> set = new HashSet<>(asList(states));
        synchronized (machineState) {
            while (!machineState.containsKey(jobId) ||
                    !set.contains(machineState.get(jobId).getState())) {
                logger.debug(
                    "Waiting for job " + jobId + " to get to one of " + states);
                waitfor(machineState);
            }
            return machineState.get(jobId);
        }
    }

    @Override
    public List<SpinnakerMachine> getMachines() {
        try {
			List<SpinnakerMachine> machines = new ArrayList<>();
			for (Machine machine : listMachines())
				if (machine.getTags().contains(DEFAULT_TAG))
					machines.add(new SpinnakerMachine(machine.getName(),
							MACHINE_VERSION, machine.getWidth() * 12, machine
									.getHeight() * 12, machine.getWidth()
									* machine.getHeight(), null));
            return machines;
        } catch (IOException e) {
            logger.error("Error getting machines", e);
            return null;
        }
    }

    @Override
    public SpinnakerMachine getNextAvailableMachine(int nBoards) {
        while(true) {
            try {
				int jobId = createJob(nBoards);

				logger.debug("Got machine " + jobId
						+ ", requesting notifications");
				notifyJob(jobId);
				JobState state = getJobState(jobId);
				synchronized (machineState) {
					machineState.put(jobId, state);
				}

                logger.debug("Notifications for " + jobId + " are on");
				state = waitForStates(jobId, READY, DESTROYED);
                if (state.getState() == DESTROYED)
                    throw new RuntimeException(state.getReason());

				SpinnakerMachine machineAllocated = getMachineForJob(jobId);
                machinesAllocated.put(jobId, machineAllocated);
                jobByMachine.put(machineAllocated, jobId);
                if (callback != null)
                    callbacks.put(jobId, callback);
                return machineAllocated;
            } catch (IOException e) {
                logger.error("Error getting machine - retrying", e);
            }
        }
    }

    @Override
    public void releaseMachine(SpinnakerMachine machine) {
        Integer jobId = jobByMachine.remove(machine);
        if (jobId != null) {
            try {
                logger.debug("Turning off notification for " + jobId);
                noNotifyJob(jobId);
                logger.debug("Notifications for " + jobId + " are off");
                machinesAllocated.remove(jobId);
                synchronized (machineState) {
                    machineState.remove(jobId);
                }
                callbacks.remove(jobId);
                destroyJob(jobId);
                logger.debug("Job " + jobId + " destroyed");
            } catch (IOException e) {
                logger.error("Error releasing machine for " + jobId);
            }
        }
    }

    @Override
    public boolean isMachineAvailable(SpinnakerMachine machine) {
		Integer jobId = jobByMachine.get(machine);
		if (jobId == null)
			return false;
		logger.debug("Job " + jobId + " still available");
		return true;
    }

    @Override
    public boolean waitForMachineStateChange(SpinnakerMachine machine,
            int waitTime) {
        Integer jobId = jobByMachine.get(machine);
        if (jobId == null)
            return true;

        synchronized (machineState) {
            JobState state = machineState.get(jobId);
            try {
                machineState.wait(waitTime);
            } catch (InterruptedException e) {
                // Does Nothing
            }
            JobState newState = machineState.get(jobId);
            return (newState != null) && newState.equals(state);
        }
    }

    private void keepAllJobsAlive() {
		List<Integer> jobIds;
		synchronized (machineState) {
			jobIds = new ArrayList<>(machineState.keySet());
		}
		for (int jobId : jobIds)
			try {
				jobKeepAlive(jobId);
			} catch (IOException e) {
				logger.error("Error keeping machine " + jobId
						+ " alive");
			}
	}

	private void updateStateOfJobs() throws InterruptedException {
		while (!done)
			for (int job : notifications.take().getJobsChanged())
				try {
					updateJobState(job);
				} catch (IOException e) {
					logger.error("Error getting job state", e);
				}
	}

	// --------------------------- DEMO/TEST CODE ---------------------------

	private static void msg(String msg, Object...args) {
    	System.out.println(String.format(msg, args));
    }

    public static void main(String[] args) throws Exception {
		final SpallocMachineManagerImpl manager = new SpallocMachineManagerImpl(
				"10.0.0.3", 22244, "test");
		new Thread(manager).start();

		for (SpinnakerMachine machine : manager.getMachines())
			msg("%d x %d", machine.getWidth(), machine.getHeight());
		final SpinnakerMachine machine = manager.getNextAvailableMachine(1);

		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				boolean available = manager.isMachineAvailable(machine);
				while (available) {
					msg("Waiting for Machine to go");
					manager.waitForMachineStateChange(machine, 10000);
					available = manager.isMachineAvailable(machine);
				}
				msg("Machine gone");
			}
		});
		t.start();

		msg("Machine %s allocated", machine.getMachineName());
		Thread.sleep(20000);
		msg("Machine %s is available: %s", machine.getMachineName(),
				manager.isMachineAvailable(machine));
		manager.releaseMachine(machine);
		msg("Machine %s deallocated", machine.getMachineName());
		manager.close();
	}
}
