package uk.ac.manchester.cs.spinnaker.collab.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CollabPermissions {

    private boolean delete;

    private boolean update;

    private boolean view;

    @JsonProperty("DELETE")
    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    @JsonProperty("UPDATE")
    public boolean isUpdate() {
        return update;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }

    @JsonProperty("VIEW")
    public boolean isView() {
        return view;
    }

    public void setView(boolean view) {
        this.view = view;
    }

}
