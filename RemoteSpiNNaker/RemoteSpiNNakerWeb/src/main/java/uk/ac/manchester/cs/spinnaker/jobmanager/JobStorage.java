package uk.ac.manchester.cs.spinnaker.jobmanager;

import static uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage.DAO.Values.where;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface JobStorage {
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
	
	void initResourceUsage(int id, long plannedResourceUsage, long quotaNCores);

	void setResourceUsage(int id, double seconds);

	long getResourceUsage(Job job);

	void addProvenance(int id, String key, String value);

	Map<String, String> getProvenance(Job job);

	static class Queue implements JobStorage {
		private Map<String, Integer> map = new ConcurrentHashMap<>();
		private Set<Integer> running = new ConcurrentSkipListSet<>();
		private Map<Integer, Job> store = new ConcurrentHashMap<>();
		private Set<Integer> waiting = new ConcurrentSkipListSet<>();
		private Map<Integer,Long> resourceUsage = new ConcurrentHashMap<>();
		private Map<Integer,Long> nCores = new ConcurrentHashMap<>();
		private Map<Integer,Map<String,String>> provenance = new HashMap<>();

		@Override
		public void addJob(Job job, String executerId) {
			store.put(job.getId(), job);
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
		public void initResourceUsage(int id, long resourceUsage, long nCores) {
			this.resourceUsage.put(id, resourceUsage);
			this.nCores.put(id, nCores);
		}

		@Override
		public void setResourceUsage(int id, double seconds) {
			Long cores = nCores.get(id);
			if (cores != null)
				resourceUsage.put(id, (long) (seconds * cores));
		}

		@Override
		public long getResourceUsage(Job job) {
			Integer id = job.getId();
			nCores.remove(id);
			return resourceUsage.get(id);
		}

		@Override
		public void addProvenance(int id, String key, String value) {
			synchronized (provenance) {
				Map<String, String> map = provenance.get(id);
				if (map == null) {
					map = new HashMap<>();
					provenance.put(id, map);
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
				return new HashMap<>(map);
			}
		}
	}

	static class DAO implements JobStorage {
		private NamedParameterJdbcTemplate db;

		@Autowired
		public void setDataSource(DataSource dataSource) {
			this.db = new NamedParameterJdbcTemplate(dataSource);
		}

		private static final String ADD_JOB = "INSERT INTO job(id, json, state, executer) "
				+ "VALUES (:id, :json, 0, :executer)";
		private static final String GET_JOB_BY_ID = "SELECT json FROM job WHERE id = :id LIMIT 1";
		private static final String GET_JOB_BY_EXECUTER = "SELECT json FROM job WHERE executer = :executer LIMIT 1";
		private static final String GET_IN_STATE = "SELECT json FROM job WHERE state = :state";
		private static final String GET_STATE = "SELECT state FROM job WHERE id = :id LIMIT 1";
		private static final String SET_STATE = "UPDATE job SET state = :state WHERE id = :id";
		private static final String SET_EXEC = "UPDATE job SET executer = :executer WHERE id = :id";
		private static final String INIT_RU = "UPDATE job SET resourceUsage = :resources, numCores = :cores WHERE id = :id";
		private static final String SET_RU = "UPDATE job SET resourceUsage = CAST(numCores * :seconds AS INTEGER) WHERE id = :id";
		private static final String GET_RU = "SELECT resourceUsage FROM job WHERE id = :id LIMIT 1";
		private static final String ADD_PROV = "INSERT OR REPLACE INTO jobProvenance (id, provKey, provValue) "
				+ "VALUES (:id, :key, :value)";
		private static final String GET_PROV = "SELECT provKey, provValue FROM jobProvenance WHERE id = :id";

		@Override
		public void addJob(Job job, String executerId) {
			Objects.requireNonNull(job, "can only register real jobs");
			db.update(ADD_JOB,
					where("id", job.getId()).and("json", JobMapper.map(job))
							.and("executer", executerId));
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
			return db.queryForObject(GET_JOB_BY_ID, where("id", jobId),
					new JobMapper("json"));
		}

		private List<Job> getInState(int state) {
			return db.query(GET_IN_STATE, where("state", state), new JobMapper(
					"json"));
		}

		private Integer getState(Job job) {
			if (job == null)
				return null;
			return db.queryForObject(GET_STATE, where("id", job.getId()),
					Integer.class);
		}

		private void setState(Job job, int state) {
			if (job != null)
				db.update(SET_STATE,
						where("id", job.getId()).and("state", state));
		}

		@Override
		public List<Job> getWaiting() {
			return getInState(0);
		}

		@Override
		public boolean isRunning(Job job) {
			if (job == null)
				return false;
			return getState(job) == 1;
		}

		@Override
		public void markDone(Job job) {
			if (job != null)
				setState(job, 2);
		}

		@Override
		public void markRunning(Job job) {
			if (job != null)
				setState(job, 1);
		}

		@Override
		public void assignNewExecutor(Job job, JobExecuter executer) {
			if (job != null)
				db.update(
						SET_EXEC,
						where("id", job.getId()).and(
								"executer",
								executer != null ? executer.getExecuterId()
										: null));
		}

		@SuppressWarnings("serial")
		static class Values extends HashMap<String, Object> {
			static Values where(String key, Object value) {
				Values v = new Values();
				v.put(key, value);
				return v;
			}

			Values and(String key, Object value) {
				put(key, value);
				return this;
			}
		}

		@Override
		public void initResourceUsage(int id, long resourceUsage,
				long quotaNCores) {
			db.update(INIT_RU, where("id", id).and("resources", resourceUsage)
					.and("cores", quotaNCores));
		}

		@Override
		public void setResourceUsage(int id, double seconds) {
			db.update(SET_RU, where("id", id).and("seconds", seconds));
		}

		@Override
		public long getResourceUsage(Job job) {
			Long l = db.queryForObject(GET_RU, where("id", job.getId()),
					Long.class);
			return l == null ? 0 : l;
		}

		@Override
		public void addProvenance(int id, String key, String value) {
			db.update(ADD_PROV, where("id", id).and("key", key).and("value",value));
		}

		@Override
		public Map<String, String> getProvenance(Job job) {
			return db.query(GET_PROV, where("id", job.getId()),
					new ResultSetExtractor<Map<String, String>>() {
						@Override
						public Map<String, String> extractData(ResultSet rs)
								throws SQLException, DataAccessException {
							Map<String, String> map = new TreeMap<>();
							int provKey = rs.findColumn("provKey");
							int provValue = rs.findColumn("provValue");
							while (rs.next())
								map.put(rs.getString(provKey),
										rs.getString(provValue));
							return map.isEmpty() ? null : map;
						}
					});
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
