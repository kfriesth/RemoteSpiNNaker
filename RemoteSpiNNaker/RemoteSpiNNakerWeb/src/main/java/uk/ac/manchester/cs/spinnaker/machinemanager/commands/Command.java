package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Command<A> {
    private String command;
    private List<A> args = new ArrayList<>();
	private Map<String, String> kwargs = new HashMap<>();

	protected final void addKwArg(String key, Object value) {
		kwargs.put(key, value.toString());
	}

	@SafeVarargs
	protected final void addArg(A... values) {
		for (A value: values)
			args.add(value);
	}

	public Command(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public List<A> getArgs() {
        return args;
    }

    public Map<String,String> getKwargs() {
        return kwargs;
    }
}
