package uk.ac.manchester.cs.spinnaker.job.nmpi;

import org.joda.time.DateTime;

import com.fasterxml.jackson.datatype.joda.deser.DateTimeDeserializer;

public class NMPIDateDeserialiser extends DateTimeDeserializer {

    private static final long serialVersionUID = 1L;

    public NMPIDateDeserialiser() {
        super(DateTime.class);
    }
}
