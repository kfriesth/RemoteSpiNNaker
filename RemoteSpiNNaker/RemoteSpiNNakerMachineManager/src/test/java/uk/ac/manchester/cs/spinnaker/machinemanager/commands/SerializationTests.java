package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

import static org.junit.Assert.*;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("rawtypes")
public class SerializationTests {
	ObjectMapper mapper = new ObjectMapper();

	@Test
	public void testCreateJob() throws JsonProcessingException {
		Command cmd = new CreateJobCommand(123, "abc def");
		assertEquals(
				"{\"command\":\"create_job\",\"args\":[123],\"kwargs\":{\"owner\":\"abc def\"}}",
				mapper.writeValueAsString(cmd));
	}

	@Test
	public void testDestroyJob() throws JsonProcessingException {
		Command cmd = new DestroyJobCommand(123);
		assertEquals(
				"{\"command\":\"destroy_job\",\"args\":[123],\"kwargs\":{}}",
				mapper.writeValueAsString(cmd));
	}

	@Test
	public void testGetJobMachineInfo() throws JsonProcessingException {
		Command cmd = new GetJobMachineInfoCommand(123);
		assertEquals(
				"{\"command\":\"get_job_machine_info\",\"args\":[123],\"kwargs\":{}}",
				mapper.writeValueAsString(cmd));
	}

	@Test
	public void testGetJobState() throws JsonProcessingException {
		Command cmd = new GetJobStateCommand(123);
		assertEquals(
				"{\"command\":\"get_job_state\",\"args\":[123],\"kwargs\":{}}",
				mapper.writeValueAsString(cmd));
	}

	@Test
	public void testJobKeepAlive() throws JsonProcessingException {
		Command cmd = new JobKeepAliveCommand(123);
		assertEquals(
				"{\"command\":\"job_keepalive\",\"args\":[123],\"kwargs\":{}}",
				mapper.writeValueAsString(cmd));
	}

	@Test
	public void testListMachines() throws JsonProcessingException {
		Command cmd = new ListMachinesCommand();
		assertEquals(
				"{\"command\":\"list_machines\",\"args\":[],\"kwargs\":{}}",
				mapper.writeValueAsString(cmd));
	}

	@Test
	public void testNoNotifyJob() throws JsonProcessingException {
		Command cmd = new NoNotifyJobCommand(123);
		assertEquals(
				"{\"command\":\"no_notify_job\",\"args\":[123],\"kwargs\":{}}",
				mapper.writeValueAsString(cmd));
	}

	@Test
	public void testNotifyJob() throws JsonProcessingException {
		Command cmd = new NotifyJobCommand(123);
		assertEquals(
				"{\"command\":\"notify_job\",\"args\":[123],\"kwargs\":{}}",
				mapper.writeValueAsString(cmd));
	}
}
