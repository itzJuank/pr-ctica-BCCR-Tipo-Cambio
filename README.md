# Tipo de cambio BCCR - SIFAC

Programa de consola en Java para consultar el tipo de cambio del BCCR, convertir
dólares a colones y generar facturas con productos cotizados en ambas monedas.
Está organizado en cuatro capas: entidades, datos, lógica y presentación.

## Funciones

1. Ver las tasas de compra y venta de la última publicación disponible.
2. Convertir dólares a colones: venta para pagar y compra para recibir.
3. Crear una factura mixta con subtotal, IVA del 13 % y total en colones.
4. Ingresar o cambiar el token del BCCR.
5. Salir.

## Cómo ejecutarlo

Requiere **JDK 26 o superior**. Abra el proyecto en NetBeans y ejecútelo.
También puede compilar con Ant, usando el mismo JDK:

```sh
ant jar
java -jar dist/ConsultaTipoDeCambio.jar
```

Ejecute desde la raíz del proyecto y conserve la carpeta `dist/lib` junto al JAR.

En la opción **4**, ingrese su token del BCCR. Se guardará en `bccr.properties`
para las próximas ejecuciones. Ese archivo está excluido de Git. Como alternativa,
puede usar `BCCR_TOKEN` si no tiene un token guardado. La aplicación conserva la
fecha real de la tasa y muestra errores si falla la consulta.

## Pruebas

```sh
ant verificar-tipo-cambio
```

Las pruebas no requieren internet ni un token real y comprueban consultas,
conversiones, configuración del token y facturación.
