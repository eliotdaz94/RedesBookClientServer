import java.net.*;
import java.io.*;
import java.util.*;

public class Libro {

    public String nombre;
    public final File libro;

    Libro(String nombre, String path){
        nombre = nombre;
        libro = new File(path);
    }

    public String toString(){
        return this.name;
    }

}