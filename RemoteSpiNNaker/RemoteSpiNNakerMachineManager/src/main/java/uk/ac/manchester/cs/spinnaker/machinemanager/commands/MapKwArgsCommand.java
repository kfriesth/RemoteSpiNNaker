package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

import java.util.HashMap;
import java.util.Map;

public class MapKwArgsCommand<A, M> extends Command<A, Map<String, M>> {

    private Map<String, M> mapKwArgs = new HashMap<String, M>();

    public MapKwArgsCommand(String command) {
        super(command);
        setKwargs(mapKwArgs);
    }

    public void addKwArg(String key, M value) {
        mapKwArgs.put(key, value);
    }

}
