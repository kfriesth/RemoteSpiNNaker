package uk.ac.manchester.cs.spinnaker.jobmanager;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage.DAO.State.Done;
import static uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage.DAO.State.Running;
import static uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage.DAO.State.Waiting;
import static uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage.DAO.Values.where;
import static uk.ac.manchester.cs.spinnaker.utils.DirectoryUtils.mktmpdir;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage.DAO.State;
import uk.ac.manchester.cs.spinnaker.jobmanager.XenVMExecuterFactory.XenVMDescriptor;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface JobStorage {
	void addJob(Job job);

	void addJob(Job job, String executerId);

	Job getJob(String executerId);

	Job getJob(int jobId);

	List<Job> getWaiting();

	boolean isRunning(Job job);

	void markDone(Job job);

	void markRunning(Job job);

	/**
	 * @param job
	 *            the job to update the assignment for
	 * @param executer
	 *            The executer that is assigned to the job. May be <tt>null</tt>
	 *            to clear the assignment.
	 */
	void assignNewExecutor(Job job, JobExecuter executer);

	void initResourceUsage(Job job, long plannedResourceUsage, long quotaNCores);

	void setResourceUsage(Job job, double seconds);

	long getResourceUsage(Job job);

	void addProvenance(Job job, String key, String value);

	Map<String, String> getProvenance(Job job);

	/** Get (or create) the temporary directory for storing output files in. */
	File getTempDirectory(Job job) throws IOException;

	List<SpinnakerMachine> getMachines(Job job);

	void addMachine(Job job, SpinnakerMachine machine);

	void addXenVm(String id, XenVMDescriptor descriptor);

	void removeXenVm(String id);

	XenVMDescriptor getXenVm(String id);

	/**
	 * Describe <i>all</i> the VMs that we know about.
	 * @return a map of IDs to VM descriptors. Never <tt>null</tt>.
	 */
	Map<String, XenVMDescriptor> getXenVms();

	class Queue implements JobStorage {
		private Map<String, Integer> map = new ConcurrentHashMap<>();
		private Set<Integer> running = new ConcurrentSkipListSet<>();
		private Map<Integer, Job> store = new ConcurrentHashMap<>();
		private Set<Integer> waiting = new ConcurrentSkipListSet<>();
		private Map<Integer, Long> resourceUsage = new ConcurrentHashMap<>();
		private Map<Integer, Long> nCores = new ConcurrentHashMap<>();
		private Map<Integer, Map<String, String>> provenance = new HashMap<>();
		private Map<Integer, File> tempDir = new HashMap<>();
		private Map<Integer, List<SpinnakerMachine>> machines = new ConcurrentHashMap<>();

		@Override
		public void addJob(Job job) {
			if (getJob(job.getId()) == null) {
				store.put(job.getId(), job);
				// What state should it be in in this case?
			}
		}

		@Override
		public void addJob(Job job, String executerId) {
			store.put(job.getId(), job);
			if (executerId != null)
				map.put(executerId, job.getId());
			waiting.add(job.getId());
		}

		@Override
		public Job getJob(String executerId) {
			return store.get(map.get(executerId));
		}

		@Override
		public Job getJob(int jobId) {
			return store.get(jobId);
		}

		@Override
		public List<Job> getWaiting() {
			List<Job> result = new ArrayList<>();
			for (Integer id : waiting)
				result.add(store.get(id));
			return result;
		}

		@Override
		public boolean isRunning(Job job) {
			if (job == null)
				return false;
			return running.contains(job.getId());
		}

		@Override
		public void markDone(Job job) {
			if (job != null) {
				waiting.remove(job.getId());
				running.remove(job.getId());
			}
		}

		@Override
		public void markRunning(Job job) {
			if (job != null) {
				waiting.remove(job.getId());
				running.add(job.getId());
			}
		}

		@Override
		public void assignNewExecutor(Job job, JobExecuter executer) {
			for (Entry<String, Integer> e : map.entrySet())
				if (e.getValue().equals(job.getId())) {
					map.remove(e.getKey());
					break;
				}
			if (executer != null)
				map.put(executer.getExecuterId(), job.getId());
		}

		@Override
		public void initResourceUsage(Job job, long resourceUsage, long nCores) {
			this.resourceUsage.put(job.getId(), resourceUsage);
			this.nCores.put(job.getId(), nCores);
		}

		@Override
		public void setResourceUsage(Job job, double seconds) {
			Long cores = nCores.get(job.getId());
			if (cores != null)
				resourceUsage.put(job.getId(), (long) (seconds * cores));
		}

		@Override
		public long getResourceUsage(Job job) {
			Integer id = job.getId();
			nCores.remove(id);
			return resourceUsage.get(id);
		}

		@Override
		public void addProvenance(Job job, String key, String value) {
			synchronized (provenance) {
				Map<String, String> map = provenance.get(job.getId());
				if (map == null) {
					map = new LinkedHashMap<>();
					provenance.put(job.getId(), map);
				}
				map.put(key, value);
			}
		}

		@Override
		public Map<String, String> getProvenance(Job job) {
			synchronized (provenance) {
				Map<String, String> map = provenance.get(job.getId());
				if (map == null || map.isEmpty())
					return null;
				return new LinkedHashMap<>(map);
			}
		}

		@Override
		public File getTempDirectory(Job job) throws IOException {
			synchronized (tempDir) {
				File tmp = tempDir.get(job.getId());
				if (tmp == null) {
					tmp = mktmpdir();
					tempDir.put(job.getId(), tmp);
				}
				return tmp;
			}
		}

		@Override
		public List<SpinnakerMachine> getMachines(Job job) {
			synchronized (machines) {
				List<SpinnakerMachine> m = machines.get(job.getId());
				if (m == null)
					return emptyList();
				return new ArrayList<>(m);
			}
		}

		@Override
		public void addMachine(Job job, SpinnakerMachine machine) {
			synchronized (machines) {
				List<SpinnakerMachine> m = machines.get(job.getId());
				if (m == null)
					machines.put(job.getId(), m = new ArrayList<>());
				m.add(machine);
			}
		}

		@Override
		public void addXenVm(String id, XenVMDescriptor descriptor) {
			// Do nothing to persist this right now
		}

		@Override
		public XenVMDescriptor getXenVm(String id) {
			// We never have a descriptor; always null
			return null;
		}

		@Override
		public void removeXenVm(String id) {
			// Never need to do anything; it was never here
		}

		@Override
		public Map<String, XenVMDescriptor> getXenVms() {
			return emptyMap();
		}
	}

	class DAO implements JobStorage {
		private NamedParameterJdbcTemplate db;

		@Autowired
		public void setDataSource(DataSource dataSource) {
			this.db = new NamedParameterJdbcTemplate(dataSource);
		}

		public static enum State {
			Waiting(0), Running(1), Done(2);
			private final int state;

			State(int code) {
				this.state = code;
			}

			public int getCode() {
				return state;
			}

			public static State get(int i) {
				for (State s : values())
					if (s.getCode() == i)
						return s;
				throw new IllegalArgumentException("unexpected state code: "
						+ i);
			}
		}

		private static final String ADD_JOB = "INSERT INTO job (id, json, state, executer, creation) "
				+ "VALUES (:job, :json, 0, :executer, :timestamp)";
		private static final String ADD_MACH = "INSERT INTO jobMachines (id, machine) "
				+ "VALUES (:job, :machine)";
		private static final String ADD_PROV = "INSERT OR REPLACE INTO jobProvenance (id, provKey, provValue) "
				+ "VALUES (:job, :key, :value)";
		private static final String ADD_XENVM = "INSERT INTO xen (id,vm,disk,image,extraDisk,extraImage) "
				+ "VALUES (:id,:vmid,:disk1id,:image1id,:disk2id,:image2id)";

		private static final String EXISTS_JOB = "SELECT EXISTS(1 AS b FROM job WHERE id = :id LIMIT 1)";
		private static final String GET_JOB_BY_ID = "SELECT json FROM job WHERE id = :id LIMIT 1";
		private static final String GET_JOB_BY_EXECUTER = "SELECT json FROM job WHERE executer = :executer LIMIT 1";
		private static final String GET_IN_STATE = "SELECT json FROM job WHERE state = :state";
		private static final String GET_STATE = "SELECT state FROM job WHERE id = :job LIMIT 1";
		private static final String GET_RU = "SELECT resourceUsage FROM job WHERE id = :job LIMIT 1";
		private static final String GET_PROV = "SELECT provKey, provValue FROM jobProvenance WHERE id = :job";
		private static final String GET_TMPDIR = "SELECT temporaryDirectory FROM job WHERE id = :job LIMIT 1";
		private static final String GET_MACH = "SELECT machine FROM jobMachines WHERE id = :job";
		private static final String GET_XENVM = "SELECT vm AS vmid, disk AS disk1id, image AS image1id, extraDisk AS disk2id, extraImage AS image2id FROM xen WHERE id = :id LIMIT 1";
		private static final String GET_XENVMS = "SELECT id, vm AS vmid, disk AS disk1id, image AS image1id, extraDisk AS disk2id, extraImage AS image2id FROM xen";

		private static final String SET_EXEC = "UPDATE job SET executer = :executer WHERE id = :job";
		private static final String SET_STATE = "UPDATE job SET state = :state WHERE id = :job";
		private static final String SET_TMPDIR = "UPDATE job SET temporaryDirectory = :tempdir WHERE id = :job";
		private static final String INIT_RU = "UPDATE job SET resourceUsage = :resources, numCores = :cores WHERE id = :job";
		private static final String SET_RU = "UPDATE job SET resourceUsage = CAST(numCores * :seconds AS INTEGER) WHERE id = :job";

		private static final String DELETE_XENVM = "DELETE FROM xen WHERE id = :id";

		@Override
		public void addJob(Job job) {
			requireNonNull(job, "can only register real jobs");
			requireNonNull(job.getId(), "can only register jobs with IDs");
			Integer i = db.queryForObject(EXISTS_JOB, where("id", job.getId()),
					Integer.class);
			if (i == null || i.intValue() == 0) {
				addJob(job, null);
				setState(job, Done);
			}
		}

		@Override
		public void addJob(Job job, String executerId) {
			requireNonNull(job, "can only register real jobs");
			requireNonNull(job.getId(), "can only register jobs with IDs");
			db.update(
					ADD_JOB,
					where("job", job).and("json", JobMapper.map(job))
							.and("executer", executerId)
							.and("timestamp", new Date()));
		}

		@Override
		public Job getJob(String executerId) {
			if (executerId == null)
				return null;
			return db.queryForObject(GET_JOB_BY_EXECUTER,
					where("executer", executerId), new JobMapper("json"));
		}

		@Override
		public Job getJob(int jobId) {
			try {
				return db.queryForObject(GET_JOB_BY_ID, where("id", jobId),
						new JobMapper("json"));
			} catch (DataAccessException e) {
				return null;
			}
		}

		private List<Job> getInState(State state) {
			return db.query(GET_IN_STATE, where("state", state), new JobMapper(
					"json"));
		}

		private State getState(Job job) {
			if (job == null)
				return null;
			return db.queryForObject(GET_STATE, where("job", job),
					new StateMapper("state"));
		}

		private void setState(Job job, State state) {
			if (job != null)
				db.update(SET_STATE, where("job", job).and("state", state));
		}

		@Override
		public List<Job> getWaiting() {
			return getInState(Waiting);
		}

		@Override
		public boolean isRunning(Job job) {
			if (job == null)
				return false;
			return getState(job) == Running;
		}

		@Override
		public void markDone(Job job) {
			if (job != null)
				setState(job, Done);
		}

		@Override
		public void markRunning(Job job) {
			if (job != null)
				setState(job, Running);
		}

		@Override
		public void assignNewExecutor(Job job, JobExecuter executer) {
			if (job != null)
				db.update(SET_EXEC, where("job", job).and("executer", executer));
		}

		@Override
		public void initResourceUsage(Job job, long resourceUsage,
				long quotaNCores) {
			db.update(INIT_RU, where("job", job)
					.and("resources", resourceUsage).and("cores", quotaNCores));
		}

		@Override
		public void setResourceUsage(Job job, double seconds) {
			db.update(SET_RU, where("job", job).and("seconds", seconds));
		}

		@Override
		public long getResourceUsage(Job job) {
			try {
				Long l = db.queryForObject(GET_RU, where("job", job),
						Long.class);
				return l == null ? 0 : l;
			} catch (DataAccessException e) {
				return 0;
			}
		}

		@Override
		public void addProvenance(Job job, String key, String value) {
			db.update(ADD_PROV,
					where("job", job).and("key", key).and("value", value));
		}

		@Override
		public Map<String, String> getProvenance(Job job) {
			return db.query(GET_PROV, where("job", job),
					new MapExtractor<String>("provKey", "provValue") {
						@Override
						protected String extractColumn(ResultSet rs,
								int column) throws SQLException {
							return rs.getString(column);
						}
					});
		}

		@Override
		public File getTempDirectory(Job job) throws IOException {
			File tmp = db.queryForObject(GET_TMPDIR, where("job", job),
					File.class);
			if (tmp == null) {
				tmp = mktmpdir();
				db.update(SET_TMPDIR, where("job", job).and("tempdir", tmp));
			}
			return tmp;
		}

		@Override
		public List<SpinnakerMachine> getMachines(Job job) {
			return db.query(GET_MACH, where("job", job), new SpinMachineMapper(
					"machine"));
		}

		@Override
		public void addMachine(Job job, SpinnakerMachine machine) {
			db.update(ADD_MACH, where("job", job).and("machine", machine));
		}

		@Override
		public void addXenVm(String id, XenVMDescriptor descriptor) {
			db.update(
					ADD_XENVM,
					where("id", id).and("vmid", descriptor.vm)
							.and("disk1id", descriptor.disk1)
							.and("image1id", descriptor.image1)
							.and("disk2id", descriptor.disk2)
							.and("image2id", descriptor.image2));
		}

		@Override
		public XenVMDescriptor getXenVm(String id) {
			return db.queryForObject(GET_XENVM, where("id", id),
					new RowMapper<XenVMDescriptor>() {
						@Override
						public XenVMDescriptor mapRow(ResultSet rs, int rowNum)
								throws SQLException {
							XenVMDescriptor d = new XenVMDescriptor();
							d.vm = rs.getString("vmid");
							d.disk1 = rs.getString("disk1id");
							d.image1 = rs.getString("image1id");
							d.disk2 = rs.getString("disk2id");
							d.image2 = rs.getString("image2id");
							return d;
						}
					});
		}

		@Override
		public void removeXenVm(String id) {
			db.update(DELETE_XENVM, where("id", id));
		}

		@Override
		public Map<String, XenVMDescriptor> getXenVms() {
			return db.query(GET_XENVMS,
					new ResultSetExtractor<Map<String, XenVMDescriptor>>() {
						@Override
						public Map<String, XenVMDescriptor> extractData(
								ResultSet rs) throws SQLException,
								DataAccessException {
							int execid = rs.findColumn("id");
							int vmid = rs.findColumn("vmid");
							int disk1id = rs.findColumn("disk1id");
							int image1id = rs.findColumn("image1id");
							int disk2id = rs.findColumn("disk2id");
							int image2id = rs.findColumn("image2id");
							Map<String, XenVMDescriptor> map = new HashMap<>();
							while (rs.next()) {
								XenVMDescriptor d = new XenVMDescriptor();
								d.vm = rs.getString(vmid);
								d.disk1 = rs.getString(disk1id);
								d.image1 = rs.getString(image1id);
								d.disk2 = rs.getString(disk2id);
								d.image2 = rs.getString(image2id);
								map.put(rs.getString(execid), d);
							}
							return map;
						}
					});
		}

		@SuppressWarnings("serial")
		static class Values extends HashMap<String, Object> {
			private static Object ser(Object o) {
				if (o == null)
					return null;
				if (o instanceof Job)
					return ((Job) o).getId();
				if (o instanceof State)
					return ((State) o).getCode();
				if (o instanceof SpinnakerMachine)
					return o.toString();
				if (o instanceof Date)
					return ((Date) o).getTime();
				if (o instanceof JobExecuter)
					return ((JobExecuter) o).getExecuterId();
				return o;
			}

			static Values where(String key, Object value) {
				Values v = new Values();
				v.put(key, ser(value));
				return v;
			}

			Values and(String key, Object value) {
				put(key, ser(value));
				return this;
			}
		}
	}
}

