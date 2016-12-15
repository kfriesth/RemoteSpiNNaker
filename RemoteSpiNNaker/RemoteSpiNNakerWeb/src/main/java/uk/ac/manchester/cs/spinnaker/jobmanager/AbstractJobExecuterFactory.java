package uk.ac.manchester.cs.spinnaker.jobmanager;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

abstract class AbstractJobExecuterFactory implements JobExecuterFactory {
	Map<String, JobExecuter> map = new ConcurrentHashMap<>();

	protected abstract JobExecuter makeExecuter(URL url) throws IOException;

	@Override
	public final JobExecuter createJobExecuter(URL baseUrl) throws IOException {
		requireNonNull(baseUrl);

		JobExecuter e = makeExecuter(baseUrl);
		map.put(e.getExecuterId(), e);
		return e;
	}

	@Override
	public JobExecuter getJobExecuter(String id) {
		return map.get(id);
	}

	protected void executorFinished(JobExecuter executor) {
		map.remove(executor.getExecuterId());
	}
}
