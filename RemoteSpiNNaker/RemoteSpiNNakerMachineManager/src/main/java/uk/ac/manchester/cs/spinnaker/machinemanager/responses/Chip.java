package uk.ac.manchester.cs.spinnaker.machinemanager.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"x", "y"})
@JsonFormat(shape=JsonFormat.Shape.ARRAY)
public class Chip {

    private int x;

    private int y;

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}
