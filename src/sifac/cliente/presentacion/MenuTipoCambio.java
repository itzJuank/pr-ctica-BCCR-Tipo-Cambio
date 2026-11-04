package sifac.cliente.presentacion;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;
import sifac.comun.entidades.Factura;
import sifac.comun.entidades.LineaFactura;
import sifac.comun.entidades.TipoCambio;
import sifac.servidor.datos.RepositorioTipoCambio;
import sifac.servidor.datos.ConfiguracionToken;
import sifac.servidor.datos.ConfiguracionTokenArchivo;
import sifac.servidor.datos.ConsultaTipoDeCambio;
import sifac.servidor.datos.TipoCambioBCCR;
import sifac.servidor.logica.ServicioConfiguracionToken;
import sifac.servidor.logica.ServicioFacturacion;
import sifac.servidor.logica.ServicioTipoCambio;

/** Consola de SIFAC: entrada de datos y presentacion de resultados. */
public class MenuTipoCambio {

    private final Scanner entrada;
    private final ServicioTipoCambio servicioTipoCambio;
    private final ServicioFacturacion servicioFacturacion;
    private final ServicioConfiguracionToken servicioConfiguracion;

    public MenuTipoCambio(Scanner entrada, ServicioTipoCambio servicioTipoCambio,
            ServicioFacturacion servicioFacturacion) {
        this(entrada, servicioTipoCambio, servicioFacturacion, null);
    }

    public MenuTipoCambio(Scanner entrada, ServicioTipoCambio servicioTipoCambio,
            ServicioFacturacion servicioFacturacion, ServicioConfiguracionToken servicioConfiguracion) {
        this.entrada = entrada;
        this.servicioTipoCambio = servicioTipoCambio;
        this.servicioFacturacion = servicioFacturacion;
        this.servicioConfiguracion = servicioConfiguracion;
    }

    public static void main(String[] args) {
        // El punto de entrada conecta la implementacion con los servicios.
        ConfiguracionToken configuracion = new ConfiguracionTokenArchivo();
        RepositorioTipoCambio repositorio = new TipoCambioBCCR(
                new ConsultaTipoDeCambio(configuracion), java.time.Clock.system(ZoneId.of("America/Costa_Rica")));
        ServicioTipoCambio tipoCambio = new ServicioTipoCambio(repositorio);
        ServicioFacturacion facturacion = new ServicioFacturacion(tipoCambio);
        ServicioConfiguracionToken credencial = new ServicioConfiguracionToken(configuracion);
        try (Scanner entrada = new Scanner(System.in)) {
            new MenuTipoCambio(entrada, tipoCambio, facturacion, credencial).ejecutar();
        }
    }

    public void ejecutar() {
        try {
            while (true) {
                System.out.println("**************************************");
                System.out.println("SIFAC - Tipo de cambio y facturacion");
                System.out.println("1. Ver el tipo de cambio del dia");
                System.out.println("2. Convertir dolares a colones");
                System.out.println("3. Facturar");
                System.out.println("4. Ingresar o cambiar token");
                System.out.println("5. Salir");
                System.out.println("**************************************");
                int opcion = leerEntero("Opcion: ", 1, 5);
                try {
                    switch (opcion) {
                        case 1:
                            mostrarTipoCambio();
                            break;
                        case 2:
                            convertir();
                            break;
                        case 3:
                            facturar();
                            break;
                        case 4:
                            ingresarToken();
                            break;
                        case 5:
                            System.out.println("Hasta luego.");
                            return;
                        default:
                            throw new IllegalStateException("Opcion no reconocida");
                    }
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    System.out.println(ex.getMessage());
                }
            }
        } catch (NoSuchElementException ex) {
            System.out.println("\nFin de la entrada. Hasta luego.");
        }
    }

    private void ingresarToken() {
        if (servicioConfiguracion == null) {
            throw new IllegalStateException("La configuracion del token no esta disponible");
        }
        System.out.print("Pegue su token (Enter para cancelar): ");
        String token = entrada.nextLine();
        if (token.isBlank()) {
            System.out.println("Configuracion cancelada.");
            return;
        }
        servicioConfiguracion.guardarToken(token);
        System.out.println("Token guardado. Se utilizara en la proxima consulta.");
    }

