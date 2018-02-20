import java.net.*;
import java.io.*;

public class ThreadedEchoHandler extends Thread {
	private Socket ss;
	private int counter;
	public ThreadedEchoHandler(Socket i, int c) { ss = i; counter = c; }
	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(ss.getInputStream()));
			PrintWriter out = new PrintWriter(ss.getOutputStream(), true);
			out.println("Hola! Escribe BYE para salir.");
			boolean done = false;
			while (!done) {
				String str = in.readline();
				if (str == null) done = true;
				else {
					out.println("Echo " + counter + " : " + str);
					if (str.trim().equals("BYE")) done = true;
				}
			}
			ss.close();
		} catch (Exception e) { System.out.println(e); }
	}
}