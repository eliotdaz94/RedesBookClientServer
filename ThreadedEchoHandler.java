import java.net.*;
import java.io.*;
import java.util.*;

public class ThreadedEchoHandler extends Thread {
	private Socket ss;
	private int counter;
	public ThreadedEchoHandler(Socket i, int c) { ss = i; counter = c; }
	public void run() {

		try {
			InputStream io = ss.getInputStream();
    		OutputStream os = ss.getOutputStream();
			DataInputStream in = new DataInputStream(io);
			DataOutputStream out = new DataOutputStream(os);
			out.writeUTF("Hola! Escribe BYE para salir.");
			boolean done = false;
			while (!done) {
				String str = in.readUTF();
				System.out.println("The message is " + str);
				if (str == null) done = true;
				else {
					out.writeUTF("Echo " + counter + " : " + str);
					if (str.trim().equals("BYE")) done = true;
				}
			}
			ss.close();
		} catch (Exception e) { System.out.println(e); }
	}
}