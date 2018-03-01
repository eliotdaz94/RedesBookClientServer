import java.io.File;

/**
 * Clase libro utilizada para representar los PDF, posee el nombre y el archivo asociado al mismo.
 */
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
