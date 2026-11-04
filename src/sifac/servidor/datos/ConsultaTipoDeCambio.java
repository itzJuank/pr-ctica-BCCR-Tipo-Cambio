package sifac.servidor.datos;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Cliente HTTP de indicadores economicos; no aplica reglas de conversion. */
public class ConsultaTipoDeCambio {

    private static final URI URL_BASE = URI.create(
            "https://apim.bccr.fi.cr/SDDE/api/"
            + "Bccr.GE.SDDE.Publico.Indicadores.API/indicadoresEconomicos/");
    private final HttpClient cliente;
    private final URI urlBase;
    private final String token;
    private final ConfiguracionToken configuracion;

    public ConsultaTipoDeCambio() {
        this(new ConfiguracionTokenArchivo());
    }

    public ConsultaTipoDeCambio(ConfiguracionToken configuracion) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                URL_BASE, configuracion);
    }

    /** Permite probar el transporte sin conectarse al servicio externo. */
    public ConsultaTipoDeCambio(HttpClient cliente, URI urlBase, String token) {
        this(cliente, urlBase, token, new ConfiguracionTokenArchivo());
    }

    public ConsultaTipoDeCambio(HttpClient cliente, URI urlBase, ConfiguracionToken configuracion) {
        this(cliente, urlBase, null, configuracion);
    }

    private ConsultaTipoDeCambio(HttpClient cliente, URI urlBase, String token,
            ConfiguracionToken configuracion) {
        this.cliente = Objects.requireNonNull(cliente);
        this.urlBase = Objects.requireNonNull(urlBase);
        this.token = token;
        this.configuracion = Objects.requireNonNull(configuracion);
    }

    public String consultarSerie(int indicador, LocalDate inicio, LocalDate fin)
            throws IOException, InterruptedException {
        if (indicador <= 0 || inicio == null || fin == null || inicio.isAfter(fin)) {
            throw new IllegalArgumentException("El indicador o el rango de fechas no es valido");
        }
        String credencial = credencialActual();
        URI url = urlBase.resolve(indicador + "/series?fechaInicio=" + fechaApi(inicio)
                + "&fechaFin=" + fechaApi(fin) + "&idioma=es");
        HttpRequest solicitud = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + credencial)
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> respuesta = cliente.send(solicitud,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (respuesta.statusCode() == 401 || respuesta.statusCode() == 403) {
            throw new IOException("El BCCR rechazo el acceso (HTTP " + respuesta.statusCode()
                    + "). Revise el token y la suscripcion");
        }
        if (respuesta.statusCode() != 200) {
            throw new IOException("El BCCR respondio con HTTP " + respuesta.statusCode());
        }
        return respuesta.body();
    }

    private static String fechaApi(LocalDate fecha) {
        return fecha.format(DateTimeFormatter.ofPattern("uuuu/MM/dd")).replace("/", "%2F");
    }

    String credencialActual() throws IOException {
        String valor = token == null ? configuracion.leer() : token.strip();
        if (valor.isBlank()) {
            throw new IllegalStateException("Ingrese el token en la opcion 4 del menu");
        }
        return valor;
    }
}
