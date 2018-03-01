import java.io.File;

public class Libro {

    public String nombre;
    public final File libro;

    Libro(String name, final File file){
        nombre = name;
        libro = file;
    }

    public String toString(){
        return this.nombre;
    }
}