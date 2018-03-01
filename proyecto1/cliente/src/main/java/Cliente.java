import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class Cliente {

    ForkJoinPool workerPool = new ForkJoinPool();

    private String host;
    private int port;
    private HashMap<String, ArrayList<String>> librosServer = new HashMap<>();
    private HashMap<String, Integer> librosSize = new HashMap<>();
    private HashMap<String, Integer> librosDownload = new HashMap<>();

    public Cliente(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * La funcion execute, es la que se encarga del manejo y procesamiento de todas las solicitudes del cliente que
     * involucren a algun servidor. Se encarga de abrir un canal no persistente hacia la direccion y puerto del
     * servidor mediante el cual establece la comunicacion por este mismo canal se realiza la lectura de la respuesta
     * al momento de su cierre del lado del servidor, luego de leer dependiento del comando enviado por el cliente
     * se realiza la logica pertinente.
     * @param command Comando a ejecutar por el cliente para solicitar libros, descargas o reanudaciones.
     * @param addr Direccion a la cual se conectara el canal.
     * @param port Puerto al cual se conectara el canal.
     * @return Esta funcion retorna un futuro de string el cual es un evento que se encargara de disparar el valor
     * obtenido de parte del servidor cuando el proceso asincrono deje de funcionar.
     * @throws IOException
     */
    public CompletableFuture<String> execute(RemoteCommand command, String addr, int port) throws IOException {
        byte[] serialized = RemoteCommand.serialize(command);
        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();

        return CompletableIO.<Void, Cliente>execute(handler ->
                channel.connect(new InetSocketAddress(addr, port), this, handler))
                .thenComposeAsync(nothing -> CompletableIO.<Integer, Cliente>execute(handler -> channel.write(ByteBuffer.wrap(serialized), 600, TimeUnit.SECONDS, this, handler)), workerPool)
                .thenComposeAsync(written -> {
                    String bookName = null;
                    if(command instanceof Commands.Request){
                        bookName = ((Commands.Request) command).name;
                        librosDownload.put(bookName,0);
                    }
                    return readUntilCompletion(channel, 600, bookName);
                }, workerPool)
                .thenApplyAsync((ByteBuffer result) -> {
                    String regreso = new String(result.array(), StandardCharsets.UTF_8);
                    if(command instanceof Commands.Request) {
                        if(regreso.equals("0")){
                            return "Libro no conseguido.";
                        }
                        System.out.println("Libro conseguido, iniciando descarga.");
                        String nombreLibro = ((Commands.Request) command).name;
                        try {
                            FileOutputStream fos = new FileOutputStream("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/LibrosDescargados/" + nombreLibro + "_download.pdf");
                            fos.write(result.array());
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        try{
                            Gson gson = new Gson();
                            ArrayList<String> entry;
                            String key = channel.getRemoteAddress().toString();
                            if(librosServer == null) {
                            librosServer = new HashMap<>();
                            }
                            if(librosServer.containsKey(key)) {
                                entry = librosServer.get(key);
                            }
                            else{
                                entry = new ArrayList<>();
                            }
                            entry.add(nombreLibro);
                            librosServer.put(key, entry);
                            librosDownload.remove(nombreLibro);
                            try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosServer.json")) {
                                gson.toJson(librosServer, writer);
                            }
                        }catch (Exception e){
                            System.out.println(e);
                            e.printStackTrace();
                         }
                         librosSize.put(nombreLibro, null);
                         return "Descarga finalizada.";
                        }
                        if(command instanceof Commands.Restart){
                            String nombreLibro = ((Commands.Restart) command).name;
                            String from = ((Commands.Restart) command).from;

                            byte[] bytesArray = new byte[librosDownload.get(nombreLibro)];

                            FileInputStream fis;
                            try {
                                fis = new FileInputStream("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/LibrosDescargados/" + nombreLibro + "_download.pdf");
                                fis.read(bytesArray);
                                fis.close();

                                byte[] c = new byte[bytesArray.length + result.array().length];
                                System.arraycopy(bytesArray, 0, c, 0, bytesArray.length);
                                System.arraycopy(result.array(), 0, c, bytesArray.length, result.array().length);

                                FileOutputStream fos = new FileOutputStream("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/LibrosDescargados/" + nombreLibro + "_download.pdf");
                                fos.write(c);
                                fos.close();

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    return  regreso;
                });
    }

    /**
     * Lee de el canal dado hasta el final del mismo de forma recursiva.
     * @param channel El canal para leer.
     * @param timeoutSeconds El tiempo de espera por la lectura.
     * @param fileName Nombre del archivo que se esta leyendo para mantener control sobre los datos en json.
     * @return
     */
    private CompletableFuture<ByteBuffer> readUntilCompletion(AsynchronousSocketChannel channel, int timeoutSeconds, String fileName) {

        int bufferSize = 128;
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        final ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        return CompletableIO.<Integer, Cliente>execute(h -> channel.read(buffer, timeoutSeconds, TimeUnit.SECONDS, this, h))
                .thenComposeAsync(read -> {
                    if(read == -1) {
                        return CompletableFuture.completedFuture(ByteBuffer.wrap(new byte[0]));
                    } else {
                        return readUntilCompletion(channel, timeoutSeconds, fileName)
                                .thenApplyAsync(next -> {
                                    ByteBuffer result = ByteBuffer.allocate(read + next.capacity());
                                    buffer.position(0);
                                    result.put(buffer.array(), 0, read).put(next.array());
                                    if(fileName != null){
                                        librosDownload.put(fileName, (read + next.capacity()));
                                        try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosDownload.json")) {
                                            Gson gson = new Gson();
                                            gson.toJson(librosDownload, writer);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    return result;
                                }, workerPool);
                    }
                }, workerPool);
    }

    /**
     * Archivo de corrida pricipal del cliente, se encarga de instanciar los datos y proveer un menu para realizar las
     * solicitudes. Mediante el encadenamiento de funciones asincronas se pueden realizar las solicitudes para luego
     * trabajar con los mensajes que estas proveen.
     * @param args
     */
    public static void main(String[] args){
        try{
            HashMap<String, Boolean> finds = new HashMap<>();
            Cliente cliente = new Cliente("localhost", 8989);
            System.out.println("Connected");

            final Type REVIEW_TYPE = new TypeToken<HashMap<String, ArrayList<String>>>() {
            }.getType();
            Gson gson = new Gson();
            JsonReader reader = new JsonReader(new FileReader("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosServer.json"));
            cliente.librosServer = gson.fromJson(reader, REVIEW_TYPE);
            if(cliente.librosServer == null){
                cliente.librosServer = new HashMap<>();
            }

            final Type REVIEW_TYPE_R = new TypeToken<HashMap<String, Integer>>() {
            }.getType();
            reader = new JsonReader(new FileReader("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosSize.json"));
            cliente.librosSize = gson.fromJson(reader, REVIEW_TYPE_R);
            if(cliente.librosSize == null){
                cliente.librosSize = new HashMap<>();
            }

            reader = new JsonReader(new FileReader("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosDownload.json"));
            cliente.librosDownload = gson.fromJson(reader, REVIEW_TYPE_R);
            if(cliente.librosDownload == null){
                cliente.librosDownload = new HashMap<>();
            }

            String[] addresses = {"159.90.9.10","159.90.9.11","159.90.9.12"};

            if(!cliente.librosDownload.isEmpty()){
                cliente.librosDownload.forEach((key, val) ->
                {
                    finds.put(addresses[0], false);
                    finds.put(addresses[1], false);
                    finds.put(addresses[2], false);
                    try{
                        Command c = Command.parseCommand("restart", key, val.toString());
                        Command s = Command.parseCommand("size", key, null);
                        Command f = Command.parseCommand("finish", key, null);
                        cliente.execute((RemoteCommand) s, addresses[0], 8989)
                        .thenAcceptAsync(size -> {
                            cliente.librosSize.put(key, Integer.parseInt(size));
                            try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosSize.json")) {
                                gson.toJson(cliente.librosSize, writer);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            if (!size.equals("0")) {
                                finds.put(addresses[0], true);
                            }
                        })
                        .thenComposeAsync(nothing -> {
                            try {
                                return cliente.execute((RemoteCommand) c, addresses[0], 8989);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return CompletableFuture.completedFuture("Fallo en la descarga.");
                        })
                        .thenComposeAsync(none -> {
                            try {
                                if (finds.get(addresses[0]))
                                    return cliente.execute((RemoteCommand) f, addresses[0], 8989);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return CompletableFuture.completedFuture("Fallo en la descarga.");
                        })
                        .thenAcceptAsync(none -> {
                            try {
                                if(finds.get(addresses[0])){
                                    return;
                                }
                                cliente.execute((RemoteCommand) s, addresses[1], 8989)
                                .thenAcceptAsync(size -> {
                                    cliente.librosSize.put(key, Integer.parseInt(size));
                                    try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosSize.json")) {
                                        gson.toJson(cliente.librosSize, writer);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    if (!size.equals("0")) {
                                        finds.put(addresses[1], true);
                                    }
                                })
                                .thenComposeAsync(nothing -> {
                                    try {
                                        return cliente.execute((RemoteCommand) c, addresses[1], 8989);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    return CompletableFuture.completedFuture("Fallo en la descarga.");
                                })
                                .thenComposeAsync(no -> {
                                    try {
                                        if (finds.get(addresses[1]))
                                            return cliente.execute((RemoteCommand) f, addresses[1], 8989);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    return CompletableFuture.completedFuture("Fallo en la descarga.");
                                });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        })
                        .thenAcceptAsync(none -> {
                            try {
                                if(finds.get(addresses[1]) || finds.get(addresses[0])){
                                    return;
                                }
                                cliente.execute((RemoteCommand) s, addresses[2], 8989)
                                .thenAcceptAsync(size -> {
                                    cliente.librosSize.put(key, Integer.parseInt(size));
                                    try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosSize.json")) {
                                        gson.toJson(cliente.librosSize, writer);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    if (!size.equals("0")) {
                                        finds.put(addresses[2], true);
                                    }
                                })
                                .thenComposeAsync(nothing -> {
                                    try {
                                        return cliente.execute((RemoteCommand) c, addresses[2], 8989);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    return CompletableFuture.completedFuture("Fallo en la descarga.");
                                })
                                .thenComposeAsync(no -> {
                                    try {
                                        if (finds.get(addresses[2]))
                                            return cliente.execute((RemoteCommand) f, addresses[2], 8989);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    return CompletableFuture.completedFuture("Fallo en la descarga.");
                                });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                }catch (Exception e){
                    System.out.println(e);
                }
                });
            }

            Scanner sc = new Scanner(System.in);
            String output;
            String input;
            while (true) {
                System.out.println("Opciones del Cliente:");
                System.out.println("	1.- Estado de descargas.");
                System.out.println("	2.- Lista de libros.");
                System.out.println("	3.- Solicitud de libro.");
                System.out.println("	4.- Libros descargados por servidor.");
                System.out.println("	5.- Cerrar cliente.");
                input = sc.nextLine();
                if(input.equals("1")){
                    cliente.librosDownload.forEach((key, value) -> {
                        if(cliente.librosSize.containsKey(key)){
                            System.out.println("El libro " + key + " lleva un " +
                                    ((float) cliente.librosDownload.get(key)/cliente.librosSize.get(key))*100 + "%");
                        }
                    });
                }
                else if(input.equals("2")){
                    Command c = Command.parseCommand("books", null, null);
                    cliente.execute((RemoteCommand) c,addresses[0],8989)
                    .thenAcceptAsync(System.out::println);
		    		cliente.execute((RemoteCommand) c,addresses[1],8989)
		    		.thenAcceptAsync(System.out::println);
		    		cliente.execute((RemoteCommand) c,addresses[2],8989)
		    		.thenAcceptAsync(System.out::println);
                }
                else if(input.equals("3")){
                    System.out.println("Ingrese el nombre del libro a descargar: ");
                    String bookName = sc.nextLine();
                    Command c = Command.parseCommand("request", bookName, null);
                    Command s = Command.parseCommand("size", bookName, null);
                    Command f = Command.parseCommand("finish", bookName, null);
                    Boolean find = false;
                    finds.put(addresses[0], false);
                    finds.put(addresses[1], false);
                    finds.put(addresses[2], false);
                    cliente.execute((RemoteCommand) s, addresses[0], 8989)
                    .thenAcceptAsync(size -> {
                        cliente.librosSize.put(bookName, Integer.parseInt(size));
                        try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosSize.json")) {
                            gson.toJson(cliente.librosSize, writer);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (!size.equals("0")) {
                            finds.put(addresses[0], true);
                        }
                    })
                    .thenComposeAsync(nothing -> {
                        try {
                            return cliente.execute((RemoteCommand) c, addresses[0], 8989);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return CompletableFuture.completedFuture("Fallo en la descarga.");
                    })
                    .thenAcceptAsync(System.out::println)
                    .thenComposeAsync(none -> {
                        try {
                            if (finds.get(addresses[0]))
                                return cliente.execute((RemoteCommand) f, addresses[0], 8989);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return CompletableFuture.completedFuture("Fallo en la descarga.");
                    })
                    .thenAcceptAsync(none -> {
                        try {
                            if(finds.get(addresses[0])){
                                return;
                            }
                            cliente.execute((RemoteCommand) s, addresses[1], 8989)
                            .thenAcceptAsync(size -> {
                                cliente.librosSize.put(bookName, Integer.parseInt(size));
                                try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosSize.json")) {
                                    gson.toJson(cliente.librosSize, writer);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if (!size.equals("0")) {
                                    finds.put(addresses[1], true);
                                }
                            })
                            .thenComposeAsync(nothing -> {
                                try {
                                    return cliente.execute((RemoteCommand) c, addresses[1], 8989);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                return CompletableFuture.completedFuture("Fallo en la descarga.");
                            })
                            .thenAcceptAsync(System.out::println)
                            .thenComposeAsync(no -> {
                                try {
                                    if (finds.get(addresses[1]))
                                        return cliente.execute((RemoteCommand) f, addresses[1], 8989);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                return CompletableFuture.completedFuture("Fallo en la descarga.");
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    })
                    .thenAcceptAsync(none -> {
                        try {
                            if(finds.get(addresses[1]) || finds.get(addresses[0])){
                                return;
                            }
                            cliente.execute((RemoteCommand) s, addresses[2], 8989)
                            .thenAcceptAsync(size -> {
                                cliente.librosSize.put(bookName, Integer.parseInt(size));
                                try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosSize.json")) {
                                    gson.toJson(cliente.librosSize, writer);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if (!size.equals("0")) {
                                    finds.put(addresses[2], true);
                                }
                            })
                            .thenComposeAsync(nothing -> {
                                try {
                                    return cliente.execute((RemoteCommand) c, addresses[2], 8989);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                return CompletableFuture.completedFuture("Fallo en la descarga.");
                            })
                            .thenAcceptAsync(System.out::println)
                            .thenComposeAsync(no -> {
                                try {
                                    if (finds.get(addresses[2]))
                                        return cliente.execute((RemoteCommand) f, addresses[2], 8989);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                return CompletableFuture.completedFuture("Fallo en la descarga.");
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                }
                else if(input.equals("4")){
                    cliente.librosServer.forEach((key, value) -> {
                        System.out.println("Del servidor " + key + " se descargaron los siguientes libros: ");
                        value.forEach(val -> System.out.println("    " + val));
                        System.out.println(" ");
                    });
                }
                else if(input.equals("5")){
                    System.exit(0);
                }
                else{
                    System.out.println("Escoja una opcion valida");
                }
            }
        } catch (Exception e) {System.out.println(e);}
    }
}
