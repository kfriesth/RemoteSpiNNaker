package uk.ac.manchester.cs.spinnaker.remote.web;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Path;

import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.spring.JaxRsConfig;
import org.pac4j.core.client.Clients;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.springframework.security.authentication.ClientAuthenticationProvider;
import org.pac4j.springframework.security.web.ClientAuthenticationEntryPoint;
import org.pac4j.springframework.security.web.ClientAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.oauth2.sdk.ParseException;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;
import uk.ac.manchester.cs.spinnaker.jobmanager.impl.LocalJobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.impl.XenVMExecuterFactory;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;
import uk.ac.manchester.cs.spinnaker.machinemanager.FixedMachineManagerImpl;
import uk.ac.manchester.cs.spinnaker.machinemanager.MachineManager;
import uk.ac.manchester.cs.spinnaker.machinemanager.SpallocMachineManagerImpl;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueManager;
import uk.ac.manchester.cs.spinnaker.output.OutputManager;
import uk.ac.manchester.cs.spinnaker.output.impl.OutputManagerImpl;

@SuppressWarnings("unused")
@Configuration
//@EnableGlobalMethodSecurity(prePostEnabled=true, proxyTargetClass=true)
//@EnableWebSecurity
@Import(JaxRsConfig.class)
public class RemoteSpinnakerBeans {
	@Bean
	public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	@Bean
	public static ConversionServiceFactoryBean conversionService() {
		ConversionServiceFactoryBean factory = new ConversionServiceFactoryBean();
		Set<Converter<?, ?>> converters = new HashSet<>();
		converters.add(new StringToSpinnakerMachine());
		factory.setConverters(converters);
		return factory;
	}

	@Autowired
	private ApplicationContext ctx;

    @Value("${spalloc.enabled}")
    private boolean useSpalloc;

    @Value("${machines}")
    private List<SpinnakerMachine> machines;

    @Value("${spalloc.server}")
    private String spallocServer;

    @Value("${spalloc.port}")
    private int spallocPort;

    @Value("${spalloc.user.name}")
    private String spallocUser;

    @Value("${nmpi.url}")
    private URL nmpiUrl;

    @Value("${nmpi.username}")
    private String nmpiUsername;

    @Value("${nmpi.password}")
    private String nmpiPassword;

    @Value("${nmpi.passwordIsApiKey}")
    private boolean nmpiPasswordIsApiKey;

    @Value("${nmpi.hardware}")
    private String nmpiHardware;

    @Value("${results.directory}")
    private File resultsDirectory;

    @Value("${baseserver.url}${cxf.path}${cxf.rest.path}/")
    private URL baseServerUrl;

    @Value("${cxf.rest.path}")
    private String restPath;

    @Value("${cxf.path}${cxf.rest.path}")
    private String restServicePath;

    @Value("${deleteJobsOnExit}")
    private boolean deleteJobsOnExit;

    @Value("${liveUploadOutput}")
    private boolean liveUploadOutput;

    @Value("${requestSpiNNakerMachine}")
    private boolean requestSpiNNakerMachine;

    @Value("${oidc.clientId}")
    private String oidcClientId;

    @Value("${oidc.secret}")
    private String oidcSecret;

    @Value("${oidc.discovery.uri}")
    private String oidcDiscoveryUri;

    @Value("${baseserver.url}${callback.path}")
    private String oidcRedirectUri;

    @Value("${collab.service.uri}")
    private String collabServiceUri;

    @Value("${results.purge.days}")
    private long nDaysToKeepResults;

    @Value("${xen.server.enabled}")
    private boolean useXenVms;

    @Value("${xen.server.url}")
    private URL xenServerUrl;

    @Value("${xen.server.username}")
    private String xenUsername;

    @Value("${xen.server.password}")
    private String xenPassword;

    @Value("${xen.server.templateVm}")
    private String xenTemplateVmName;

    @Value("${xen.server.diskspaceInGbs}")
    private long xenDiskSizeInGbs;

    @Value("${xen.server.shutdownOnExit}")
    private boolean xenShutdownOnExit;

    @Value("${xen.server.maxVms}")
    private int xenMaxVms;

