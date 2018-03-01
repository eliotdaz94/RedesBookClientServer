
public abstract class Command {

    public static Command parseCommand(String commandLine, String nombre) {
        if(commandLine.equals("echo"))
            return new Commands.Echo();
        if(commandLine.equals("books"))
            return new Commands.Books();
        if(commandLine.equals("request"))
            return new Commands.Request(nombre);
        if(commandLine.equals("size"))
            return new Commands.Size(nombre);
        if(commandLine.equals("finish"))
            return new Commands.Finish(nombre);
        return null;
    }
}

