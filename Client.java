import java.net.*;
import java.io.*;

class Client {
	public static void main(String[] args) {
		try {
			int c;
			Socket s = new Socket("sala",8189);
			InputStream in = new DataInputStream(s.getInputStream());
			OutputStream out = new DataOutputStream(s.getOutputStream());
			while(true) {
				System.out.println("Opciones del Cliente:");
				System.out.println("	1.- Estado de descargas.");
				System.out.println("	2.- Lista de libros.");
				System.out.println("	3.- Solicitud de libro.");
				System.out.println("	4.- Libros descargados por servidor.");
			}
		} catch (Exception e) { System.out.println(e); }
	}
}