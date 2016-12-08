package uk.ac.manchester.cs.spinnaker.database;

import static java.util.Collections.singleton;
import static java.util.Collections.sort;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DatabaseTests {
	private static String jdbcDriver = "org.sqlite.JDBC";
	private static String jdbcUrl = "jdbc:sqlite:RemoteSpiNNaker.db";
	private static String jdbcUser = "";
	private static String jdbcPass = "";
	private static File root = new File("src/main/resources");// FIXME
	private File databaseInitScript = new File(root, "init.sql");
	private static String connectionInitScript = "PRAGMA foreign_keys = ON";

	@BeforeClass
	public static void loadConfig() throws IOException {
		try (InputStream in = new FileInputStream(new File(root,
				"database.properties"))) {
			Properties props = new Properties();
			props.load(in);
			jdbcDriver = props.getProperty("jdbc.driver", "jdbc:sqlite::memory:");
			jdbcUrl = props.getProperty("jdbc.url");
			jdbcUser = props.getProperty("jdbc.username");
			jdbcPass = props.getProperty("jdbc.password");
		}
	}

	@AfterClass
	public static void cleanup() {
		//FIXME
	}

	public BasicDataSource getDataSource() throws SQLException, IOException {
		BasicDataSource dataSource = new BasicDataSource();
		dataSource.setDriverClassName(jdbcDriver);
		dataSource.setUrl(jdbcUrl);

		if (isNotBlank(jdbcUser)&&isNotBlank(jdbcPass)) {
			dataSource.setUsername(jdbcUser);
			dataSource.setPassword(jdbcPass);
		}

		dataSource.setConnectionInitSqls(singleton(connectionInitScript));
		String sql = readFileToString(databaseInitScript,
				Charset.forName("UTF-8"));
		try (Connection conn = dataSource.getConnection();
				Statement s = conn.createStatement()) {
			s.execute(sql);
		}

		return dataSource;
	}

	@Test
	public void testDataSource() throws SQLException, IOException {
		assertTrue("can read from " + databaseInitScript.getAbsolutePath(),
				databaseInitScript.canRead());

		try (BasicDataSource dataSource = getDataSource();
				Connection conn = dataSource.getConnection();
				Statement s = conn.createStatement()) {
			ResultSet rs = s.executeQuery("SELECT * FROM job");
			ResultSetMetaData md = rs.getMetaData();
			assertEquals("id", md.getColumnLabel(1).toLowerCase());
			assertEquals(3, md.getColumnCount());
			List<String> names = new ArrayList<>();
			for (int i = 1; i <= md.getColumnCount(); i++)
				names.add(md.getColumnLabel(i).toLowerCase());
			sort(names);
			Assert.assertEquals("[id, json, state]", names.toString());
		}
	}

}
