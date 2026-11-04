package sifac.servidor.datos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import sifac.cliente.presentacion.MenuTipoCambio;
import sifac.comun.entidades.Factura;
import sifac.comun.entidades.TipoCambio;
import sifac.servidor.logica.ServicioFacturacion;
import sifac.servidor.logica.ServicioConfiguracionToken;
import sifac.servidor.logica.ServicioTipoCambio;

/** Pruebas sin internet ni credenciales: ant verificar-tipo-cambio. */
public class TipoCambioBCCRTest {

    private static final URI URL = URI.create("https://ejemplo.invalid/indicadoresEconomicos/");

    public static void main(String[] args) throws IOException {
        conversionYFactura();
        fechasYPublicaciones();
        cacheYErrores();
        respuestasInvalidas();
        interrupcionYCredencial();
        configuracionInteractiva();
        System.out.println("Pruebas aprobadas: transporte, JSON, fechas, cache, errores, conversion y factura.");
    }

    private static void conversionYFactura() {
        HttpPrueba http = new HttpPrueba();
        TipoCambioBCCR repositorio = repositorio(http, new RelojPrueba());
        ServicioTipoCambio servicio = new ServicioTipoCambio(repositorio);
        comprobar(servicio.convertirAColonesParaPagar(120), 61608);
        comprobar(servicio.convertirAColonesParaPagar(85.50), 43895.70);
        comprobar(servicio.convertirAColonesParaRecibir(100), 50625);
        comprobar(servicio.convertirAColonesParaRecibir(250), 126562.50);
        comprobar(servicio.convertirADolaresParaPagar(61608), 120);
        comprobar(servicio.diferencialPorCada100(), 715);
        ServicioFacturacion facturacion = new ServicioFacturacion(servicio);
        Factura factura = facturacion.facturar(List.of(
                facturacion.crearLinea("Tubo PVC 1/2", 10, 1000, false),
                facturacion.crearLinea("Taladro importado", 1, 120, true),
                facturacion.crearLinea("Clavos 2 pulg", 5, 1200, false)));
        comprobar(factura.getSubtotal(), 77608);
        comprobar(factura.getIva(), 10089.04);
        comprobar(factura.getTotal(), 87697.04);
        exigir(http.solicitudes.size() == 2, "Se deben reutilizar las tasas consultadas");
        HttpRequest solicitud = http.solicitudes.get(0);
        exigir("Bearer token-de-prueba".equals(solicitud.headers()
                .firstValue("Authorization").orElse("")), "Falta autenticacion Bearer");
        exigir(solicitud.uri().toString().contains("fechaInicio=2026%2F10%2F04&fechaFin=2026%2F11%2F03"),
                "Rango de fechas incorrecto");
        exigir(solicitud.timeout().orElseThrow().equals(Duration.ofSeconds(15)), "Falta timeout");
    }

    private static void fechasYPublicaciones() {
        HttpPrueba http = new HttpPrueba();
        TipoCambioBCCR repositorio = repositorio(http, new RelojPrueba());
        TipoCambio reciente = repositorio.obtenerMasReciente();
        exigir("2026-11-03".equals(reciente.getFecha()), "La serie no esta ordenada por fecha");
        comprobar(repositorio.obtenerPorFecha("2026-11-02").getCompra(), 505.50);
        exigir(repositorio.obtenerPorFecha("2026-10-31") == null, "No se debe inventar una fecha");
        exigir(repositorio.obtenerPorFecha("2026-11-01") == null, "No se debe completar el fin de semana");
        exigir(repositorio.obtenerPorFecha("2026-11-04") == null, "No se debe usar una fecha futura");
        esperar(IllegalArgumentException.class, () -> repositorio.obtenerPorFecha("03/11/2026"));
        esperar(IllegalArgumentException.class, () -> repositorio.obtenerPorFecha(null));

        http.compra = serie(317, "2026-09-01", "500");
        http.venta = serie(318, "2026-09-01", "510");
        comprobar(repositorio.obtenerPorFecha("2026-09-01").getVenta(), 510);
        exigir(http.solicitudes.get(2).uri().toString().contains(
                "fechaInicio=2026%2F09%2F01&fechaFin=2026%2F09%2F01"), "La consulta historica debe ser exacta");

        HttpPrueba incompleto = new HttpPrueba();
        incompleto.compra = serie(317, "2026-11-03", "506.25");
        incompleto.venta = serie(318, "2026-11-02", "512.75");
        exigir(repositorio(incompleto, new RelojPrueba()).obtenerMasReciente() == null,
                "No se pueden combinar compra y venta de dias diferentes");

        HttpPrueba anterior = new HttpPrueba();
        anterior.compra = serie(317, "2026-11-02T00:00:00", "505.50");
        anterior.venta = serie(318, "2026-11-02", "512.75");
        ServicioTipoCambio servicio = new ServicioTipoCambio(repositorio(anterior, new RelojPrueba()));
        exigir("2026-11-02".equals(servicio.obtenerMasReciente().getFecha()), "Se debe conservar la fecha real");
        String salida = capturarMenu("1\n2\n100\n1\n5\n", servicio);
        exigir(salida.contains("2026-11-02") && salida.contains("51,275.00"),
                "El menu debe utilizar y mostrar la publicacion disponible");

        HttpPrueba futuro = new HttpPrueba();
        futuro.compra = serie(317, "2026-11-04", "9999");
        futuro.venta = serie(318, "2026-11-04", "9999");
        exigir(repositorio(futuro, new RelojPrueba()).obtenerMasReciente() == null,
                "La respuesta no puede adelantar el dia vigente");
    }

