import java.rmi.Remote;

/**
 * Clase Comandos, en esta se definen las clases "anonimas" que usara el cliente para saber que informacion enviar, de
 * esta manera se reduce la memoria utilizada y mejora el desempeño.
 */
final public class Commands {

    public static class Echo extends RemoteCommand {

    }

    public static class Books extends RemoteCommand {

    }

    public static class Request extends RemoteCommand {
        String name;

        Request(String n){
            name = n;
        }
    }

    public static class Size extends RemoteCommand {
        String name;

        Size(String n){
            name = n;
        }
    }

    public static class Finish extends RemoteCommand{
        String name;

        Finish(String n){
            name = n;
        }
    }

    public static class Restart extends RemoteCommand{
        String name;
        String from;

        Restart(String n, String f){
            name = n;
            from = f;
        }
    }
}
