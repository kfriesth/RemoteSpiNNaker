package uk.ac.manchester.cs.spinnaker.remote.web;

import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.ws.rs.Path;

import org.pac4j.core.client.Clients;
import org.pac4j.springframework.security.authentication.ClientAuthenticationProvider;
import org.pac4j.springframework.security.web.ClientAuthenticationEntryPoint;
import org.pac4j.springframework.security.web.ClientAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;
import uk.ac.manchester.cs.spinnaker.rest.OutputManager;

import com.nimbusds.oauth2.sdk.ParseException;

@Configuration("hbp-security")
@Profile("security")
@EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
@Import(HBPSecurityBeans.HbpSecurityServiceBeans.class)
@EnableWebSecurity
public class HBPSecurityBeans extends WebSecurityConfigurerAdapter {
	@Value("${cxf.path}${cxf.rest.path}")
	String restServicePath;
	@Value("${callback.path}")
	private String callbackPath;
	@Autowired
	BasicOidcClient hbpAuthenticationClient;
	@Autowired
	BearerOidcClient hbpBearerClient;
	@Autowired
	private Clients hbpClients;

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth)
			throws Exception {
		auth.authenticationProvider(clientProvider());
	}

	@Bean
	public ClientAuthenticationProvider clientProvider() throws ParseException,
			MalformedURLException, IOException {
		ClientAuthenticationProvider provider = new ClientAuthenticationProvider();
		provider.setClients(hbpClients);
		return provider;
	}

	@Bean
	public ClientAuthenticationFilter callbackFilter() throws Exception {
		ClientAuthenticationFilter filter = new ClientAuthenticationFilter(
				callbackPath);
		filter.setClients(hbpClients);
		filter.setAuthenticationManager(authenticationManagerBean());
		return filter;
	}

	@Bean("authenticationManager")
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}

	@Override
	public void configure(WebSecurity web) throws Exception {
		Path path = findAnnotation(JobManager.class, Path.class);
		web.ignoring().antMatchers(restServicePath + path.value() + "/**");
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		Path path = findAnnotation(OutputManager.class, Path.class);
		http.addFilterBefore(directAuthFilter(),
				UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(callbackFilter(),
						UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling()
				.authenticationEntryPoint(hbpAuthenticationEntryPoint()).and()
				.authorizeRequests()
				.antMatchers(restServicePath + path.value() + "/**")
				.authenticated().anyRequest().permitAll();
	}

	@Bean
	public OncePerRequestFilter directAuthFilter() throws Exception {
		DirectClientAuthenticationFilter filter = new DirectClientAuthenticationFilter(
				authenticationManagerBean());
		filter.setClient(hbpBearerClient);
		return filter;
	}

	@Bean
	public ClientAuthenticationEntryPoint hbpAuthenticationEntryPoint() {
		ClientAuthenticationEntryPoint entryPoint = new ClientAuthenticationEntryPoint();
		entryPoint.setClient(hbpAuthenticationClient);
		return entryPoint;
	}

	@Configuration("hbp-security-beans")
	@Profile("security")
	public static class HbpSecurityServiceBeans {
		@Value("${baseserver.url}${callback.path}")
		private String oidcRedirectUri;

		@Bean
		public Clients clients() throws ParseException, MalformedURLException,
				IOException {
			return new Clients(oidcRedirectUri, hbpAuthenticationClient(),
					hbpBearerClient());
		}

		@Bean
		public CollabSecurityService collabSecurityService()
				throws MalformedURLException {
			return new CollabSecurityService();
		}

		@Bean
		public BasicOidcClient hbpAuthenticationClient() {
			return new BasicOidcClient();
		}

		@Bean
		public BearerOidcClient hbpBearerClient() throws ParseException,
				MalformedURLException, IOException {
			return new BearerOidcClient();
		}
	}
}
