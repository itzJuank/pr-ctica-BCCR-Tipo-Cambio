package sifac.servidor.logica;

import java.util.Objects;
import sifac.comun.entidades.TipoCambio;
import sifac.servidor.datos.RepositorioTipoCambio;

/**
 * Reglas de conversion para pagar y recibir montos en dolares.
 */
public class ServicioTipoCambio {

    private final RepositorioTipoCambio repositorio;

    public ServicioTipoCambio(RepositorioTipoCambio repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio,
                "El repositorio de tipo de cambio es obligatorio");
    }

    /** Usa la tasa de venta mas reciente para pagar un monto en dolares. */
    public double convertirAColonesParaPagar(double usd) {
        return usd * obtenerMasReciente().getVenta();
    }

    /** Usa la tasa de compra mas reciente al recibir dolares de un cliente. */
    public double convertirAColonesParaRecibir(double usd) {
        return usd * obtenerMasReciente().getCompra();
    }

    /** Convierte colones a dolares dividiendo entre la tasa de venta. */
    public double convertirADolaresParaPagar(double crc) {
        return crc / obtenerMasReciente().getVenta();
    }

    /** Devuelve el tipo de cambio de la fecha o informa que no hay datos. */
    public TipoCambio delDia(String fecha) {
        TipoCambio tipoCambio = repositorio.obtenerPorFecha(fecha);
        if (tipoCambio == null) {
            throw new IllegalArgumentException("No hay tipo de cambio para " + fecha);
        }
        return tipoCambio;
    }

    /** Diferencia en colones entre pagar y recibir cien dolares. */
    public double diferencialPorCada100() {
        return obtenerMasReciente().diferencial() * 100;
    }

    /** Expone la fecha y las tasas de la ultima publicacion disponible. */
    public TipoCambio obtenerMasReciente() {
        TipoCambio tipoCambio = repositorio.obtenerMasReciente();
        if (tipoCambio == null) {
            throw new IllegalStateException("No hay tipos de cambio disponibles");
        }
        return tipoCambio;
    }
}
