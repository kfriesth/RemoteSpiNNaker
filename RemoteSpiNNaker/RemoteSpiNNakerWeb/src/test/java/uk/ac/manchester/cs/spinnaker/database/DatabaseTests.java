package uk.ac.manchester.cs.spinnaker.database;

import static java.util.Collections.singleton;
import static java.util.Collections.sort;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.manchester.cs.spinnaker.job.nmpi.Job;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage;

public class DatabaseTests {
	private static String jdbcDriver = "org.sqlite.JDBC";
	private static String jdbcUrl = "jdbc:sqlite:RemoteSpiNNaker.db";
	private static String jdbcUser = "";
	private static String jdbcPass = "";
	private static File root = new File("src/main/resources");// FIXME
	private File databaseInitScript = new File(root, "init.sql");
	private static String connectionInitScript = "PRAGMA foreign_keys = ON";
	private static File dbfile;

	@BeforeClass
	public static void loadConfig() throws IOException {
		try (InputStream in = new FileInputStream(new File(root,
				"database.properties"))) {
			Properties props = new Properties();
			props.load(in);
			jdbcDriver = props.getProperty("jdbc.driver", jdbcDriver);
			jdbcUrl = props.getProperty("jdbc.url", jdbcUrl);
			jdbcUser = props.getProperty("jdbc.username", jdbcUser);
			jdbcPass = props.getProperty("jdbc.password", jdbcPass);
			connectionInitScript=props.getProperty("jdbc.connectionInit", connectionInitScript);
		}
		if (jdbcUrl.startsWith("jdbc:sqlite:")
				&& !jdbcUrl.equals("jdbc:sqlite::memory:")
				&& !jdbcUrl.equals("jdbc:sqlite:"))
			dbfile = new File(jdbcUrl.substring("jdbc:sqlite:".length()));
	}

	@AfterClass
	public static void cleanup() {
		if (dbfile != null && dbfile.exists())
			dbfile.delete();
	}

	BasicDataSource dataSource;

	@Before
	public void initDataSource() throws SQLException, IOException {
		BasicDataSource dataSource = new BasicDataSource();
		dataSource.setDriverClassName(jdbcDriver);
		dataSource.setUrl(jdbcUrl);

		if (isNotBlank(jdbcUser)&&isNotBlank(jdbcPass)) {
			dataSource.setUsername(jdbcUser);
			dataSource.setPassword(jdbcPass);
		}

		assumeTrue(isNotBlank(connectionInitScript));
		dataSource.setConnectionInitSqls(singleton(connectionInitScript));

		assumeTrue("can read from " + databaseInitScript.getAbsolutePath(),
				databaseInitScript.canRead());
		String sql = readFileToString(databaseInitScript,
				Charset.forName("UTF-8"));
		try (Connection conn = dataSource.getConnection();
				Statement s = conn.createStatement()) {
			s.execute(sql);
		}

		this.dataSource = dataSource;
	}

	@After
	public void closeDataSource() throws SQLException {
		dataSource.close();
	}

	@Test
	public void testDatabaseConfig() throws SQLException, IOException {
		try (Connection conn = dataSource.getConnection();
				Statement s = conn.createStatement()) {
			ResultSet rs = s.executeQuery("SELECT * FROM job");
			ResultSetMetaData md = rs.getMetaData();
			assertEquals("id", md.getColumnLabel(1).toLowerCase());
			assertEquals(4, md.getColumnCount());
			List<String> names = new ArrayList<>();
			for (int i = 1; i <= md.getColumnCount(); i++)
				names.add(md.getColumnLabel(i).toLowerCase());
			sort(names);
			Assert.assertEquals("[executer, id, json, state]", names.toString());
		}
	}

	@Test
	public  void testJobStorage() {
		JobStorage.DAO storage = new JobStorage.DAO();
		storage.setDataSource(dataSource);

		Job in = new Job();
		in.setId(12345);
		in.setCode("do stuff with python");
		in.setUserId("root");

		storage.addJob(in, "abc-def");

		Job out = storage.getJob("abc-def");
		assertNotNull("must get the job back", out);
		assertEquals(new Integer(12345), out.getId());
		assertEquals("root", out.getUserId());
		assertEquals("do stuff with python", out.getCode());

		assertEquals(1, storage.getWaiting().size());
		assertFalse("not yet running", storage.isRunning(in));
		storage.markRunning(in);
		assertTrue("now running", storage.isRunning(in));
		assertEquals(0, storage.getWaiting().size());
		storage.markDone(in);
		assertFalse("no longer running", storage.isRunning(in));
		assertEquals(0, storage.getWaiting().size());
	}
}
