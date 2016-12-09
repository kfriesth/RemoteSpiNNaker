package uk.ac.manchester.cs.spinnaker.jobmanager;

import static uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage.DAO.Values.where;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;

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

	static class Queue implements JobStorage {
		private Map<String, Integer> map = new ConcurrentHashMap<>();
		private Set<Integer> running = new ConcurrentSkipListSet<>();
		private Map<Integer, Job> store = new ConcurrentHashMap<>();
		private Set<Integer> waiting = new ConcurrentSkipListSet<>();

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

		@Override
		public void addJob(final Job job, final String executerId) {
			Objects.requireNonNull(job, "can only register real jobs");
			db.update(ADD_JOB,
					where("id", job.getId()).and("json", JobMapper.map(job))
							.and("executer", executerId));
		}

		@Override
		public Job getJob(final String executerId) {
			if (executerId == null)
				return null;
			return db.queryForObject(GET_JOB_BY_EXECUTER,
					where("executer", executerId), new JobMapper("json"));
		}

		@Override
		public Job getJob(final int jobId) {
			return db.queryForObject(GET_JOB_BY_ID, where("id", jobId),
					new JobMapper("json"));
		}

		private List<Job> getInState(final int state) {
			return db.query(GET_IN_STATE, where("state", state), new JobMapper(
					"json"));
		}

		private Integer getState(final Job job) {
			if (job == null)
				return null;
			return db.queryForObject(GET_STATE, where("id", job.getId()),
					Integer.class);
		}

		private void setState(final Job job, final int state) {
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
		public void assignNewExecutor(final Job job, final JobExecuter executer) {
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

	static int getColumnIndex(String label, ResultSet rs) throws SQLException {
		ResultSetMetaData md = rs.getMetaData();
		for (int i = 1; i <= md.getColumnCount(); i++)
			if (md.getColumnLabel(i).equals(label))
				return i;
		throw new SQLException("cannot look up column with label '" + label
				+ "'");
	}

	protected abstract T mapRow(ResultSet rs) throws Exception;

	@Override
	public final T mapRow(ResultSet rs, int row) throws SQLException {
		if (row == 0)
			column = getColumnIndex(label, rs);
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