abstract class SingleColumnMapper<T> implements RowMapper<T> {
	public SingleColumnMapper(String columnLabel) {
		label = columnLabel;
	}

	private String label;
	/**
	 * The column index corresponding to the label. Initialised before
	 * subclasses perform any mapping.
	 */
	protected int column;

	protected abstract T mapRow(ResultSet rs) throws Exception;

	@Override
	public final T mapRow(ResultSet rs, int row) throws SQLException {
		if (row == 0)
			column = rs.findColumn(label);
		try {
			return mapRow(rs);
		} catch (SQLException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("problem in object handling", e);
		}
	}
}

class StateMapper extends SingleColumnMapper<State> {
	public StateMapper(String columnLabel) {
		super(columnLabel);
	}

	@Override
	protected State mapRow(ResultSet rs) throws Exception {
		return State.get(rs.getInt(column));
	}
}

class JobMapper extends SingleColumnMapper<Job> {
	public JobMapper(String columnLabel) {
		super(columnLabel);
	}

	private static ObjectMapper mapper = new ObjectMapper();

	public static String map(Job job) {
		try {
			return mapper.writeValueAsString(job);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("problem in object serialization", e);
		}
	}

	@Override
	public Job mapRow(ResultSet rs) throws SQLException, JsonParseException,
			JsonMappingException, IOException {
		return mapper.readValue(rs.getString(column), Job.class);
	}
}

