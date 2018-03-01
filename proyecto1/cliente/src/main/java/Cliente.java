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
     * Reads the given channel until the end.
     * @param channel The channel to read.
     * @return
     */
    private CompletableFuture<ByteBuffer> readUntilCompletion(AsynchronousSocketChannel channel, int timeoutSeconds, String fileName) {

        int bufferSize = 128;
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
                    try{
                        Command c = Command.parseCommand("restart", key, val.toString());
                        Command s = Command.parseCommand("size", key, null);
                        Command f = Command.parseCommand("finish", key, null);
                        Boolean find = false;
                        finds.put(addresses[0], false);
                        finds.put(addresses[1], false);
                        finds.put(addresses[2], false);
                        for(int i = 0; i < 3; i++) {
                            final int index = i;
                            if(index == 1 && finds.get(addresses[0])){
                                break;
                            }
                            if(index == 2 && finds.get(addresses[1])){
                                break;
                            }
                            cliente.execute((RemoteCommand) s, addresses[index], 8989)
                            .thenAcceptAsync(size -> {
                                cliente.librosSize.put(key, Integer.parseInt(size));
                                try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosSize.json")) {
                                    gson.toJson(cliente.librosSize, writer);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if (!size.equals("0")) {
                                    finds.put(addresses[index], true);
                                }
                            })
                            .thenComposeAsync(nothing -> {
                                try {
                                    return cliente.execute((RemoteCommand) c, addresses[index], 8989);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                return CompletableFuture.completedFuture("Fallo en la descarga.");
                            })
                            .thenAcceptAsync(System.out::println)
                            .thenComposeAsync(none -> {
                                try {
                                    if (finds.get(addresses[index]))
                                        return cliente.execute((RemoteCommand) f, addresses[index], 8989);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                return CompletableFuture.completedFuture("Fallo en la descarga.");
                            });
                        }
                        finds.put(addresses[0], false);
                        finds.put(addresses[1], false);
                        finds.put(addresses[2], false);
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
                    for(int i = 0; i < 3; i++) {
                        final int index = i;
                        if(index == 1 && finds.get(addresses[0])){
                            break;
                        }
                        if(index == 2 && finds.get(addresses[1])){
                            break;
                        }
                        cliente.execute((RemoteCommand) s, addresses[index], 8989)
                        .thenAcceptAsync(size -> {
                            cliente.librosSize.put(bookName, Integer.parseInt(size));
                            try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/cliente/src/main/java/librosSize.json")) {
                                gson.toJson(cliente.librosSize, writer);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            if (!size.equals("0")) {
                                finds.put(addresses[index], true);
                            }
                        })
                        .thenComposeAsync(nothing -> {
                            try {
                                return cliente.execute((RemoteCommand) c, addresses[index], 8989);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return CompletableFuture.completedFuture("Fallo en la descarga.");
                        })
                        .thenAcceptAsync(System.out::println)
                        .thenComposeAsync(none -> {
                            try {
                                if (finds.get(addresses[index]))
                                    return cliente.execute((RemoteCommand) f, addresses[index], 8989);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return CompletableFuture.completedFuture("Fallo en la descarga.");
                        });
                    }
                    finds.put(addresses[0], false);
                    finds.put(addresses[1], false);
                    finds.put(addresses[2], false);
                }
                else if(input.equals("4")){
                    cliente.librosServer.forEach((key, value) -> {
                        System.out.println("Del servidor " + key + " se descargaron los siguientes libros: ");
                        value.forEach(val -> System.out.println("    " + val));
                        System.out.println(" ");
                    });


                    cliente.librosSize.forEach((key, value) -> {
                        System.out.println("El libro" + key + " tiene tam " + value);
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
