package uk.ac.manchester.cs.spinnaker.machinemanager.commands;

public class Command<A, K> {

    private String command;

    private A args = null;

    private K kwargs = null;

    public Command(String command) {
        this.command = command;
    }

    public Command(String command, A args, K kwargs) {
        this.command = command;
        this.args = args;
        this.kwargs = kwargs;
    }

    protected void setArgs(A args) {
        this.args = args;
    }

    protected void setKwargs(K kwargs) {
        this.kwargs = kwargs;
    }

    public String getCommand() {
        return command;
    }

    public A getArgs() {
        return args;
    }

    public K getKwargs() {
        return kwargs;
    }

}
