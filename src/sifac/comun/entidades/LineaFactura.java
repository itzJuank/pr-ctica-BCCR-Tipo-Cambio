package sifac.comun.entidades;

/** Una linea cotizada y su importe final en colones. */
public class LineaFactura {

    private final String descripcion;
    private final int cantidad;
    private final double precioUnitario;
    private final boolean enDolares;
    private final double importeColones;

    public LineaFactura(String descripcion, int cantidad, double precioUnitario,
            boolean enDolares, double importeColones) {
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.enDolares = enDolares;
        this.importeColones = importeColones;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getCantidad() {
        return cantidad;
    }

    public double getPrecioUnitario() {
        return precioUnitario;
    }

    public boolean isEnDolares() {
        return enDolares;
    }

    public double getImporteColones() {
        return importeColones;
    }
}
