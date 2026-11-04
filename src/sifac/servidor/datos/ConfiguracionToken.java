package sifac.servidor.datos;

import java.io.IOException;

/** Contrato para consultar y guardar la credencial del servicio externo. */
public interface ConfiguracionToken {
    String leer() throws IOException;
    void guardar(String token) throws IOException;
}
