package uk.ac.manchester.cs.spinnaker.remote.web;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;
import javax.ws.rs.Path;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.spring.JaxRsConfig;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.springframework.security.authentication.ClientAuthenticationProvider;
import org.pac4j.springframework.security.web.ClientAuthenticationEntryPoint;
import org.pac4j.springframework.security.web.ClientAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.nimbusds.oauth2.sdk.ParseException;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobStorage;
import uk.ac.manchester.cs.spinnaker.jobmanager.LocalJobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.XenVMExecuterFactory;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;
import uk.ac.manchester.cs.spinnaker.machinemanager.FixedMachineManagerImpl;
import uk.ac.manchester.cs.spinnaker.machinemanager.MachineManager;
import uk.ac.manchester.cs.spinnaker.machinemanager.SpallocMachineManagerImpl;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueManager;
import uk.ac.manchester.cs.spinnaker.output.OutputManagerImpl;
import uk.ac.manchester.cs.spinnaker.rest.OutputManager;
import uk.ac.manchester.cs.spinnaker.rest.utils.NullExceptionMapper;


@Configuration
@Import({JaxRsConfig.class, RemoteSpinnakerBeans.Database.class, RemoteSpinnakerBeans.TrivialStore.class, RemoteSpinnakerBeans.HbpSecurityServices.class})
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

	@Autowired
	private ApplicationContext ctx;

    @Value("${spalloc.enabled}")
    private boolean useSpalloc;

    @Value("${xen.server.enabled}")
    private boolean useXenVms;

    @Value("${baseserver.url}${cxf.path}${cxf.rest.path}/")
    private URL baseServerUrl;

    @Value("${cxf.rest.path}")
    private String restPath;

    @Configuration
    @Profile("security")
    @EnableGlobalMethodSecurity(prePostEnabled=true, proxyTargetClass=true)
    @EnableWebSecurity
    public static class HbpSecurityServices extends WebSecurityConfigurerAdapter {
    	@Value("${baseserver.url}${callback.path}")
    	private String oidcRedirectUri;
		@Value("${cxf.path}${cxf.rest.path}")
		String restServicePath;
		@Value("${callback.path}")
		private String callbackPath;

    	
		@Autowired
		public void configureGlobal(AuthenticationManagerBuilder auth)
				throws Exception {
			auth.authenticationProvider(clientProvider());
		}

		@Bean
		public CollabSecurityService collabSecurityService()
				throws MalformedURLException {
			return new CollabSecurityService();
		}

		@Bean
		public Client<?,?> hbpAuthenticationClient() {
			return new BasicOidcClient();
		}

		@Bean
		public Client<?,?> hbpBearerClient() throws ParseException,
				MalformedURLException, IOException {
			return new BearerOidcClient();
		}

		@Bean
		public Clients clients() throws ParseException, MalformedURLException,
				IOException {
			return new Clients(oidcRedirectUri, hbpAuthenticationClient(),
					hbpBearerClient());
		}

		@Bean
		public ClientAuthenticationProvider clientProvider()
				throws ParseException, MalformedURLException, IOException {
			ClientAuthenticationProvider provider = new ClientAuthenticationProvider();
			provider.setClients(clients());
			return provider;
		}

		@Bean
		public ClientAuthenticationFilter callbackFilter() throws Exception {
			ClientAuthenticationFilter filter = new ClientAuthenticationFilter(
					callbackPath);
			filter.setClients(clients());
			filter.setAuthenticationManager(authenticationManagerBean());
			return filter;
		}

		@Override
		public void configure(WebSecurity web) throws Exception {
			Path path = findAnnotation(JobManager.class, Path.class);
			web.ignoring().antMatchers(restServicePath + path.value() + "/**");
		}

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			Path path = AnnotationUtils.findAnnotation(OutputManager.class,
					Path.class);
			http.addFilterBefore(directAuthFilter(),
					UsernamePasswordAuthenticationFilter.class)
					.addFilterBefore(callbackFilter(),
							UsernamePasswordAuthenticationFilter.class)
					.exceptionHandling()
					.authenticationEntryPoint(hbpAuthenticationEntryPoint())
					.and().authorizeRequests()
					.antMatchers(restServicePath + path.value() + "/**")
					.authenticated().anyRequest().permitAll();
		}

		@Bean
		public OncePerRequestFilter directAuthFilter() throws Exception {
			DirectClientAuthenticationFilter filter = new DirectClientAuthenticationFilter(
					authenticationManagerBean());
			filter.setClient(hbpBearerClient());
			return filter;
		}

		@Bean
		public ClientAuthenticationEntryPoint hbpAuthenticationEntryPoint() {
			ClientAuthenticationEntryPoint entryPoint = new ClientAuthenticationEntryPoint();
			entryPoint.setClient(hbpAuthenticationClient());
			return entryPoint;
		}
	}

	@Bean
	public MachineManager machineManager() {
		if (useSpalloc)
			return new SpallocMachineManagerImpl();
		return new FixedMachineManagerImpl();
	}

	@Bean
	public NMPIQueueManager queueManager() throws NoSuchAlgorithmException,
			KeyManagementException {
		return new NMPIQueueManager();
	}

	@Bean
	public JobExecuterFactory jobExecuterFactory() throws IOException {
		if (!useXenVms)
			return new LocalJobExecuterFactory();
		return new XenVMExecuterFactory();
	}

	@Bean
	public OutputManager outputManager() {
		// Pass this, as it is non-trivial constructed value
		return new OutputManagerImpl(baseServerUrl);
	}

	@Bean
	public JobManager jobManager() {
		// Pass this, as it is non-trivial constructed value
		return new JobManager(baseServerUrl);
	}

	@Bean
	public Server jaxRsServer() throws KeyManagementException,
			NoSuchAlgorithmException, IOException {
		JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
		factory.setAddress(restPath);
		factory.setBus(ctx.getBean(SpringBus.class));
		factory.setServiceBeans(asList(outputManager(), jobManager()));
		factory.setProviders(asList(new JacksonJsonProvider(),
				new NullExceptionMapper()));
		return factory.create();
	}

	/**
	 * Proxy to use instead of database access layer
	 * 
	 * @author Donal Fellows
	 * @see Database
	 */
	@Configuration
	@Profile("!db")
	public static class TrivialStore {
		@Bean
		JobStorage jobStorage() {
			return new JobStorage.Queue();
		}
	}

	/**
	 * Database Access Layer
	 * @author Donal Fellows
	 */
	@Configuration
	@Profile("db")
	@PropertySource("classpath:/database.properties")
	@EnableTransactionManagement
	public static class Database {
		@Value("${jdbc.driver:org.sqlite.JDBC}")
		private String jdbcDriver;
		@Value("${jdbc.url:jdbc:sqlite:${sqlite.dbfile:RemoteSpiNNaker.db}}")
		private String jdbcUrl;
		@Value("${jdbc.username:}")
		private String jdbcUser;
		@Value("${jdbc.password:}")
		private String jdbcPass;
		@Value("${jdbc.initScript:init.sql}")
		private File databaseInitScript;
		@Value("${jdbc.connectionInit:PRAGMA foreign_keys = ON}")
		private String connectionInit;

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

			if (databaseInitScript.canRead())
				try (Connection conn = dataSource.getConnection();
						Statement s = conn.createStatement()) {
					s.execute(readFileToString(databaseInitScript,
							Charset.forName("UTF-8")));
				}

			return dataSource;
		}

		@Bean
		public PlatformTransactionManager txManager() throws SQLException,
				IOException {
			return new DataSourceTransactionManager(dataSource());
		}

		@Bean
		JobStorage jobStorage() {
			return new JobStorage.DAO();
		}
	}
}
