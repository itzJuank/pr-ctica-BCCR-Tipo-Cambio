package sifac.servidor.logica;

import java.io.IOException;
import java.util.Objects;
import sifac.servidor.datos.ConfiguracionToken;

/** Valida la credencial ingresada y delega su almacenamiento al contrato de datos. */
public class ServicioConfiguracionToken {

    private final ConfiguracionToken configuracion;

    public ServicioConfiguracionToken(ConfiguracionToken configuracion) {
        this.configuracion = Objects.requireNonNull(configuracion);
    }

    public void guardarToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token no puede estar vacio");
        }
        String valor = token.strip();
        if (valor.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException("Pegue solo el token, sin espacios ni saltos de linea");
        }
        try {
            configuracion.guardar(valor);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar el token. Revise los permisos de la carpeta", ex);
        }
    }
}
