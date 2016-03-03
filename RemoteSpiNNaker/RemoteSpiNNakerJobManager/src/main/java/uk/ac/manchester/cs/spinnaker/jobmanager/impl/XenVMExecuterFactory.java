package uk.ac.manchester.cs.spinnaker.jobmanager.impl;

import java.io.IOException;
import java.net.URL;

import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuter;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobExecuterFactory;
import uk.ac.manchester.cs.spinnaker.jobmanager.JobManager;

public class XenVMExecuterFactory implements JobExecuterFactory {

    private URL xenServerUrl = null;

    private String username = null;

    private String password = null;

    private String templateLabel = null;

    public XenVMExecuterFactory(
            URL xenServerUrl, String username, String password,
            String templateLabel) {
        this.xenServerUrl = xenServerUrl;
        this.username = username;
        this.password = password;
        this.templateLabel = templateLabel;
    }

    @Override
    public JobExecuter createJobExecuter(JobManager manager, URL baseUrl)
            throws IOException {
        try {
            return new XenVMExecuter(
                manager, xenServerUrl, username, password, templateLabel,
                baseUrl);
        } catch (Exception e) {
            throw new IOException("Error creating VM", e);
        }
    }

}
