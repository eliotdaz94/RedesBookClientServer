import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;


public class Servidor {

    ForkJoinPool workerPool = new ForkJoinPool();

    private static ArrayList<Libro> libros = new ArrayList<>();
    private HashMap<String, Integer> downloadedBooks = new HashMap<>();
    private HashMap<String, Integer> clients = new HashMap<>();
    private HashMap<String, HashMap<String, Integer>> booksClient = new HashMap<>();
    private HashMap<String, ArrayList<String>> currentDownloads = new HashMap<>();

    private static ArrayList<Libro> llenarLibros(final File folder) {
        ArrayList<Libro> result = new ArrayList<>();
        for (final File fileEntry : Objects.requireNonNull(folder.listFiles())) {
            Libro lib = new Libro(fileEntry.getName().split("\\.")[0], fileEntry);
            result.add(lib);
        }
        return result;
    }

    public Servidor() {
        final File file = new File("/home/invitado/Documents/RedesBookClientServer/proyecto1/servidor/src/main/java/Libros");
        libros = llenarLibros(file);
    }

    byte [] executeCommand(String command, AsynchronousSocketChannel clientChannel){
        String serverAdd = "";
        try{
            serverAdd = InetAddress.getLocalHost().toString();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        String[] splitResult = command.split(" ");
        String com = splitResult[0];
        if("Books".equals(com.trim())){
            System.out.println("Se realizo un request de la lista de libros.");
            StringBuilder mensaje = new StringBuilder("La lista de libros disponibles en el servidor " + serverAdd + " es: " + System.lineSeparator());
            for(Libro libro : libros){
                mensaje.append("    ").append(libro.nombre).append(System.lineSeparator());
            }
            try{
                int aux = 1;
                String key = clientChannel.getRemoteAddress().toString().split(":")[0];
                if(clients.containsKey(key)){
                    aux = clients.get(key) + 1;
                }
                clients.put(key, aux);
            } catch (Exception e){
                System.out.println("No se pudo conseguir la direccion del cliente " + e);
            }
            return mensaje.toString().getBytes();
        }
        else{
            Libro elegido = null;
            for(Libro libro : libros){
                if(libro.nombre.equals(splitResult[1])){
                    elegido = libro;
                }
            }
            if(elegido == null){
                return "0".getBytes();
            }
            if(com.equals("Size")){
                Long size = elegido.libro.length();
                return size.toString().getBytes();
            }
            if(com.equals("Finish")){
                System.out.println("Descarga finalizada");
                try{
                    int bookD = 1;
                    if(downloadedBooks.containsKey(elegido.nombre)){
                        bookD = downloadedBooks.get(elegido.nombre) + 1;
                    }
                    downloadedBooks.put(elegido.nombre, bookD);
                    Gson gson = new Gson();
                    String key = clientChannel.getRemoteAddress().toString().split(":")[0];
                    if(booksClient.containsKey(key)){
                        int clientB = 1;
                        if(booksClient.get(key).containsKey(elegido.nombre)){
                            clientB = booksClient.get(key).get(elegido.nombre) + 1;
                        }
                        booksClient.get(key).put(elegido.nombre, clientB);
                    }
                    else {
                        HashMap<String, Integer> aux = new HashMap<>();
                        aux.put(elegido.nombre, 1);
                        booksClient.put(key, aux);
                    }
                    if(currentDownloads.containsKey(key)){
                        currentDownloads.get(key).remove(elegido.nombre);
                    }
                    try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/servidor/src/main/java/librosDescargados.json")) {
                        gson.toJson(booksClient, writer);
                    }
                    return new byte[0];
                }catch (Exception e){
                    System.out.println(e);
                }
            }
            if(com.equals("Restart")){
                System.out.println("Reanudando descarga");
                try{
                    byte[] bytesArray = new byte[(int) elegido.libro.length()];
                    byte[] bytesArrayAux = new byte[(int) bytesArray.length - Integer.parseInt(splitResult[2]) + 1];

                    FileInputStream fis;
                    fis = new FileInputStream(elegido.libro);
                    fis.read(bytesArray);
                    fis.close();

                    System.arraycopy(bytesArray, 0, bytesArrayAux, Integer.parseInt(splitResult[2]) + 1, bytesArray.length);
                    int bookD = 1;
                    if(downloadedBooks.containsKey(elegido.nombre)){
                        bookD = downloadedBooks.get(elegido.nombre) + 1;
                    }
                    downloadedBooks.put(elegido.nombre, bookD);
                    Gson gson = new Gson();
                    String key = clientChannel.getRemoteAddress().toString().split(":")[0];
                    if(booksClient.containsKey(key)){
                        int clientB = 1;
                        if(booksClient.get(key).containsKey(elegido.nombre)){
                            clientB = booksClient.get(key).get(elegido.nombre) + 1;
                        }
                        booksClient.get(key).put(elegido.nombre, clientB);
                    }
                    else {
                        HashMap<String, Integer> aux = new HashMap<>();
                        aux.put(elegido.nombre, 1);
                        booksClient.put(key, aux);
                    }
                    if(currentDownloads.containsKey(key)){
                        currentDownloads.get(key).remove(elegido.nombre);
                    }
                    try (Writer writer = new FileWriter("/home/invitado/Documents/RedesBookClientServer/proyecto1/servidor/src/main/java/librosDescargados.json")) {
                        gson.toJson(booksClient, writer);
                    }
                    return bytesArrayAux;
                }catch (Exception e){
                    System.out.println(e);
                }
            }
            System.out.println("Enviando archivo " + elegido.nombre);
            System.out.println("De tam " + (int) elegido.libro.length());

            byte[] bytesArray = new byte[(int) elegido.libro.length()];
            FileInputStream fis;
            try {
                fis = new FileInputStream(elegido.libro);
                fis.read(bytesArray);
                fis.close();
                String key = clientChannel.getRemoteAddress().toString().split(":")[0];
                if(currentDownloads.containsKey(key)){
                    if(!currentDownloads.get(key).contains(elegido.nombre)){
                        currentDownloads.get(key).add(elegido.nombre);
                    }
                }
                else{
                    ArrayList<String> librosC = new ArrayList<>();
                    librosC.add(elegido.nombre);
                    currentDownloads.put(key, librosC);
                }
                return bytesArray;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new byte[0];
    }

    private CompletableFuture<String> read(AsynchronousSocketChannel channel) {
        return readUntilCompletion(channel, 10)
                .thenApplyAsync(result -> new String(result.array(), StandardCharsets.UTF_8));
    }

    public CompletableFuture<Integer> execute(AsynchronousSocketChannel channel){
        return read(channel)
                .thenComposeAsync(read -> CompletableIO.<Integer, Servidor>execute(handler ->
                {
                    byte[] bytesArray = executeCommand(read, channel);
                    channel.write(ByteBuffer.wrap(bytesArray), 600, TimeUnit.SECONDS, this, handler);
                }), workerPool)
                .thenApply(nothing ->
                {
                    try {
                        channel.close();
                    } catch (Exception e){}
                    return nothing;
                });
    }

    private CompletableFuture<ByteBuffer> readUntilCompletion(AsynchronousSocketChannel channel, int timeoutSeconds) {
        int bufferSize = 1024;
        final ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        return CompletableIO.<Integer, Servidor>execute(h -> channel.read(buffer, timeoutSeconds, TimeUnit.SECONDS, this, h))
                .thenComposeAsync(read -> {
                    ByteBuffer result = ByteBuffer.allocate(read);
                    //System.out.println(Arrays.toString(buffer.array()));
                    buffer.position(0);
                    result.put(buffer.array(), 0, read);
                    return CompletableFuture.completedFuture(result);
                }, workerPool);
    }


    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String option;
        Servidor servidor = new Servidor();

        InetSocketAddress sAddr = new InetSocketAddress(8989);
        AsynchronousServerSocketChannel s;
        try {
            s = AsynchronousServerSocketChannel.open().bind(sAddr);
        } catch (Exception e){ s = null;}
        System.out.println("The server is listening at 8989");

        try {
            final Type REVIEW_TYPE = new TypeToken<HashMap<String, HashMap<String, Integer>>>() {
            }.getType();
            Gson gson = new Gson();
            JsonReader reader = new JsonReader(new FileReader("/home/invitado/Documents/RedesBookClientServer/proyecto1/servidor/src/main/java/librosDescargados.json"));
            servidor.booksClient = gson.fromJson(reader, REVIEW_TYPE);
            if(servidor.booksClient == null){
                servidor.booksClient = new HashMap<>();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        final AsynchronousServerSocketChannel server = s;
        server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel channel, Void aVoid) {
                server.accept(null, this);
                servidor.execute(channel);
            }

            @Override
            public void failed(Throwable throwable, Void aVoid) {

            }
        });

        while (true) {
            System.out.println("Opciones del Servidor:");
            System.out.println("	1.- Libros Descargados.");
            System.out.println("	2.- Clientes que consultaron.");
            System.out.println("	3.- Descargas totales.");
            System.out.println("	4.- Descargas en curso.");
            System.out.println("	5.- Salir del sistema.");
            option = sc.nextLine();

            if(option.equals("1")){
                servidor.downloadedBooks.forEach((key, val) ->
                        System.out.println("Se han descargado " + val + " copias del libro " + key));
            }
            if(option.equals("2")){
                servidor.clients.forEach((key, val) ->
                        System.out.println("El usuario " + key + " ha consultado " + val + " veces"));
            }
            if(option.equals("3")){
                servidor.booksClient.forEach((key, val) -> {
                  System.out.println("El cliente " + key + " ha descargado los siguientes libros: ");
                  val.forEach((name, cant) ->
                        System.out.println(name + " " + cant + " veces"));
                });
            }
            if(option.equals("4")){
                servidor.currentDownloads.forEach((key, val) -> {
                    if(val.size() == 0){
                        return;
                    }
                    System.out.println("El cliente " + key + " esta descargando los siguientes libros: ");
                    val.forEach(System.out::println);
                });
            }
            else if(option.equals("5")){
                System.exit(0);
            }
            else{
                System.out.println("Escoja una opcion valida");
            }
        }
    }
}

