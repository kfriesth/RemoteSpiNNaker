package uk.ac.manchester.cs.spinnaker.collab.model;

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

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
