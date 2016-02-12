package uk.ac.manchester.cs.spinnaker.remote.web;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.converter.Converter;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;
import uk.ac.manchester.cs.spinnaker.jobmanager.impl.LocalJobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;
import uk.ac.manchester.cs.spinnaker.machinemanager.MachineManager;
import uk.ac.manchester.cs.spinnaker.nmpi.NMPIQueueManager;

@Configuration
@PropertySource("file:${remotespinnaker.properties.location}")
public class RemoteSpinnakerBeans {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
       return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public static ConversionServiceFactoryBean conversionService() {
        ConversionServiceFactoryBean factory =
                new ConversionServiceFactoryBean();
        Set<Converter<?, ?>> converters = new HashSet<Converter<?,?>>();
        converters.add(new StringToSpinnakerMachine());
        factory.setConverters(converters);
        return factory;
    }

    @Value("${machines}")
    private List<SpinnakerMachine> machines;

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

    @Value("${baseserver.url}")
    private URL baseServerUrl;

    @Value("${deleteJobsOnExit}")
    private boolean deleteJobsOnExit;

    @Bean
    public MachineManager machineManager() {
        return new MachineManager(machines);
    }

    @Bean
    public NMPIQueueManager queueManager() throws NoSuchAlgorithmException,
            KeyManagementException {
        return new NMPIQueueManager(nmpiUrl, nmpiHardware, resultsDirectory,
                baseServerUrl, nmpiUsername, nmpiPassword,
                nmpiPasswordIsApiKey, deleteJobsOnExit);
    }

    @Bean
    public JobExecuterFactory jobExecuterFactory() throws IOException {
        return new LocalJobExecuterFactory();
    }

    @Bean
    public JobManager jobManager() throws IOException,
            NoSuchAlgorithmException, KeyManagementException {
        return new JobManager(machineManager(), queueManager(),
                baseServerUrl, jobExecuterFactory());
    }
}