    @Value("${restartJobExecutorOnFailure}")
    private boolean restartJobExecutorOnFailure;

//    @Autowired
//    public void configureGlobal(AuthenticationManagerBuilder auth)
//            throws Exception {
//        auth.authenticationProvider(clientProvider());
//    }
//
//    @Bean
//    public CollabSecurityService collabSecurityService()
//            throws MalformedURLException {
//        return new CollabSecurityService(new URL(collabServiceUri));
//    }
//
//    @Bean
//    public OidcClient hbpAuthenticationClient() {
//        OidcClient oidcClient = new OidcClient();
//        oidcClient.setClientID(oidcClientId);
//        oidcClient.setSecret(oidcSecret);
//        oidcClient.setDiscoveryURI(oidcDiscoveryUri);
//        oidcClient.setScope("openid profile hbp.collab");
//        oidcClient.setPreferredJwsAlgorithm(JWSAlgorithm.RS256);
//        return oidcClient;
//    }
//
//    @Bean
//    public BearerOidcClient hbpBearerClient()
//            throws ParseException, MalformedURLException, IOException {
//        BearerOidcClient client = new BearerOidcClient(oidcDiscoveryUri, "");
//        return client;
//    }
//
//    @Bean
//    public Clients clients()
//            throws ParseException, MalformedURLException, IOException {
//        return new Clients(
//            oidcRedirectUri, hbpAuthenticationClient(), hbpBearerClient());
//    }
//
//    @Bean
//    public ClientAuthenticationProvider clientProvider()
//            throws ParseException, MalformedURLException, IOException {
//        ClientAuthenticationProvider provider =
//            new ClientAuthenticationProvider();
//        provider.setClients(clients());
//        return provider;
//    }

//    @Configuration
//    @Order(100)
//    public static class HbpAuthentication extends WebSecurityConfigurerAdapter {
//
//        @Value("${cxf.path}${cxf.rest.path}")
//        String restServicePath;
//
//        @Value("${callback.path}")
//        private String callbackPath;
//
//        @Autowired
//        OidcClient hbpAuthenticationClient;
//
//        @Autowired
//        BearerOidcClient hbpBearerClient;
//
//        @Autowired
//        Clients clients;
//
//        @Override
//        public void configure(WebSecurity web) throws Exception {
//            Path path = AnnotationUtils.findAnnotation(
//                JobManager.class, Path.class);
//            web.ignoring().antMatchers(restServicePath + path.value() + "/**");
//        }
//
//        @Override
//        protected void configure(HttpSecurity http) throws Exception {
//            Path path = AnnotationUtils.findAnnotation(
//                OutputManager.class, Path.class);
//            http.addFilterBefore(
//                    directAuthFilter(),
//                    UsernamePasswordAuthenticationFilter.class)
//                .addFilterBefore(
//                    callbackFilter(),
//                    UsernamePasswordAuthenticationFilter.class)
//                .exceptionHandling().authenticationEntryPoint(
//                        hbpAuthenticationEntryPoint()).and()
//                .authorizeRequests().antMatchers(
//                    restServicePath + path.value() + "/**").authenticated()
//                                   .anyRequest().permitAll();
//        }
//
//        @Bean
//        public ClientAuthenticationFilter callbackFilter() throws Exception {
//            ClientAuthenticationFilter filter =
//                new ClientAuthenticationFilter(callbackPath);
//            filter.setClients(clients);
//            filter.setAuthenticationManager(authenticationManagerBean());
//            return filter;
//        }
//
//        @Bean
//        public DirectClientAuthenticationFilter directAuthFilter()
//                throws Exception {
//            DirectClientAuthenticationFilter filter =
//                new DirectClientAuthenticationFilter(
//                    authenticationManagerBean());
//            filter.setClient(hbpBearerClient);
//            return filter;
//        }
//
//        @Bean
//        public ClientAuthenticationEntryPoint hbpAuthenticationEntryPoint() {
//            ClientAuthenticationEntryPoint entryPoint =
//                new ClientAuthenticationEntryPoint();
//            entryPoint.setClient(hbpAuthenticationClient);
//            return entryPoint;
//        }
//    }

	@Bean
	public MachineManager machineManager() {
		if (useSpalloc) {
			SpallocMachineManagerImpl spalloc = new SpallocMachineManagerImpl(
					spallocServer, spallocPort, spallocUser);
			spalloc.start();
			return spalloc;
		}
		return new FixedMachineManagerImpl(machines);
	}

	@Bean
	public NMPIQueueManager queueManager() throws NoSuchAlgorithmException,
			KeyManagementException {
		return new NMPIQueueManager(nmpiUrl, nmpiHardware, nmpiUsername,
				nmpiPassword, nmpiPasswordIsApiKey);
	}

	@Bean
	public JobExecuterFactory jobExecuterFactory() throws IOException {
		if (!useXenVms)
			return new LocalJobExecuterFactory(deleteJobsOnExit,
					liveUploadOutput, requestSpiNNakerMachine);

		return new XenVMExecuterFactory(xenServerUrl, xenUsername, xenPassword,
				xenTemplateVmName, xenDiskSizeInGbs, deleteJobsOnExit,
				xenShutdownOnExit, liveUploadOutput, requestSpiNNakerMachine,
				xenMaxVms);
	}

	@Bean
	public OutputManager outputManager() {
		return new OutputManagerImpl(baseServerUrl, resultsDirectory,
				nDaysToKeepResults);
	}

	@Bean
	public JobManager jobManager() throws IOException,
			NoSuchAlgorithmException, KeyManagementException {
		return new JobManager(machineManager(), queueManager(),
				outputManager(), baseServerUrl, jobExecuterFactory(),
				restartJobExecutorOnFailure);
	}

	@Bean
	public Server jaxRsServer() throws KeyManagementException,
			NoSuchAlgorithmException, IOException {
		JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
		factory.setAddress(restPath);
		factory.setBus(ctx.getBean(SpringBus.class));
		factory.setServiceBeans(Arrays.asList(outputManager(), jobManager()));
		factory.setProviders(Arrays.asList(new JacksonJsonProvider()));
		return factory.create();
	}
}
