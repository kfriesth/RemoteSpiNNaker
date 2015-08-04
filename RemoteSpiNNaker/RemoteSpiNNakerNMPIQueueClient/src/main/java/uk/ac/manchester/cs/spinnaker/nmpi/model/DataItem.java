package uk.ac.manchester.cs.spinnaker.nmpi.model;

public class DataItem {

    private String url = null;

    public DataItem() {

        // Does Nothing
    }

    public DataItem(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
