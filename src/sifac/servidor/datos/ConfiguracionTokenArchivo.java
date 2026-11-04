package sifac.servidor.datos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

/** Conserva el token localmente; el entorno se usa cuando no hay un token guardado. */
public class ConfiguracionTokenArchivo implements ConfiguracionToken {

    private final Path archivo;
    private final Supplier<String> tokenEntorno;

    public ConfiguracionTokenArchivo() {
        this(Path.of("bccr.properties"), () -> System.getenv("BCCR_TOKEN"));
    }

    public ConfiguracionTokenArchivo(Path archivo, Supplier<String> tokenEntorno) {
        this.archivo = Objects.requireNonNull(archivo).toAbsolutePath();
        this.tokenEntorno = Objects.requireNonNull(tokenEntorno);
    }

    @Override
    public String leer() throws IOException {
        String token = leerPropiedades().getProperty("token", "").strip();
        if (!token.isBlank()) {
            return token;
        }
        String entorno = tokenEntorno.get();
        return entorno == null ? "" : entorno.strip();
    }

    @Override
    public void guardar(String token) throws IOException {
        Properties propiedades = leerPropiedades();
        propiedades.setProperty("token", Objects.requireNonNull(token));
        Path temporal = Files.createTempFile(archivo.getParent(), "bccr-", ".tmp");
        try {
            try (Writer escritor = Files.newBufferedWriter(temporal, StandardCharsets.UTF_8)) {
                propiedades.store(escritor, "Configuracion local de acceso al BCCR");
            }
            try {
                Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private Properties leerPropiedades() throws IOException {
        Properties propiedades = new Properties();
        if (Files.exists(archivo)) {
            try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
                propiedades.load(lector);
            }
        }
        return propiedades;
    }
}
