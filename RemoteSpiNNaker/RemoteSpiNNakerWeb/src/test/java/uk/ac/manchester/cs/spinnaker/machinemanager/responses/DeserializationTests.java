package uk.ac.manchester.cs.spinnaker.machinemanager.responses;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DeserializationTests {
	private ObjectMapper mapper = new ObjectMapper();

	@Test
	public void machine() throws JsonParseException, JsonMappingException, IOException {
		String machine = "{\"name\":\"foo\",\"tags\":[\"a\",\"b c\"],\"width\":123,\"height\":456}";
		Machine m = mapper.readValue(machine, Machine.class);
		assertNotNull(m);

		assertEquals("foo", m.getName());
		assertEquals(123,m.getWidth());
		assertEquals(456,m.getHeight());
		assertEquals("[a, b c]", m.getTags().toString());
	}

	@Test
	public void machineArray() throws JsonParseException, JsonMappingException, IOException {
		String machine = "[{\"name\":\"foo\",\"tags\":[\"a\",\"b c\"],\"width\":123,\"height\":456},"
				+ "{\"name\":\"bar\",\"width\":1,\"height\":2}]";
		Machine[] m = mapper.readValue(machine, Machine[].class);
		assertNotNull(m);

		assertEquals(2, m.length);
		assertEquals("foo", m[0].getName());
		assertEquals(123,m[0].getWidth());
		assertEquals(456,m[0].getHeight());
		assertEquals("[a, b c]", m[0].getTags().toString());

		assertEquals("bar", m[1].getName());
		assertEquals(1,m[1].getWidth());
		assertEquals(2,m[1].getHeight());
		assertNull(m[1].getTags());
	}

	@Test
	public void jobMachineInfo() throws JsonParseException, JsonMappingException, IOException {
		String machine = "{\"width\":123,\"height\":456,\"connections\":["
				+ "[[1,2],\"abcde\"],[[3,4],\"edcba\"]"
				+ "],\"machineName\":\"foo\"}";
		JobMachineInfo m = mapper.readValue(machine, JobMachineInfo.class);
		assertNotNull(m);

		assertEquals("foo", m.getMachineName());
		assertEquals(123,m.getWidth());
		assertEquals(456,m.getHeight());
		assertEquals(2, m.getConnections().size());

		assertEquals("abcde", m.getConnections().get(0).getHostname());
		assertEquals(1, m.getConnections().get(0).getChip().getX());
		assertEquals(2, m.getConnections().get(0).getChip().getY());

		assertEquals("edcba", m.getConnections().get(1).getHostname());
		assertEquals(3, m.getConnections().get(1).getChip().getX());
		assertEquals(4, m.getConnections().get(1).getChip().getY());
	}

	@Test
	public void jobState() throws JsonParseException, JsonMappingException, IOException {
		String machine = "{\"state\":2,\"power\":true,\"keepAlive\":1.25,\"reason\":\"foo\"}";
		JobState m = mapper.readValue(machine, JobState.class);
		assertNotNull(m);

		assertEquals("foo", m.getReason());
		assertEquals(2,m.getState());
		assertEquals(true,m.getPower());
		// Hack to work around deprecation of comparison of doubles
		assertEquals(1250000, (int)(m.getKeepAlive()*1000000));
	}
}
