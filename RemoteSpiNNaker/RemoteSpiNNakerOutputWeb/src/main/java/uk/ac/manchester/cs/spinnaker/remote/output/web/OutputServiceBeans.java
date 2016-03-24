package uk.ac.manchester.cs.spinnaker.remote.output.web;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.oauth2.sdk.ParseException;

import uk.ac.manchester.cs.spinnaker.output.OutputManager;
import uk.ac.manchester.cs.spinnaker.output.impl.OutputManagerImpl;
import uk.ac.manchester.cs.spinnaker.remote.webutils.BearerOidcClient;
import uk.ac.manchester.cs.spinnaker.remote.webutils.CollabSecurityService;
import uk.ac.manchester.cs.spinnaker.remote.webutils.DirectClientAuthenticationFilter;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled=true, proxyTargetClass=true)
@EnableWebSecurity
@Import(JaxRsConfig.class)
public class OutputServiceBeans {

    @Bean
    public static PropertySourcesPlaceholderConfigurer
            propertySourcesPlaceholderConfigurer() {
       return new PropertySourcesPlaceholderConfigurer();
    }

    @Autowired
    private ApplicationContext ctx;

    @Value("${results.directory}")
    private File resultsDirectory;

    @Value("${baseserver.url}${cxf.path}${cxf.rest.path}/")
    private URL baseServerUrl;

    @Value("${cxf.rest.path}")
    private String restPath;

    @Value("${cxf.path}${cxf.rest.path}")
    private String restServicePath;

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

    @Value("${document.service.uri}")
    private URL documentServiceUri;

    @Value("${results.purge.days}")
    private long nDaysToKeepResults;

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth)
            throws Exception {
        auth.authenticationProvider(clientProvider()).eraseCredentials(false);
    }

    @Bean
    public CollabSecurityService collabSecurityService()
            throws MalformedURLException {
        return new CollabSecurityService(new URL(collabServiceUri));
    }

    @Bean
    public OidcClient hbpAuthenticationClient() {
        OidcClient oidcClient = new OidcClient();
        oidcClient.setClientID(oidcClientId);
        oidcClient.setSecret(oidcSecret);
        oidcClient.setDiscoveryURI(oidcDiscoveryUri);
        oidcClient.setScope("openid profile hbp.collab hbp.documents");
        oidcClient.setPreferredJwsAlgorithm(JWSAlgorithm.RS256);
        return oidcClient;
    }

    @Bean
    public BearerOidcClient hbpBearerClient()
            throws ParseException, MalformedURLException, IOException {
        BearerOidcClient client = new BearerOidcClient(oidcDiscoveryUri, "");
        return client;
    }

    @Bean
    public Clients clients()
            throws ParseException, MalformedURLException, IOException {
        return new Clients(
            oidcRedirectUri, hbpAuthenticationClient(), hbpBearerClient());
    }

    @Bean
    public ClientAuthenticationProvider clientProvider()
            throws ParseException, MalformedURLException, IOException {
        ClientAuthenticationProvider provider =
            new ClientAuthenticationProvider();
        provider.setClients(clients());
        return provider;
    }

    @Configuration
    @Order(100)
    public static class HbpAuthentication extends WebSecurityConfigurerAdapter {

        @Value("${cxf.path}${cxf.rest.path}")
        String restServicePath;

        @Value("${callback.path}")
        private String callbackPath;

        @Autowired
        OidcClient hbpAuthenticationClient;

        @Autowired
        BearerOidcClient hbpBearerClient;

        @Autowired
        Clients clients;

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            Path path = AnnotationUtils.findAnnotation(
                OutputManager.class, Path.class);
            http.addFilterBefore(
                    directAuthFilter(),
                    UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                    callbackFilter(),
                    UsernamePasswordAuthenticationFilter.class)
                .csrf().disable()
                .exceptionHandling().authenticationEntryPoint(
                        hbpAuthenticationEntryPoint()).and()
                .authorizeRequests().antMatchers(
                    restServicePath + path.value() + "/**").authenticated()
                                   .anyRequest().permitAll();
        }

        @Bean
        public ClientAuthenticationFilter callbackFilter() throws Exception {
            ClientAuthenticationFilter filter =
                new ClientAuthenticationFilter(callbackPath);
            filter.setClients(clients);
            filter.setAuthenticationManager(authenticationManagerBean());
            return filter;
        }

        @Bean
        public DirectClientAuthenticationFilter directAuthFilter()
                throws Exception {
            DirectClientAuthenticationFilter filter =
                new DirectClientAuthenticationFilter(
                    authenticationManagerBean());
            filter.setClient(hbpBearerClient);
            return filter;
        }

        @Bean
        public ClientAuthenticationEntryPoint hbpAuthenticationEntryPoint() {
            ClientAuthenticationEntryPoint entryPoint =
                new ClientAuthenticationEntryPoint();
            entryPoint.setClient(hbpAuthenticationClient);
            return entryPoint;
        }
    }

    @Bean
    public OutputManager outputManager() {
        return new OutputManagerImpl(
            baseServerUrl, documentServiceUri, resultsDirectory,
            nDaysToKeepResults);
    }

    @Bean
    public Server jaxRsServer()
            throws KeyManagementException, NoSuchAlgorithmException,
            IOException {

        List<Object> beans = new ArrayList<Object>();
        beans.add(outputManager());

        JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
        factory.setAddress(restPath);
        factory.setBus(ctx.getBean(SpringBus.class));
        factory.setServiceBeans(beans);
        factory.setProviders(Arrays.asList(new JacksonJsonProvider()));
        return factory.create();
    }
}
