package uk.ac.manchester.cs.spinnaker.collab.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocEntityFile extends DocEntity {

    private String contentType;

    public DocEntityFile() {

        // Does Nothing
    }

    public DocEntityFile(String parent, String name, String contentType) {
        super(parent, name);
        this.contentType = contentType;
    }

    @JsonProperty("_contentType")
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

}
