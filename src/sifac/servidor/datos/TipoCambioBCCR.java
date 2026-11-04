package sifac.servidor.datos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import sifac.comun.entidades.TipoCambio;

/** Combina compra y venta de la misma fecha publicada por el BCCR. */
public class TipoCambioBCCR implements RepositorioTipoCambio {

    private static final int COMPRA = 317;
    private static final int VENTA = 318;
    private static final int DIAS_HISTORIAL = 30;
    private static final Duration DURACION_CACHE = Duration.ofMinutes(5);
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final ConsultaTipoDeCambio consulta;
    private final Clock reloj;
    private NavigableMap<LocalDate, TipoCambio> recientes = new TreeMap<>();
    private LocalDate diaConsultado;
    private Instant venceCache = Instant.MIN;
    private String credencialCache;

    public TipoCambioBCCR() {
        this(new ConsultaTipoDeCambio(), Clock.system(ZoneId.of("America/Costa_Rica")));
    }

    public TipoCambioBCCR(ConsultaTipoDeCambio consulta, Clock reloj) {
        this.consulta = Objects.requireNonNull(consulta);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public synchronized TipoCambio obtenerPorFecha(String fecha) {
        LocalDate dia;
        try {
            dia = LocalDate.parse(Objects.requireNonNull(fecha));
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new IllegalArgumentException("La fecha debe tener formato AAAA-MM-DD", ex);
        }
        LocalDate hoy = LocalDate.now(reloj);
        if (dia.isAfter(hoy)) {
            return null;
        }
        if (!dia.isBefore(hoy.minusDays(DIAS_HISTORIAL))) {
            cargarRecientes(hoy);
            return recientes.get(dia);
        }
        return consultarRango(dia, dia).get(dia);
    }

    @Override
    public synchronized TipoCambio obtenerMasReciente() {
        cargarRecientes(LocalDate.now(reloj));
        return recientes.isEmpty() ? null : recientes.lastEntry().getValue();
    }

    private void cargarRecientes(LocalDate hoy) {
        String credencial;
        try {
            credencial = consulta.credencialActual();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer la configuracion del token", ex);
        }
        if (hoy.equals(diaConsultado) && credencial.equals(credencialCache)
                && reloj.instant().isBefore(venceCache)) {
            return;
        }
        // Se actualiza la cache solamente si ambas consultas terminan con exito.
        NavigableMap<LocalDate, TipoCambio> nuevos = consultarRango(
                hoy.minusDays(DIAS_HISTORIAL), hoy);
        recientes = nuevos;
        diaConsultado = hoy;
        venceCache = reloj.instant().plus(DURACION_CACHE);
        credencialCache = credencial;
    }

    private NavigableMap<LocalDate, TipoCambio> consultarRango(LocalDate inicio, LocalDate fin) {
        try {
            Map<LocalDate, Double> compras = leerSerie(
                    consulta.consultarSerie(COMPRA, inicio, fin), COMPRA, inicio, fin);
            Map<LocalDate, Double> ventas = leerSerie(
                    consulta.consultarSerie(VENTA, inicio, fin), VENTA, inicio, fin);
            NavigableMap<LocalDate, TipoCambio> resultado = new TreeMap<>();
            for (Map.Entry<LocalDate, Double> compra : compras.entrySet()) {
                Double venta = ventas.get(compra.getKey());
                if (venta != null) {
                    LocalDate fecha = compra.getKey();
                    resultado.put(fecha, new TipoCambio(fecha.toString(), compra.getValue(), venta));
                }
            }
            return resultado;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La consulta del tipo de cambio fue interrumpida", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo consultar el tipo de cambio: "
                    + ex.getMessage(), ex);
        }
    }

    private Map<LocalDate, Double> leerSerie(String respuesta, int indicador,
            LocalDate inicio, LocalDate fin) {
        try {
            JsonObject raiz = JSON.fromJson(respuesta, JsonObject.class);
            if (raiz == null || !raiz.has("estado")
                    || !raiz.get("estado").getAsJsonPrimitive().isBoolean()
                    || !raiz.get("estado").getAsBoolean()) {
                throw new IllegalArgumentException("La consulta no fue exitosa");
            }
            JsonArray datos = raiz.getAsJsonArray("datos");
            if (datos == null) {
                throw new IllegalArgumentException("Falta el arreglo de datos");
            }
            Map<LocalDate, Double> valores = new TreeMap<>();
            for (JsonElement dato : datos) {
                JsonObject grupo = dato.getAsJsonObject();
                if (!Integer.toString(indicador).equals(grupo.get("codigoIndicador").getAsString())) {
                    throw new IllegalArgumentException("El indicador recibido no coincide");
                }
                JsonArray series = grupo.getAsJsonArray("series");
                if (series == null) {
                    throw new IllegalArgumentException("Falta el arreglo de series");
                }
                for (JsonElement elemento : series) {
                    JsonObject serie = elemento.getAsJsonObject();
                    String fecha = serie.get("fecha").getAsString();
                    LocalDate dia = fecha.length() == 10 ? LocalDate.parse(fecha)
                            : LocalDate.from(DateTimeFormatter.ISO_DATE_TIME.parse(fecha));
                    JsonElement valor = serie.get("valorDatoPorPeriodo");
                    if (valor == null) {
                        throw new IllegalArgumentException("Falta el valor de la serie");
                    }
                    if (dia.isBefore(inicio) || dia.isAfter(fin) || valor.isJsonNull()) {
                        continue;
                    }
                    double tasa = valor.getAsDouble();
                    if (!Double.isFinite(tasa) || tasa <= 0) {
                        throw new IllegalArgumentException("La tasa debe ser positiva y finita");
                    }
                    Double anterior = valores.putIfAbsent(dia, tasa);
                    if (anterior != null && Double.compare(anterior, tasa) != 0) {
                        throw new IllegalArgumentException("Hay tasas distintas para la misma fecha");
                    }
                }
            }
            return valores;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Respuesta del BCCR no valida para el indicador "
                    + indicador, ex);
        }
    }
}
