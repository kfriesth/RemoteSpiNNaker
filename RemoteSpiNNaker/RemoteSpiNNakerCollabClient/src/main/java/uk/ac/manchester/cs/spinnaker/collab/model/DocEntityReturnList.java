package uk.ac.manchester.cs.spinnaker.collab.model;

import java.util.List;

public class DocEntityReturnList {

    private List<DocEntityReturn> result;

    private boolean hasMore;

    public List<DocEntityReturn> getResult() {
        return result;
    }

    public void setResult(List<DocEntityReturn> result) {
        this.result = result;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

}
