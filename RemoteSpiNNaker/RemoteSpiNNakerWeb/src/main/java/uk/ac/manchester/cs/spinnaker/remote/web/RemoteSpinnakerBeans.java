package uk.ac.manchester.cs.spinnaker.remote.web;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.spring.JaxRsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.converter.Converter;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage;
import uk.ac.manchester.cs.spinnaker.jobmanager.LocalJobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.XenVMExecuterFactory;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;
import uk.ac.manchester.cs.spinnaker.machinemanager.FixedMachineManagerImpl;
import uk.ac.manchester.cs.spinnaker.machinemanager.SpallocMachineManagerImpl;
import uk.ac.manchester.cs.spinnaker.rest.OutputManager;
import uk.ac.manchester.cs.spinnaker.rest.utils.NullExceptionMapper;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

@Configuration("RemoteSpiNNaker-beans")
@Import({ RemoteSpinnakerBeans.Database.class,
		RemoteSpinnakerBeans.TrivialStore.class, HBPSecurityBeans.class,
		RemoteSpinnakerBeans.CXFFactory.class })
@ComponentScan("uk.ac.manchester.cs.spinnaker")
public class RemoteSpinnakerBeans {
	@Bean
	public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	@Bean
	public static ConversionServiceFactoryBean conversionService() {
		ConversionServiceFactoryBean factory = new ConversionServiceFactoryBean();
		factory.setConverters(singleton(new Converter<String, SpinnakerMachine>() {
			@Override
			public SpinnakerMachine convert(String value) {
				return SpinnakerMachine.parse(value);
			}
		}));
		return factory;
	}

	@Bean
	@Profile("spalloc")
	public SpallocMachineManagerImpl spallocMachineManager() {
		return new SpallocMachineManagerImpl();
	}

	@Bean
	@Profile("!spalloc")
	public FixedMachineManagerImpl fixedMachineManager() {
		return new FixedMachineManagerImpl();
	}

	@Bean
	@Profile("xen")
	public XenVMExecuterFactory localExecuterFactory() throws IOException {
		return new XenVMExecuterFactory();
	}

	@Bean
	@Profile("!xen")
	public LocalJobExecuterFactory xenExecuterFactory() throws IOException {
		return new LocalJobExecuterFactory();
	}

	@Bean
	JacksonJsonProvider jsonProvider() {
		return new JacksonJsonProvider();
	}

	@Configuration("webapp-core")
	@Import(JaxRsConfig.class)
	public static class CXFFactory {
		@Value("${cxf.rest.path}")
		private String restPath;
		@Autowired
		Bus bus;
		@Autowired
		OutputManager outputManager;
		@Autowired
		JobManager jobManager;
		@Autowired
		NullExceptionMapper npeMapper;
		@Autowired
		JacksonJsonProvider jsonProvider;

		@Bean
		public Server jaxRsServer() throws KeyManagementException,
				NoSuchAlgorithmException, IOException {
			JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
			factory.setAddress(restPath);
			factory.setBus(bus);
			factory.setServiceBeanObjects(outputManager, jobManager);
			factory.setProviders(asList(jsonProvider, npeMapper));
			return factory.create();
		}
	}

	/**
	 * Proxy to use instead of database access layer
	 * 
	 * @author Donal Fellows
	 * @see Database
	 */
	@Configuration("data-layer-memorystore")
	@Profile("!db")
	public static class TrivialStore {
		@Bean
		JobStorage.Queue jobStorage() {
			return new JobStorage.Queue();
		}
	}

	@Configuration("database-connection")
	@Profile("db")
	@PropertySource("classpath:/database.properties")
	public static class DatabaseConnection {
		@Value("${jdbc.driver:org.sqlite.JDBC}")
		private String jdbcDriver;
		@Value("${jdbc.url:jdbc:sqlite:${sqlite.dbfile:RemoteSpiNNaker.db}}")
		private String jdbcUrl;
		@Value("${jdbc.username:}")
		private String jdbcUser;
		@Value("${jdbc.password:}")
		private String jdbcPass;
		private String databaseInitScript = "";
		@Value("${jdbc.connectionInit:PRAGMA foreign_keys = ON}")
		private String connectionInit;

		@Value("${jdbc.initScript:init.sql}")
		void setDatabaseInitScript(File file) throws IOException {
			if (file != null && file.canRead())
				databaseInitScript = readFileToString(file,
						Charset.forName("UTF-8")).trim();
		}

		@Bean
		public DataSource dataSource() throws SQLException, IOException {
			BasicDataSource dataSource = new BasicDataSource();

			dataSource.setDriverClassName(jdbcDriver);
			dataSource.setUrl(jdbcUrl);
			if (isNotBlank(jdbcUser) && isNotBlank(jdbcPass)) {
				dataSource.setUsername(jdbcUser);
				dataSource.setPassword(jdbcPass);
			}

			if (isNotBlank(connectionInit))
				dataSource.setConnectionInitSqls(singleton(connectionInit));

			try (Connection conn = dataSource.getConnection();
					Statement s = conn.createStatement()) {
				if (!databaseInitScript.isEmpty())
					s.execute(databaseInitScript);
			}

			return dataSource;
		}
	}

	/**
	 * Database Access Layer
	 * 
	 * @author Donal Fellows
	 */
	@Configuration("data-layer-database")
	@Profile("db")
	@EnableTransactionManagement
	@Import(DatabaseConnection.class)
	public static class Database {
		@Autowired
		DataSource dataSource;

		@Bean
		public PlatformTransactionManager txManager() throws SQLException,
				IOException {
			return new DataSourceTransactionManager(dataSource);
		}

		@Bean
		JobStorage.DAO jobStorage() {
			return new JobStorage.DAO();
		}
	}
}
