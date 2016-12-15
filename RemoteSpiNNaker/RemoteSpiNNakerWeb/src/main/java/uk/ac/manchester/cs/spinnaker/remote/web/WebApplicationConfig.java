package uk.ac.manchester.cs.spinnaker.remote.web;

import static java.lang.System.getProperty;
import static javax.servlet.DispatcherType.ASYNC;
import static javax.servlet.DispatcherType.ERROR;
import static javax.servlet.DispatcherType.REQUEST;
import static org.springframework.core.env.AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.cxf.transport.servlet.CXFServlet;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;

/* Instead of web.xml; application entry point */
public class WebApplicationConfig implements WebApplicationInitializer {
	/**
	 * The name of the <i>system property</i> that describes where to load
	 * configuration properties from.
	 */
	public static final String LOCATION_PROPERTY = "remotespinnaker.properties.location";
	/**
	 * The name of the <i>system property</i> that describes what Spring
	 * application profiles are enabled. Contains a comma-separated list of
	 * enabled profiles; they're (supposed to be) orthogonal to each other.
	 * <p>
	 * <h3><i>Profile Names</i></h3>
	 * <dl>
	 * <dt>db</dt>
	 * <dd>Store webapp state in a database.</dt>
	 * <dt>security</dt>
	 * <dd>Require HBP auth to talk to the webapp.</dt> 
	 * </dl>
	 * @see RemoteSpinnakerBeans
	 */
	public static final String PROFILES_PROPERTY = ACTIVE_PROFILES_PROPERTY_NAME;

	private static final String FILTER_NAME = "springSecurityFilterChain";
	private static final boolean ADD_FILTER = false;
	private static final boolean ADD_SERVLET = true;

	@Override
	public void onStartup(ServletContext container) throws ServletException {
		try {
			PropertySource<?> properties = getPropertySource();
			if (ADD_SERVLET | ADD_FILTER)
				container.addListener(getContextLoaderListener(properties));
			if (ADD_SERVLET)
				addServlet(container, properties);
			if (ADD_FILTER)
				addFilterChain(container);
		} catch (IOException e) {
			throw new ServletException(e);
		}
	}

	private ContextLoaderListener getContextLoaderListener(
			PropertySource<?> props) {
		AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
		context.getEnvironment().getPropertySources().addFirst(props);
		context.register(RemoteSpinnakerBeans.class);
		return new ContextLoaderListener(context);
	}

	private void addServlet(ServletContext container,
			PropertySource<?> properties) {
		container.addServlet("cxf", CXFServlet.class).addMapping(
				properties.getProperty("cxf.path") + "/*");
	}

	private void addFilterChain(ServletContext container) {
		container
				.addFilter(FILTER_NAME, new DelegatingFilterProxy(FILTER_NAME))
				.addMappingForUrlPatterns(EnumSet.of(REQUEST, ERROR, ASYNC),
						false, "/*");
	}

	private PropertySource<?> getPropertySource() throws IOException {
		File source = new File(getProperty(LOCATION_PROPERTY));
		return new ResourcePropertySource(source.toURI().toString());
	}
}
