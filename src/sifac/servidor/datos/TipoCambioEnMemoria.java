package sifac.servidor.datos;

import java.util.ArrayList;
import java.util.List;
import sifac.comun.entidades.TipoCambio;

/**
 * Repositorio con tres tipos de cambio almacenados en memoria.
 */
public class TipoCambioEnMemoria implements RepositorioTipoCambio {

    private final List<TipoCambio> datos = new ArrayList<>();

    public TipoCambioEnMemoria() {
        datos.add(new TipoCambio("2026-10-30", 504.80, 511.90));
        datos.add(new TipoCambio("2026-11-02", 505.50, 512.75));
        datos.add(new TipoCambio("2026-11-03", 506.25, 513.40));
    }

    @Override
    public TipoCambio obtenerPorFecha(String fecha) {
        for (TipoCambio tipoCambio : datos) {
            if (tipoCambio.getFecha().equals(fecha)) {
                return tipoCambio;
            }
        }
        return null;
    }

    @Override
    public TipoCambio obtenerMasReciente() {
        TipoCambio masReciente = null;
        for (TipoCambio tipoCambio : datos) {
            if (masReciente == null
                    || tipoCambio.getFecha().compareTo(masReciente.getFecha()) > 0) {
                masReciente = tipoCambio;
            }
        }
        return masReciente;
    }
}
