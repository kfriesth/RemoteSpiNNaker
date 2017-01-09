package uk.ac.manchester.cs.spinnaker.machinemanager.responses;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Machine {
    private String name;
    private List<String> tags;
    private int width;
    private int height;
    private List<Object> deadBoards;
    private List<Object> deadLinks;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

	@JsonProperty("dead_boards")
	public List<Object> getDeadBoards() {
		return deadBoards;
	}

	public void setDeadBoards(List<Object> deadBoards) {
		this.deadBoards = deadBoards;
	}

	@JsonProperty("dead_links")
	public List<Object> getDeadLinks() {
		return deadLinks;
	}

	public void setDeadLinks(List<Object> deadLinks) {
		this.deadLinks = deadLinks;
	}
}
