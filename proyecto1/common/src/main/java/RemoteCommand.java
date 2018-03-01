import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Clase abstracta Remotecommand con la cual se generan los mensajes en arreglos de bytes que seran enviados por el
 * cliente al momento de ejecutar una transmision al servidor, de esta forma se logra encapsular la serializacion y
 * envio de mensajes. 
 */
public abstract class RemoteCommand extends Command {

    private static final Charset charset = StandardCharsets.UTF_8;

    public static byte[] serialize(RemoteCommand command) {
        if(command instanceof Commands.Echo) {
            return "echo test".getBytes(charset);
        }
        if(command instanceof Commands.Books) {
            return "Books".getBytes(charset);
        }
        if(command instanceof Commands.Request) {
            String mensaje = "Request " + ((Commands.Request) command).name;
            return mensaje.getBytes(charset);
        }
        if(command instanceof Commands.Size) {
            String mensaje = "Size " + ((Commands.Size) command).name;
            return mensaje.getBytes(charset);
        }
        if(command instanceof  Commands.Finish){
            String mensaje = "Finish " + ((Commands.Finish) command).name;
            return mensaje.getBytes(charset);
        }
        if(command instanceof  Commands.Restart){
            String mensaje = "Restart " + ((Commands.Restart) command).name + " " + ((Commands.Restart) command).from;
            return mensaje.getBytes(charset);
        }

        return null;
    }

    public static RemoteCommand parse(byte[] serialized) {
        String prefix = new String(serialized, 0, serialized.length ,charset);
        if(prefix.equals("ec")) {
            return new Commands.Echo();
        }

        return null;
    }
}
