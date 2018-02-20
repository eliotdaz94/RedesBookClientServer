import java.net.*;
import java.io.*;
import java.util.*;

class Client {

	public static void main(String[] args) {
		try {
			int c;
			Socket s = new Socket(InetAddress.getLocalHost(),8189);
			//DataInputStream in = new DataInputStream(s.getInputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			DataOutputStream out = new DataOutputStream(s.getOutputStream());	
			Scanner scanner = new Scanner(System.in);
			final File file = new File("/home/anthony/Documents/LibrosRedesTest");
			listFilesForFolder(file);
			while(true) {
				System.out.println("Opciones del Cliente:");
				System.out.println("	1.- Estado de descargas.");
				System.out.println("	2.- Lista de libros.");
				System.out.println("	3.- Solicitud de libro.");
				System.out.println("	4.- Libros descargados por servidor.");
				String output;
				output = in.readUTF();
				System.out.println("The output is " + output);
				String input = scanner.nextLine();
				out.writeUTF(input);
				out.flush();
				System.out.println("KKK");
			}
		} catch (Exception e) { System.out.println(e); }
	}
}