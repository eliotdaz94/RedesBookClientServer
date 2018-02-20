import java.net.*;
import java.io.*;

public class ThreadedEchoServer {
	public static void main(String[] args) {
		int i = 1;
		try {
			ServerSocket s = new ServerSocket(8189);
			for (;;) {
				Socket ss = s.accept();
				System.out.println("Spawning " + i);
				new ThreadedEchoHandler(ss,i).start();
				i++;
			}
		} catch (Exception e) { System.out.println(e); }
	}
}