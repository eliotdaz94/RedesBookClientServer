import java.net.*;
import java.io.*;
import java.util.*;

public class ThreadedEchoHandler extends Thread {
	
	private Socket ss;
	private int counter;
	public ThreadedEchoHandler(Socket i, int c) { ss = i; counter = c; }


	public static ArrayList<Libro> libros = new ArrayList<>();
	public static HashMap<String, File> descargasUsuarios;

	public static ArrayList<Libro> llenarLibros(final File folder) {
		ArrayList<Libro> result = new ArrayList<>();
		for (final File fileEntry : folder.listFiles()) {
			Libro lib = new Libro(fileEntry.getName(), fileEntry);
			result.add(lib);
		}
		return result;
	}


	public void run() {
		try {
			InputStream io = ss.getInputStream();
    		OutputStream os = ss.getOutputStream();
			DataInputStream in = new DataInputStream(io);
			DataOutputStream out = new DataOutputStream(os);

			final File file = new File("/home/anthony/Documents/LibrosRedesTest");
			libros = llenarLibros(file);

			String message = "";
			message += "Conexion establecida con el servidor ubicado en " +  InetAddress.getLocalHost() + System.lineSeparator();
			message += "Los libros disponibles son: " + System.lineSeparator();
			for (Libro libro : libros){
				message += "    " + libro + System.lineSeparator();
			}
			out.writeUTF(message);
			
			boolean done = false;
			while (!done) {
				String str = in.readUTF();
				System.out.println("The message is " + str);
				if (str == null) done = true;
				else {
					out.writeUTF("Echo " + counter + " : " + str);
					if (str.equals("BYE")) done = true;
				}
			}
			ss.close();
		} catch (Exception e) { System.out.println(e); }
	}
}