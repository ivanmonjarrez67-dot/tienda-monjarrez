package entidades.persistenciadatos;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 *
 * @author Iván Monjarrez
 */
public class MiObjectOutputStream extends ObjectOutputStream{
    
    public MiObjectOutputStream(OutputStream out) throws IOException {
        super(out);
    }

    @Override
    protected void writeStreamHeader() throws IOException {
        // No escribir encabezado para permitir la adición de objetos
        reset();
    }
    
}