    private static void cacheYErrores() {
        HttpPrueba http = new HttpPrueba();
        RelojPrueba reloj = new RelojPrueba();
        TipoCambioBCCR repositorio = repositorio(http, reloj);
        repositorio.obtenerMasReciente();
        reloj.avanzar(Duration.ofMinutes(6));
        http.estadoHttp = 500;
        esperar(IllegalStateException.class, repositorio::obtenerMasReciente);
        http.estadoHttp = 200;
        http.compra = serie(317, "2026-11-03", "600");
        http.venta = serie(318, "2026-11-03", "610");
        comprobar(repositorio.obtenerMasReciente().getVenta(), 610);

        HttpPrueba medianoche = new HttpPrueba();
        RelojPrueba relojNoche = new RelojPrueba();
        relojNoche.instante = Instant.parse("2026-11-04T05:59:00Z");
        TipoCambioBCCR diario = repositorio(medianoche, relojNoche);
        diario.obtenerMasReciente();
        relojNoche.avanzar(Duration.ofMinutes(2));
        diario.obtenerMasReciente();
        exigir(medianoche.solicitudes.size() == 4, "El cambio de dia debe invalidar la cache");
        exigir(medianoche.solicitudes.get(2).uri().toString().contains("fechaFin=2026%2F11%2F04"),
                "Se debe usar la fecha de Costa Rica");

        for (int estado : new int[]{401, 403, 429, 500}) {
            HttpPrueba fallo = new HttpPrueba();
            fallo.estadoHttp = estado;
            IllegalStateException error = esperar(IllegalStateException.class,
                    repositorio(fallo, new RelojPrueba())::obtenerMasReciente);
            exigir(error.getMessage().contains(Integer.toString(estado)), "Debe informarse el error HTTP");
            exigir(!error.getMessage().contains("token-de-prueba"), "No se debe imprimir la credencial");
        }
        HttpPrueba desconectado = new HttpPrueba();
        desconectado.sinConexion = true;
        esperar(IllegalStateException.class, repositorio(desconectado, new RelojPrueba())::obtenerMasReciente);
    }

    private static void respuestasInvalidas() {
        for (String respuesta : List.of("no es JSON", "{}", "null", "{\"estado\":false,\"datos\":[]}",
                "{\"estado\":true,\"datos\":null}", serie(318, "2026-11-03", "506.25"),
                serie(317, "2026-11-03", "0"), serie(317, "2026-11-03", "-1"),
                serie(317, "2026-11-03", "\"NaN\""), serie(317, "2026-11-03", "\"Infinity\""),
                serie(317, "fecha incorrecta", "506.25"))) {
            HttpPrueba http = new HttpPrueba();
            http.compra = respuesta;
            esperar(IllegalStateException.class, repositorio(http, new RelojPrueba())::obtenerMasReciente);
        }
        for (String respuesta : List.of("{\"estado\":true,\"datos\":[]}",
                serie(317, "2026-11-03", "null"))) {
            HttpPrueba http = new HttpPrueba();
            http.compra = respuesta;
            exigir(repositorio(http, new RelojPrueba()).obtenerMasReciente() == null,
                    "Una serie vacia o nula representa ausencia de datos");
        }
    }

