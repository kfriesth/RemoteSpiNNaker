package uk.ac.manchester.cs.spinnaker.rest;

import org.joda.time.DateTime;

import com.fasterxml.jackson.datatype.joda.deser.DateTimeDeserializer;

public class DateTimeDeserialiser extends DateTimeDeserializer {
    private static final long serialVersionUID = 1L;

    public DateTimeDeserialiser() {
        super(DateTime.class);
    }
}