    private void mostrarTipoCambio() {
        String fecha = LocalDate.now(ZoneId.of("America/Costa_Rica")).toString();
        TipoCambio tipoCambio = servicioTipoCambio.obtenerMasReciente();
        if (!fecha.equals(tipoCambio.getFecha())) {
            System.out.println("No hay publicacion para hoy. Ultimo tipo de cambio disponible:");
        }
        System.out.printf(Locale.ROOT, "%s  Compra: C %.2f  Venta: C %.2f%n",
                tipoCambio.getFecha(), tipoCambio.getCompra(), tipoCambio.getVenta());
    }

    private void convertir() {
        double usd = leerMonto("Monto en dolares $$$: ");
        int sentido = leerEntero("1. Para pagar\n2. Para recibir\nOpción: ", 1, 2);
        TipoCambio tipoCambio = servicioTipoCambio.obtenerMasReciente();
        double crc = sentido == 1
                ? servicioTipoCambio.convertirAColonesParaPagar(usd)
                : servicioTipoCambio.convertirAColonesParaRecibir(usd);
        System.out.printf(Locale.ROOT, "Resultado: C %,.2f%n", crc);
        System.out.printf("Fecha del tipo de cambio: %s%n", tipoCambio.getFecha());
    }

    private void facturar() {
        int cantidadLineas = leerEntero("Cantidad de lineas: ", 1, Integer.MAX_VALUE);
        List<LineaFactura> lineas = new ArrayList<>();
        for (int i = 0; i < cantidadLineas; i++) {
            System.out.println("Linea " + (i + 1));
            String descripcion = leerDescripcion();
            int cantidad = leerEntero("Cantidad: ", 1, Integer.MAX_VALUE);
            int moneda = leerEntero("Moneda (1. Colones / 2. Dolares): ", 1, 2);
            double precio = leerMonto("Precio unitario: ");
            lineas.add(servicioFacturacion.crearLinea(descripcion, cantidad, precio, moneda == 2));
        }
        Factura factura = servicioFacturacion.facturar(lineas);
        System.out.println();
        if (lineas.stream().anyMatch(LineaFactura::isEnDolares)) {
            System.out.printf("Fecha del tipo de cambio: %s%n",
                    servicioTipoCambio.obtenerMasReciente().getFecha());
        }
        for (LineaFactura linea : factura.getLineas()) {
            System.out.printf(Locale.ROOT, "%-20s %2d x %s %8.2f  ->  C %10.2f%n",
                    linea.getDescripcion(), linea.getCantidad(), linea.isEnDolares() ? "$" : "C",
                    linea.getPrecioUnitario(), linea.getImporteColones());
        }
        System.out.printf("%54s%n", "--------------");
        System.out.printf(Locale.ROOT, "%-41s C %10.2f%n", "SUBTOTAL", factura.getSubtotal());
        System.out.printf(Locale.ROOT, "%-41s C %10.2f%n", "IVA 13%", factura.getIva());
        System.out.printf(Locale.ROOT, "%-41s C %10.2f%n", "TOTAL", factura.getTotal());
    }

    private String leerDescripcion() {
        while (true) {
            System.out.print("Descripcion: ");
            String descripcion = entrada.nextLine().strip();
            if (!descripcion.isEmpty()) {
                return descripcion;
            }
            System.out.println("Ingrese una descripcion.");
        }
    }

    private int leerEntero(String mensaje, int minimo, int maximo) {
        while (true) {
            System.out.print(mensaje);
            try {
                int valor = Integer.parseInt(entrada.nextLine().strip());
                if (valor >= minimo && valor <= maximo) {
                    return valor;
                }
            } catch (NumberFormatException ex) {
                // Se vuelve a solicitar el dato.
            }
            System.out.println("Ingrese un entero entre " + minimo + " y " + maximo + ".");
        }
    }

    private double leerMonto(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                double valor = Double.parseDouble(entrada.nextLine().strip().replace(',', '.'));
                if (Double.isFinite(valor) && valor >= 0) {
                    return valor;
                }
            } catch (NumberFormatException ex) {
                // Se vuelve a solicitar el dato.
            }
            System.out.println("Ingrese un monto no negativo, sin separadores de miles.");
        }
    }
}
