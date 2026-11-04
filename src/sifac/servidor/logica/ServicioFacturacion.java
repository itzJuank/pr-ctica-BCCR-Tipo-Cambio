package sifac.servidor.logica;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import sifac.comun.entidades.Factura;
import sifac.comun.entidades.LineaFactura;

/** Calcula las lineas, el subtotal y el IVA de una factura mixta. */
public class ServicioFacturacion {

    private static final BigDecimal TASA_IVA = new BigDecimal("0.13");
    private final ServicioTipoCambio servicioTipoCambio;

    public ServicioFacturacion(ServicioTipoCambio servicioTipoCambio) {
        this.servicioTipoCambio = Objects.requireNonNull(servicioTipoCambio);
    }

    public LineaFactura crearLinea(String descripcion, int cantidad,
            double precioUnitario, boolean enDolares) {
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripcion es obligatoria");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if (!Double.isFinite(precioUnitario) || precioUnitario < 0) {
            throw new IllegalArgumentException("El precio debe ser un numero no negativo");
        }

        double importe = cantidad * precioUnitario;
        if (enDolares) {
            importe = servicioTipoCambio.convertirAColonesParaPagar(importe);
        }
        if (!Double.isFinite(importe)) {
            throw new IllegalArgumentException("El importe de la linea es demasiado grande");
        }
        // Cada linea se redondea antes de sumar el subtotal.
        double importeColones = BigDecimal.valueOf(importe)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new LineaFactura(descripcion.strip(), cantidad, precioUnitario,
                enDolares, importeColones);
    }

    public Factura facturar(List<LineaFactura> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("La factura debe tener al menos una linea");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (LineaFactura linea : lineas) {
            subtotal = subtotal.add(BigDecimal.valueOf(linea.getImporteColones()));
        }
        BigDecimal iva = subtotal.multiply(TASA_IVA).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(iva);
        return new Factura(lineas, subtotal.doubleValue(), iva.doubleValue(), total.doubleValue());
    }
}
