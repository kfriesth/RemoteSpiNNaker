package uk.ac.manchester.cs.spinnaker.machinemanager;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static uk.ac.manchester.cs.spinnaker.machinemanager.responses.JobState.DESTROYED;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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

public class SpallocMachineManagerImpl extends Thread
        implements MachineManager {
    private static final String MACHINE_VERSION = "5";
    private static final String DEFAULT_TAG = "default";

    private String ipAddress = null;
    private int port = -1;
    private Socket socket = null;
    private BufferedReader reader = null;
    private PrintWriter writer = null;
	private ObjectMapper mapper = new ObjectMapper();
	private Queue<ReturnResponse> responses = new LinkedList<>();
	private Queue<JobsChangedResponse> notifications = new LinkedList<>();
	private Map<Integer, SpinnakerMachine> machinesAllocated = new HashMap<>();
	private Map<SpinnakerMachine, Integer> jobByMachine = new HashMap<>();
	private Map<Integer, JobState> machineState = new HashMap<>();
	private Map<Integer, MachineNotificationReceiver> callbacks = new HashMap<>();
    private Log logger = LogFactory.getLog(getClass());
    private Integer connectSync = new Integer(0);
    private boolean connected = false;
    private boolean done = false;
    private MachineNotificationReceiver callback = null;
    private String owner = null;

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

    private <T> T getNextResponse(Class<T> responseType) throws IOException {
        ReturnResponse response = null;
        synchronized (responses) {
            while (responses.isEmpty()) {
                try {
                    responses.wait();
                } catch (InterruptedException e) {
                    // Does Nothing
                }
            }
            response = responses.poll();
        }
		if (responseType == null)
			return null;
		return mapper.readValue(response.getReturnValue(), responseType);
    }

    private void waitForConnection() {
        synchronized (connectSync) {
            while (!connected) {
                logger.debug("Waiting for connection");
                try {
                    connectSync.wait();
                } catch (InterruptedException e) {
                    // Does Nothing
                }
            }
        }
    }

    private synchronized <T> T sendRequest(
            Command<?, ?> request, Class<T> responseType)
            throws IOException {
        waitForConnection();
        logger.trace("Sending message of type " + request.getCommand());
        writer.println(mapper.writeValueAsString(request));
        writer.flush();
        return getNextResponse(responseType);
    }

    private synchronized void sendRequest(Command<?, ?> request)
            throws IOException {
        waitForConnection();
        logger.trace("Sending message of type " + request.getCommand());
        String line = mapper.writeValueAsString(request);
        logger.trace("Sending message: " + line);
        writer.println(line);
        writer.flush();
        getNextResponse(null);
    }

	private SpinnakerMachine getMachineForJob(int jobId) throws IOException {
		JobMachineInfo info = sendRequest(new GetJobMachineInfoCommand(jobId),
				JobMachineInfo.class);
		return new SpinnakerMachine(info.getConnections().get(0).getHostname(),
				MACHINE_VERSION, info.getWidth(), info.getHeight(), 1, null);
	}

    private JobState waitForStates(int jobId, Set<Integer> states) {
        synchronized (machineState) {
            while (!machineState.containsKey(jobId) ||
                    !states.contains(machineState.get(jobId).getState())) {
                logger.debug(
                    "Waiting for job " + jobId + " to get to one of " + states);
                try {
                    machineState.wait();
                } catch (InterruptedException e) {
                    // Does Nothing
                }
            }
            return machineState.get(jobId);
        }
    }

    @Override
    public List<SpinnakerMachine> getMachines() {
        try {
            Machine[] spallocMachines = sendRequest(
                new ListMachinesCommand(), Machine[].class);
            List<SpinnakerMachine> machines = new ArrayList<>();
            for (Machine machine : spallocMachines)
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
        SpinnakerMachine machineAllocated = null;
        while (machineAllocated == null) {
            try {
				int jobId = sendRequest(new CreateJobCommand((int) nBoards,
						owner), Integer.class);

				logger.debug("Got machine " + jobId
						+ ", requesting notifications");
				sendRequest(new NotifyJobCommand(jobId));
				JobState state = sendRequest(new GetJobStateCommand(jobId),
						JobState.class);
				synchronized (machineState) {
					machineState.put(jobId, state);
				}

                logger.debug("Notifications for " + jobId + " are on");
                state = waitForStates(jobId, new HashSet<Integer>(Arrays.asList(
                    JobState.READY, JobState.DESTROYED)));
                if (state.getState() == JobState.DESTROYED)
                    throw new RuntimeException(state.getReason());

                machineAllocated = getMachineForJob(jobId);
                machinesAllocated.put(jobId, machineAllocated);
                jobByMachine.put(machineAllocated, jobId);
                if (callback != null)
                    callbacks.put(jobId, callback);
            } catch (IOException e) {
                logger.error("Error getting machine - retrying", e);
            }
        }

        return machineAllocated;
    }

    @Override
    public void releaseMachine(SpinnakerMachine machine) {
        Integer jobId = jobByMachine.remove(machine);
        if (jobId != null) {
            try {
                logger.debug("Turning off notification for " + jobId);
                sendRequest(new NoNotifyJobCommand(jobId));
                logger.debug("Notifications for " + jobId + " are off");
                machinesAllocated.remove(jobId);
                synchronized (machineState) {
                    machineState.remove(jobId);
                }
                callbacks.remove(jobId);
                sendRequest(new DestroyJobCommand(jobId));
                logger.debug("Job " + jobId + " destroyed");
            } catch (IOException e) {
                logger.error("Error releasing machine for " + jobId);
            }
        }
    }

    @Override
    public boolean isMachineAvailable(SpinnakerMachine machine) {
        Integer jobId = jobByMachine.get(machine);
        if (jobId != null) {
            logger.debug("Job " + jobId + " still available");
            return true;
        }
        return false;
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

    @Override
	public void close() {
        done = true;
        connected = false;
		try {
			if (socket != null)
				socket.close();
		} catch (IOException e) {
			// Does Nothing
		}
    }

    @Override
	public void run() {
        NotificationHandler handler = new NotificationHandler();
        handler.start();

		ScheduledExecutorService scheduler = newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(new KeepAlive(), 5, 5, SECONDS);

        while (!done) {
            try {
                connect();
                while (connected) {
                    try {
                        readResponse();
                    } catch (IOException e) {
                        if (!done) {
                            logger.error("Error receiving", e);
                            disconnect();
                        }
                    }
                }
            } catch (IOException e) {
                if (!done)
                    logger.error("Could not connect to machine server", e);
            }
            if (!done) {
                logger.warn("Disconnected from machine server...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Does Nothing
                }
            }
        }

        scheduler.shutdownNow();
        synchronized (notifications) {
            notifications.notifyAll();
        }
    }

	private static <T> void enqueue(Queue<T> queue, T item) {
		synchronized (queue) {
			queue.add(item);
			queue.notifyAll();
		}
	}

	private void readResponse() throws IOException, JsonParseException,
			JsonMappingException {
		String line = reader.readLine();
		if (line != null) {
			logger.trace("Received response: " + line);
			Response response = mapper.readValue(line, Response.class);
			logger.trace("Received response of type " + response);
			if (response instanceof ReturnResponse)
				enqueue(responses, (ReturnResponse) response);
			else if (response instanceof JobsChangedResponse)
				enqueue(notifications, (JobsChangedResponse) response);
			else
				logger.error("Unrecognized response: " + response);
		} else {
			synchronized (connectSync) {
				connected = false;
				connectSync.notifyAll();
			}
		}
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
		try {
			writer.close();
		} catch (Exception e2) {
			// Do Nothing
		}
		try {
			reader.close();
		} catch (Exception e2) {
			// Do Nothing
		}
		try {
			socket.close();
		} catch (Exception e2) {
			// Do Nothing
		}
	}
	
    private class NotificationHandler extends Thread {
        public void notify(JobsChangedResponse response) {
            for (int job : response.getJobsChanged())
                try {
                    notifyJob(job);
                } catch (IOException e) {
                    logger.error("Error getting job state", e);
                }
        }

		private void notifyJob(int job) throws IOException {
			logger.debug("Getting state of " + job);
			JobState state = sendRequest(new GetJobStateCommand(job),
					JobState.class);
			logger.debug("Job " + job + " is in state " + state.getState());
			synchronized (machineState) {
				machineState.put(job, state);
				machineState.notifyAll();
			}

			if (state.getState() == DESTROYED) {
				SpinnakerMachine machine = machinesAllocated.remove(job);
				if (machine != null) {
					jobByMachine.remove(machine);
					MachineNotificationReceiver callback = callbacks.get(job);
					if (callback != null)
						callback.machineUnallocated(machine);
				} else
					logger.error("Unrecognized job: " + job);
			}
		}

        @Override
		public void run() {
            while (!done) {
                JobsChangedResponse response = null;
                synchronized (notifications) {
                    while (!done && notifications.isEmpty())
                        try {
                            notifications.wait();
                        } catch (InterruptedException e) {
                            // Does Nothing
                        }
                    if (!done)
                        response = notifications.poll();
                }

                if (response != null)
                    notify(response);
            }
        }
    }

    private class KeepAlive implements Runnable {
        @Override
		public void run() {
            for (int jobId : machineState.keySet())
                try {
                    sendRequest(new JobKeepAliveCommand(jobId));
                } catch (IOException e) {
                    logger.error("Error keeping machine " + jobId + " alive");
                }
        }
    }

    private static void msg(String msg, Object...args) {
    	System.err.println(String.format(msg, args));
    }

    public static void main(String[] args) throws Exception {
		final SpallocMachineManagerImpl manager = new SpallocMachineManagerImpl(
				"10.0.0.3", 22244, "test");
		manager.start();

		for (SpinnakerMachine machine : manager.getMachines())
			msg("%d x %d", machine.getWidth(), machine.getHeight());
		final SpinnakerMachine machine = manager.getNextAvailableMachine(1);

		Thread t = new Thread() {
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
		};
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
