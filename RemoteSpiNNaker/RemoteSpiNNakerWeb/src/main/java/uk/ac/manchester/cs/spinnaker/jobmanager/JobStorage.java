package uk.ac.manchester.cs.spinnaker.jobmanager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;

public interface JobStorage {
	void addJob(Job job, String executerId);

	Job getJob(String executerId);

	List<Job> getWaiting();

	boolean isRunning(Job job);

	void markDone(Job job);

	void markRunning(Job job);

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
		public List<Job> getWaiting() {
			List<Job> result = new ArrayList<>();
			for (Integer id : waiting)
				result.add(store.get(id));
			return result;
		}

		@Override
		public boolean isRunning(Job job) {
			return running.contains(job.getId());
		}

		@Override
		public void markDone(Job job) {
			waiting.remove(job.getId());
			running.remove(job.getId());
		}

		@Override
		public void markRunning(Job job) {
			waiting.remove(job.getId());
			running.add(job.getId());
		}
	}

	@SuppressWarnings("serial")
	static class DAO implements JobStorage {
		private NamedParameterJdbcTemplate db;

		@Autowired
		public void setDataSource(DataSource dataSource) {
			this.db = new NamedParameterJdbcTemplate(dataSource);
		}

		private String serialize(Object obj) {
			return "FIXME";//FIXME
		}
		private Object deserialize(String s) {
			return null;// FIXME
		}

		private RowMapper<Job> JOB_CONVERTER = new RowMapper<Job>() {
			@Override
			public Job mapRow(ResultSet rs, int row) throws SQLException,
					DataAccessException {
				return (Job) deserialize(rs.getString(1));
			}
		};

		private RowMapper<Integer> STATE_CONVERTER = new RowMapper<Integer>() {
			@Override
			public Integer mapRow(ResultSet rs, int row) throws SQLException,
					DataAccessException {
				return rs.getInt(1);
			}
		};

		static final String ADD_JOB = "INSERT INTO job(id,json) VALUES (:id,:json)";
		static final String GET_JOB = "SELECT json FROM job WHERE id = :id LIMIT 1";
		static final String GET_IN_STATE = "SELECT json FROM job WHERE state = :state";
		static final String GET_STATE = "SELECT state FROM job WHERE id = :id LIMIT 1";
		static final String SET_STATE = "UPDATE job SET state = :state WHERE id = :id";

		@Override
		public void addJob(final Job job, String executerId) {
			db.update(ADD_JOB, new HashMap<String, Object>() {{
				put("id", job.getId());
				put("json", serialize(job));
			}});
		}

		@Override
		public Job getJob(final String executerId) {
			return db.queryForObject(GET_JOB, new HashMap<String, Object>() {{
				put("id", executerId);
			}}, JOB_CONVERTER);
		}

		private List<Job> getInState(final int state) {
			return db.query(GET_IN_STATE, new HashMap<String, Object>() {{
				put("state", state);
			}}, JOB_CONVERTER);
		}
		
		private Integer getState(final Job job) {
			return db.queryForObject(GET_STATE, new HashMap<String, Object>() {{
				put("id", job.getId());
			}}, STATE_CONVERTER);
		}

		private void setState(final Job job, final int state) {
			db.update(SET_STATE, new HashMap<String, Object>() {{
				put("state", state);
				put("id", job.getId());
			}});
		}

		@Override
		public List<Job> getWaiting() {
			return getInState(0);
		}

		@Override
		public boolean isRunning(Job job) {
			return getState(job) == 1;
		}

		@Override
		public void markDone(Job job) {
			setState(job, 2);
		}

		@Override
		public void markRunning(Job job) {
			setState(job, 1);
		}

	}
}
