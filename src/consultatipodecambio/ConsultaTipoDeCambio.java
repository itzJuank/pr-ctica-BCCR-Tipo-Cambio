package consultatipodecambio;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;


/**
 *
 * @author juank
 */
public class ConsultaTipoDeCambio {

    public static String consultar(int indicador, LocalDate fecha)
            throws IOException, InterruptedException {

        return new sifac.servidor.datos.ConsultaTipoDeCambio()
                .consultarSerie(indicador, fecha, fecha);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Costa_Rica"));

        System.out.println("Compra: " + consultar(317, hoy));
        System.out.println("Venta: " + consultar(318, hoy));
    }
}
    
