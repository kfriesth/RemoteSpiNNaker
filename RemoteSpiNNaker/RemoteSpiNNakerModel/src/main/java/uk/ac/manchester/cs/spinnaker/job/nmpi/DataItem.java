package uk.ac.manchester.cs.spinnaker.job.nmpi;

public class DataItem {
    private String url;

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
