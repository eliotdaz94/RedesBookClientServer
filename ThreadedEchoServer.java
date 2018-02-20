import java.net.*;
import java.io.*;

public class ThreadedEchoServer {
	
	//CAMBIAR ESTA VERGA
	public static void listFilesForFolder(final File folder) {
		for (final File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory()) {
				listFilesForFolder(fileEntry);
			} else {
				System.out.println(fileEntry.getName());
			}
		}
	}
	
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