package uk.ac.manchester.cs.spinnaker.remote.web;

import java.io.IOException;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.apache.cxf.transport.servlet.CXFServlet;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;

public class WebApplicationConfig implements WebApplicationInitializer {

    @Override
    public void onStartup(ServletContext container) throws ServletException {
        try {
            AnnotationConfigWebApplicationContext annotationConfig =
                new AnnotationConfigWebApplicationContext();
            ResourcePropertySource properties = new ResourcePropertySource(
                "file:" + System.getProperty(
                    "remotespinnaker.properties.location"));
            annotationConfig.getEnvironment().getPropertySources().addFirst(
                properties);
            annotationConfig.register(RemoteSpinnakerBeans.class);
            container.addListener(new ContextLoaderListener(annotationConfig));

            ServletRegistration.Dynamic dispatcher = container.addServlet(
                "cxf", CXFServlet.class);
            dispatcher.addMapping(
                properties.getProperty("cxf.path") + "/*");

            /*DelegatingFilterProxy springSecurityFilterChain =
                new DelegatingFilterProxy("springSecurityFilterChain");

            FilterRegistration.Dynamic filter = container.addFilter(
                "springSecurityFilterChain", springSecurityFilterChain);
            filter.addMappingForUrlPatterns(
                EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR,
                    DispatcherType.ASYNC), false, "/*"); */
        } catch (IOException e) {
            throw new ServletException(e);
        }
    }

}