class SpinMachineMapper extends SingleColumnMapper<SpinnakerMachine> {
	public SpinMachineMapper(String columnLabel) {
		super(columnLabel);
	}

	@Override
	public SpinnakerMachine mapRow(ResultSet rs) throws SQLException {
		return SpinnakerMachine.parse(rs.getString(column));
	}
}

/**
 * How to convert a {@linkplain ResultSet result set} into a {@linkplain Map map}.
 * 
 * @author Donal Fellows
 * @param <T>
 *            The type of the values in the extracted map. The keys are always
 *            strings.
 */
abstract class MapExtractor<T> implements ResultSetExtractor<Map<String, T>> {
	private String keyColumn, valueColumn;

	/**
	 * Create an instance of this class.
	 * @param key The name of the column containing the (string) key.
	 * @param value The name of the column containing the value.
	 */
	MapExtractor(String key, String value) {
		keyColumn = key;
		valueColumn = value;
	}

	/**
	 * Build the map that will be used to contain the results. Defaults to a
	 * {@link LinkedHashMap} but subclasses may override, e.g., where the key
	 * ordering matters.
	 * 
	 * @return The new empty modifiable map.
	 */
	protected Map<String,T> createMap() {
		return new LinkedHashMap<>();
	}

	@Override
	public final Map<String, T> extractData(ResultSet rs) throws SQLException {
		Map<String, T> map = createMap();
		int provKey = rs.findColumn(keyColumn);
		int provValue = rs.findColumn(valueColumn);
		while (rs.next())
			map.put(rs.getString(provKey), extractColumn(rs, provValue));
		return map.isEmpty() ? null : map;
	}

	/**
	 * How to get the value out of a row in the result set. (Do not call
	 * {@link ResultSet#next()}.)
	 * 
	 * @param rs
	 *            The result set, positioned on the row that is being extracted.
	 * @param columnIndex
	 *            The column that corresponds to the nominated value column.
	 */
	protected abstract T extractColumn(ResultSet rs, int columnIndex) throws SQLException;
}
