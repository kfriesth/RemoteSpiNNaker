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
import static uk.ac.manchester.cs.spinnaker.utils.ThreadUtils.sleep;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;

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
import uk.ac.manchester.cs.spinnaker.rest.utils.PropertyBasedDeserialiser;
import uk.ac.manchester.cs.spinnaker.utils.ThreadUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class SpallocMachineManagerImpl implements MachineManager, Runnable {
	private static final String MACHINE_VERSION = "5";
	private static final String DEFAULT_TAG = "default";

	public interface MachineNotificationReceiver {
		/**
		 * Indicates that a machine is no longer allocated
		 * 
		 * @param machine
		 *            The machine that is no longer allocated
		 */
		void machineUnallocated(SpinnakerMachine machine);
	}

    @Value("${spalloc.server}")
	private String ipAddress;
    @Value("${spalloc.port}")
	private int port;
    @Value("${spalloc.user.name}")
	private String owner;

	private ObjectMapper mapper = new ObjectMapper();
	private Map<Integer, SpinnakerMachine> machinesAllocated = new HashMap<>();
	private Map<SpinnakerMachine, Integer> jobByMachine = new HashMap<>();
	private Map<Integer, JobState> machineState = new HashMap<>();
	private Map<Integer, MachineNotificationReceiver> callbacks = new HashMap<>();
	private Logger logger = getLogger(getClass());
	private Comms comms = new Comms();

	private volatile boolean done = false;
	private MachineNotificationReceiver callback = null;

	@SuppressWarnings("serial")
	static private class ResponseBasedDeserializer extends
			PropertyBasedDeserialiser<Response> {
		ResponseBasedDeserializer() {
			super(Response.class);
			register("jobs_changed", JobsChangedResponse.class);
			register("return", ReturnResponse.class);
		}
	}

	public SpallocMachineManagerImpl() {
		SimpleModule module = new SimpleModule();
		module.addDeserializer(Response.class, new ResponseBasedDeserializer());
		mapper.registerModule(module);
		mapper.setPropertyNamingStrategy(CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
		mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	ScheduledExecutorService scheduler;

	@PostConstruct
	void startThreads() {
		final ThreadGroup group = new ThreadGroup("Spalloc");
		scheduler = newScheduledThreadPool(1, new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				return new Thread(group, r, "Spalloc Keep Alive Handler");
			}
		});

		new Thread(group, this, "Spalloc Comms Interface").start();

		Thread t = new Thread(group, new Runnable() {
			@Override
			public void run() {
				updateStateOfJobs();
			}
		}, "Spalloc JobState Update Notification Handler");
		t.setDaemon(true);
		t.start();

		scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				keepAllJobsAlive();
			}
		}, 5, 5, SECONDS);
	}

	// ------------------------------ COMMS ------------------------------

	private static boolean waitfor(Object obj) {
		try {
			obj.wait();
			return false;
		} catch (InterruptedException e) {
			return true;
		}
	}

	class Comms {
		private final BlockingQueue<ReturnResponse> responses = new LinkedBlockingQueue<>();
		private final BlockingQueue<JobsChangedResponse> notifications = new LinkedBlockingQueue<>();
		private Socket socket;
		private BufferedReader reader;
		private PrintWriter writer;
		private volatile boolean connected = false;

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

		private synchronized void waitForConnection() {
			while (!connected) {
				logger.debug("Waiting for connection");
				if (waitfor(this))
					break;
			}
		}

		private void writeRequest(Command<?> request) throws IOException {
			logger.trace("Sending message of type " + request.getCommand());
			writer.println(mapper.writeValueAsString(request));
			writer.flush();
		}

		private void readResponse() throws IOException {
			// Note, assumes one response per line
			String line = reader.readLine();
			if (line == null) {
				synchronized (this) {
					connected = false;
					notifyAll();
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

		public void mainLoop() {
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
				if (!done) {
					logger.warn("Disconnected from machine server...");
					sleep(1000);
				}
			}
		}

		public synchronized void connect() throws IOException {
			socket = new Socket(ipAddress, port);
			reader = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			writer = new PrintWriter(socket.getOutputStream());

			connected = true;
			// Send an empty JCR over
			notifications.offer(new JobsChangedResponse());
			notifyAll();
		}

		public void disconnect() {
			connected = false;
			closeQuietly(writer);
			closeQuietly(reader);
			closeQuietly(socket);
		}

		public <T> T sendRequest(Command<?> request, Class<T> responseType)
				throws IOException {
			synchronized (SpallocMachineManagerImpl.this) {
				waitForConnection();
				writeRequest(request);
				return getNextResponse(responseType);
			}
		}

		public void sendRequest(Command<?> request) throws IOException {
			synchronized (SpallocMachineManagerImpl.this) {
				waitForConnection();
				writeRequest(request);
				getNextResponse(null);
			}
		}

		public List<Integer> getJobsChanged() throws InterruptedException {
			return notifications.take().getJobsChanged();
		}
	}

	@Override
	public void close() {
		done = true;
		comms.disconnect();
	}

	@Override
	public void run() {
		try {
			comms.mainLoop();
		} finally {
			scheduler.shutdownNow();
		}
	}

	// ------------------------------ WIRE Job ------------------------------

	class Job {
		final int id;

		Job(int jobId) {
			this.id = jobId;
		}

		JobMachineInfo getMachineInfo() throws IOException {
			return comms.sendRequest(new GetJobMachineInfoCommand(id),
					JobMachineInfo.class);
		}

		JobState getState() throws IOException {
			return comms
					.sendRequest(new GetJobStateCommand(id), JobState.class);
		}

		void notify(boolean enable) throws IOException {
			if (enable)
				comms.sendRequest(new NotifyJobCommand(id));
			else
				comms.sendRequest(new NoNotifyJobCommand(id));
		}

		void keepAlive() throws IOException {
			comms.sendRequest(new JobKeepAliveCommand(id));
		}

		void destroy() throws IOException {
			comms.sendRequest(new DestroyJobCommand(id));
		}
	}

	Machine[] listMachines() throws IOException {
		return comms.sendRequest(new ListMachinesCommand(), Machine[].class);
	}

	Job createJob(int nBoards) throws IOException {
		return new Job(comms.sendRequest(new CreateJobCommand(nBoards, owner),
				Integer.class));
	}

	// ------------------------------ Job ------------------------------

	private void updateJobState(Job job) throws IOException {
		logger.debug("Getting state of " + job.id);
		JobState state = job.getState();
		logger.debug("Job " + job + " is in state " + state.getState());
		synchronized (machineState) {
			machineState.put(job.id, state);
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

	private SpinnakerMachine getMachineForJob(Job job) throws IOException {
		JobMachineInfo info = job.getMachineInfo();
		return new SpinnakerMachine(info.getConnections().get(0).getHostname(),
				MACHINE_VERSION, info.getWidth(), info.getHeight(), 1, null);
	}

	private JobState waitForStates(Job job, Integer... states) {
		Set<Integer> set = new HashSet<>(asList(states));
		synchronized (machineState) {
			while (!machineState.containsKey(job.id)
					|| !set.contains(machineState.get(job.id).getState())) {
				logger.debug("Waiting for job " + job.id + " to get to one of "
						+ states);
				if (waitfor(machineState))
					return null;
			}
			return machineState.get(job.id);
		}
	}

	private static int MACHINE_WIDTH_FACTOR = 12;
	private static int MACHINE_HEIGHT_FACTOR = 12;

	@Override
	public List<SpinnakerMachine> getMachines() {
		try {
			List<SpinnakerMachine> machines = new ArrayList<>();
			for (Machine machine : listMachines())
				if (machine.getTags().contains(DEFAULT_TAG))
					machines.add(new SpinnakerMachine(machine.getName(),
							MACHINE_VERSION, machine.getWidth()
									* MACHINE_WIDTH_FACTOR, machine.getHeight()
									* MACHINE_HEIGHT_FACTOR, machine.getWidth()
									* machine.getHeight(), null));
			return machines;
		} catch (IOException e) {
			logger.error("Error getting machines", e);
			return null;
		}
	}

	@Override
	public SpinnakerMachine getNextAvailableMachine(int nBoards) {
		Job job = null;
		SpinnakerMachine machineAllocated = null;

		while (job == null || machineAllocated == null) {
			try {
				job = createJob(nBoards);

				logger.debug("Got machine " + job.id
						+ ", requesting notifications");
				job.notify(true);
				JobState state = job.getState();
				synchronized (machineState) {
					machineState.put(job.id, state);
				}
				logger.debug("Notifications for " + job.id + " are on");

				state = waitForStates(job, READY, DESTROYED);
				if (state.getState() == DESTROYED)
					throw new RuntimeException(state.getReason());

				machineAllocated = getMachineForJob(job);
			} catch (IOException e) {
				logger.error("Error getting machine - retrying", e);
			}
		}

		machinesAllocated.put(job.id, machineAllocated);
		jobByMachine.put(machineAllocated, job.id);
		if (callback != null)
			callbacks.put(job.id, callback);
		return machineAllocated;
	}

	@Override
	public void releaseMachine(SpinnakerMachine machine) {
		Integer jobId = jobByMachine.remove(machine);
		if (jobId != null) {
			Job job = new Job(jobId);
			try {
				logger.debug("Turning off notification for " + jobId);
				job.notify(false);
				logger.debug("Notifications for " + jobId + " are off");
				machinesAllocated.remove(jobId);
				synchronized (machineState) {
					machineState.remove(jobId);
				}
				callbacks.remove(jobId);
				job.destroy();
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
				new Job(jobId).keepAlive();
			} catch (IOException e) {
				logger.error("Error keeping machine " + jobId + " alive");
			}
	}

	private void updateStateOfJobs() {
		try {
			while (!done)
				for (int jobId : comms.getJobsChanged())
					try {
						updateJobState(new Job(jobId));
					} catch (IOException e) {
						logger.error("Error getting job state", e);
					}
		} catch (InterruptedException e) {
			logger.warn("interrupt of job state updating");
		}
	}

	// --------------------------- DEMO/TEST CODE ---------------------------

	public static class Demo {
		private static void msg(String msg, Object... args) {
			System.out.println(String.format(msg, args));
		}

		public static void main(String[] args) throws Exception {
			final SpallocMachineManagerImpl manager = new SpallocMachineManagerImpl();
			manager.ipAddress = "10.0.0.3";
			manager.port = 22244;
			manager.owner = "test";
			manager.startThreads();

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
			ThreadUtils.sleep(20000);
			msg("Machine %s is available: %s", machine.getMachineName(),
					manager.isMachineAvailable(machine));
			manager.releaseMachine(machine);
			msg("Machine %s deallocated", machine.getMachineName());
			manager.close();
		}
	}
}
