package sifac.comun.entidades;

import java.util.List;

/** Detalle y totales de una factura expresados en colones. */
public class Factura {

    private final List<LineaFactura> lineas;
    private final double subtotal;
    private final double iva;
    private final double total;

    public Factura(List<LineaFactura> lineas, double subtotal, double iva, double total) {
        this.lineas = List.copyOf(lineas);
        this.subtotal = subtotal;
        this.iva = iva;
        this.total = total;
    }

    public List<LineaFactura> getLineas() {
        return lineas;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public double getIva() {
        return iva;
    }

    public double getTotal() {
        return total;
    }
}
