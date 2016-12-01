package uk.ac.manchester.cs.spinnaker.remote.web;

import static java.lang.System.getProperty;
import static javax.servlet.DispatcherType.ASYNC;
import static javax.servlet.DispatcherType.ERROR;
import static javax.servlet.DispatcherType.REQUEST;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.cxf.transport.servlet.CXFServlet;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;

public class WebApplicationConfig implements WebApplicationInitializer {
	/**
	 * The name of the <i>system property</i> that describes where to load
	 * configuration properties from.
	 */
	public static final String LOCATION_PROPERTY = "remotespinnaker.properties.location";
	private static final String FILTER_NAME = "springSecurityFilterChain";
	private static final boolean ADD_FILTER = false;

	@Override
	public void onStartup(ServletContext container) throws ServletException {
		try {
			AnnotationConfigWebApplicationContext annotationConfig = new AnnotationConfigWebApplicationContext();
			ResourcePropertySource properties = getPropertySource();
			annotationConfig.getEnvironment().getPropertySources()
					.addFirst(properties);
			annotationConfig.register(RemoteSpinnakerBeans.class);
			container.addListener(new ContextLoaderListener(annotationConfig));

			container.addServlet("cxf", CXFServlet.class).addMapping(
					properties.getProperty("cxf.path") + "/*");

			if (ADD_FILTER)
				addFilterChain(container);
		} catch (IOException e) {
			throw new ServletException(e);
		}
	}

	private void addFilterChain(ServletContext container) {
		container
				.addFilter(FILTER_NAME, new DelegatingFilterProxy(FILTER_NAME))
				.addMappingForUrlPatterns(EnumSet.of(REQUEST, ERROR, ASYNC),
						false, "/*");
	}

	private ResourcePropertySource getPropertySource() throws IOException {
		File source = new File(getProperty(LOCATION_PROPERTY));
		return new ResourcePropertySource(source.toURI().toString());
	}
}