    private static void interrupcionYCredencial() {
        HttpPrueba http = new HttpPrueba();
        http.interrumpido = true;
        try {
            esperar(IllegalStateException.class, repositorio(http, new RelojPrueba())::obtenerMasReciente);
            exigir(Thread.currentThread().isInterrupted(), "Se debe conservar la interrupcion");
        } finally {
            Thread.interrupted();
        }
        HttpPrueba sinToken = new HttpPrueba();
        ConsultaTipoDeCambio consulta = new ConsultaTipoDeCambio(sinToken, URL, "");
        esperar(IllegalStateException.class, () -> {
            try {
                consulta.consultarSerie(317, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3));
            } catch (IOException | InterruptedException ex) {
                throw new AssertionError(ex);
            }
        });
        exigir(sinToken.solicitudes.isEmpty(), "No se debe enviar una solicitud sin token");
    }

    private static void configuracionInteractiva() throws IOException {
        Path directorio = Files.createTempDirectory("sifac-token-prueba-");
        Path archivo = directorio.resolve("bccr.properties");
        try {
            ConfiguracionTokenArchivo configuracion = new ConfiguracionTokenArchivo(
                    archivo, () -> "token-entorno");
            exigir("token-entorno".equals(configuracion.leer()), "El entorno debe servir como alternativa");
            ServicioConfiguracionToken credencial = new ServicioConfiguracionToken(configuracion);
            esperar(IllegalArgumentException.class, () -> credencial.guardarToken(" "));
            esperar(IllegalArgumentException.class, () -> credencial.guardarToken("Bearer token"));
            esperar(IllegalArgumentException.class, () -> credencial.guardarToken("token\nsalto"));
            exigir(!Files.exists(archivo), "Un token invalido no se debe guardar");

            Files.writeString(archivo, "otra=conservar\ntoken=token-inicial\n");
            HttpPrueba http = new HttpPrueba();
            ServicioTipoCambio servicio = new ServicioTipoCambio(new TipoCambioBCCR(
                    new ConsultaTipoDeCambio(http, URL, configuracion), new RelojPrueba()));
            servicio.obtenerMasReciente();
            exigir(http.solicitudes.size() == 2, "Falta la consulta inicial");

            String salida = capturarMenu("4\n  token-nuevo  \n1\n5\n", servicio, credencial);
            exigir(salida.contains("Token guardado.") && salida.contains("Hasta luego."),
                    "El menu debe permitir guardar y continuar");
            exigir(!salida.contains("token-nuevo"), "La confirmacion no debe imprimir el token");
            exigir("token-nuevo".equals(configuracion.leer()), "Debe usarse el token ingresado");
            exigir(Files.readString(archivo).contains("otra=conservar"), "Se deben conservar otras propiedades");
            exigir(http.solicitudes.size() == 4, "Cambiar el token debe descartar las tasas en cache");
            exigir("Bearer token-nuevo".equals(http.solicitudes.get(2).headers()
                    .firstValue("Authorization").orElse("")), "La consulta debe usar el nuevo token");
            exigir("token-nuevo".equals(new ConfiguracionTokenArchivo(archivo, () -> "otro-entorno").leer()),
                    "El token debe persistir y tener prioridad sobre el entorno");

            String antes = Files.readString(archivo);
            salida = capturarMenu("4\n\n5\n", servicio, credencial);
            exigir(salida.contains("Configuracion cancelada.") && antes.equals(Files.readString(archivo)),
                    "Cancelar debe conservar el token");
            salida = capturarMenu("4\nBearer incorrecto\n5\n", servicio, credencial);
            exigir(salida.contains("sin espacios") && antes.equals(Files.readString(archivo)),
                    "Una entrada invalida debe informar el problema y conservar el token");
            salida = capturarMenu("4\n", servicio, credencial);
            exigir(salida.contains("Fin de la entrada.") && antes.equals(Files.readString(archivo)),
                    "El fin de entrada no debe borrar el token");

            ConfiguracionToken sinPermiso = new ConfiguracionToken() {
                public String leer() { return ""; }
                public void guardar(String token) throws IOException { throw new IOException("Prueba"); }
            };
            salida = capturarMenu("4\ntoken-prueba\n5\n", servicio,
                    new ServicioConfiguracionToken(sinPermiso));
            exigir(salida.contains("No se pudo guardar el token") && !salida.contains("Token guardado."),
                    "Un fallo de escritura no debe anunciar exito");
        } finally {
            Files.deleteIfExists(archivo);
            Files.deleteIfExists(directorio);
        }
    }

    private static TipoCambioBCCR repositorio(HttpPrueba http, Clock reloj) {
        return new TipoCambioBCCR(new ConsultaTipoDeCambio(http, URL, "token-de-prueba"), reloj);
    }

    private static String serie(int indicador, String fecha, String valor) {
        return "{\"estado\":true,\"datos\":[{\"codigoIndicador\":\"" + indicador
                + "\",\"series\":[{\"fecha\":\"" + fecha + "\",\"valorDatoPorPeriodo\":" + valor + "}]}]}";
    }

    private static String capturarMenu(String datos, ServicioTipoCambio servicio) {
        return capturarMenu(datos, servicio, null);
    }

    private static String capturarMenu(String datos, ServicioTipoCambio servicio,
            ServicioConfiguracionToken credencial) {
        PrintStream original = System.out;
        ByteArrayOutputStream captura = new ByteArrayOutputStream();
        try (PrintStream salida = new PrintStream(captura, true, StandardCharsets.UTF_8)) {
            System.setOut(salida);
            new MenuTipoCambio(new Scanner(datos), servicio,
                    new ServicioFacturacion(servicio), credencial).ejecutar();
        } finally {
            System.setOut(original);
        }
        return captura.toString(StandardCharsets.UTF_8);
    }

    private static void comprobar(double actual, double esperado) {
        exigir(Double.isFinite(actual) && Math.abs(actual - esperado) < 0.00000001,
                "Esperado " + esperado + ", recibido " + actual);
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    private static <T extends Throwable> T esperar(Class<T> tipo, Runnable accion) {
        try {
            accion.run();
        } catch (Throwable ex) {
            if (tipo.isInstance(ex)) {
                return tipo.cast(ex);
            }
            throw new AssertionError("Excepcion inesperada", ex);
        }
        throw new AssertionError("Se esperaba " + tipo.getSimpleName());
    }

    private static class RelojPrueba extends Clock {
        private Instant instante = Instant.parse("2026-11-03T18:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("America/Costa_Rica"); }
        @Override public Clock withZone(ZoneId zona) { return Clock.fixed(instante, zona); }
        @Override public Instant instant() { return instante; }
        void avanzar(Duration duracion) { instante = instante.plus(duracion); }
    }

    private static class HttpPrueba extends HttpClient {
        private final List<HttpRequest> solicitudes = new ArrayList<>();
        private int estadoHttp = 200;
        private boolean sinConexion;
        private boolean interrumpido;
        private String compra = """
                {"estado":true,"datos":[{"codigoIndicador":"317","series":[
                  {"fecha":"2026-11-03","valorDatoPorPeriodo":506.25},
                  {"fecha":"2026-10-30","valorDatoPorPeriodo":504.80},
                  {"fecha":"2026-11-02","valorDatoPorPeriodo":505.50}]}]}
                """;
        private String venta = """
                {"estado":true,"datos":[{"codigoIndicador":318,"series":[
                  {"fecha":"2026-10-30","valorDatoPorPeriodo":511.90},
                  {"fecha":"2026-11-03","valorDatoPorPeriodo":513.40},
                  {"fecha":"2026-11-02","valorDatoPorPeriodo":512.75}]}]}
                """;

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest solicitud, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            solicitudes.add(solicitud);
            if (sinConexion) { throw new IOException("Sin conexion de prueba"); }
            if (interrumpido) { throw new InterruptedException("Interrupcion de prueba"); }
            String respuesta = solicitud.uri().getPath().contains("/317/") ? compra : venta;
            return (HttpResponse<T>) new Respuesta(estadoHttp, respuesta, solicitud);
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(10)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record Respuesta(int statusCode, String body, HttpRequest request) implements HttpResponse<String> {
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (nombre, valor) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
