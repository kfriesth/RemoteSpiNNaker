package uk.ac.manchester.cs.spinnaker.model;

public class NMPILog {
    private StringBuilder content;

    public String getContent() {
        if (content == null)
            return null;
        return content.toString();
    }

    public void setContent(String content) {
        this.content = new StringBuilder(content);
    }

    public void appendContent(String content) {
        if (this.content == null)
            this.content = new StringBuilder(content);
        else
            this.content.append(content);
    }
}
