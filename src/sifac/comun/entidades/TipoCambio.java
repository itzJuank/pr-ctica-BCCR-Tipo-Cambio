package sifac.comun.entidades;

/**
 * Tipo de cambio de una fecha, independiente del origen de los datos.
 */
public class TipoCambio {

    private final String fecha;
    private final double compra;
    private final double venta;

    /**
     * @param fecha fecha en formato AAAA-MM-DD
     * @param compra tasa de compra
     * @param venta tasa de venta
     */
    public TipoCambio(String fecha, double compra, double venta) {
        this.fecha = fecha;
        this.compra = compra;
        this.venta = venta;
    }

    public String getFecha() {
        return fecha;
    }

    public double getCompra() {
        return compra;
    }

    public double getVenta() {
        return venta;
    }

    public double diferencial() {
        return venta - compra;
    }

    @Override
    public String toString() {
        return "TipoCambio{fecha='" + fecha + "', compra=" + compra
                + ", venta=" + venta + "}";
    }
}
