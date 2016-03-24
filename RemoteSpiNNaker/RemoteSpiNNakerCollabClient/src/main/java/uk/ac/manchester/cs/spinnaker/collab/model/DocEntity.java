package uk.ac.manchester.cs.spinnaker.collab.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocEntity {

    private String parent = null;

    private String name = null;

    public DocEntity() {

        // Does Nothing
    }

    public DocEntity(String parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    @JsonProperty("_parent")
    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    @JsonProperty("_name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
