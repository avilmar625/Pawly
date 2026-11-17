# Fase 3 — Pawly Market, cuidados y servicios

Rama de trabajo: `cambios79-fase3-bienestar-market`
Base exacta: `cambios78-pawly-market`
Commit de partida: `6acdb298ce741412c2d8d469f040434c0e21e043`

## Alcance incorporado

- **Perfiles verificados:** el modelo `PawlyUser.isVerified` ya existía y la insignia azul `PawlyVerifiedBadge` ya se mostraba en perfiles propios y resultados de personas. Se conserva: el cliente no puede conceder la verificación; debe proceder de moderación.
- **Pawly Market:** nueva entrada desde Más y nueva pantalla con categorías de alimentación, comida húmeda, snacks, juguetes, paseo, higiene y descanso. Los enlaces abren búsquedas externas de productos; Pawly no procesa pagos, no mantiene stock y no atribuye recomendaciones comerciales como verificadas.
- **Alimentación, higiene y gastos:** registro de concepto, detalle/frecuencia y coste opcional. Resumen de gasto y eliminación de entradas.
- **Ficha de emergencia:** contacto, teléfono, veterinario habitual, alergias, medicación, microchip y notas. Permite compartir una ficha textual junto con los registros.
- **Educación y comportamiento:** guías introductorias de refuerzo positivo, llamada, paseo, enriquecimiento, miedo/reactividad e higiene cooperativa.
- **Mapa inteligente:** búsquedas externas por zona para parques, playas, veterinarios, tiendas y peluquerías; acceso al directorio profesional Pawly existente.

## Límites funcionales explícitos

- Los nuevos registros de gastos/cuidados y los datos de emergencia se guardan en preferencias locales, separados por mascota y usuario autenticado cuando existe ámbito de almacenamiento. **No están sincronizados entre dispositivos ni incluidos aún en el backup JSON general.**
- El informe compartido es un resumen de datos aportados por el propietario, no una historia clínica veterinaria certificada. Los informes oficiales deben proceder de la clínica.
- Market usa búsquedas externas, no un catálogo de afiliación integrado. No hay carrito, pagos, precios ni disponibilidad sincronizados.
- El mapa abre resultados de Google Maps mediante consulta por zona; no es todavía un mapa nativo con geolocalización, filtros geoespaciales ni reservas.
- El directorio profesional existente se mantiene separado y no se declara que las reservas estén integradas. El contacto/reserva depende del proveedor externo.
- La verificación sigue siendo una decisión de moderación. Nunca se debe permitir que un usuario edite `isVerified` desde la aplicación.

## Validación pendiente

- Ejecutar en CI sobre el HEAD final: `testDebugUnitTest`, `lintDebug`, `assembleDebug` y `bundleRelease`.
- Probar navegación Más → Pawly Market → directorio profesional y regreso.
- Probar persistencia local al cerrar/abrir, cambio de mascota y cambio de cuenta; verificar aislamiento de datos.
- Probar registro/edición/eliminación de gastos, importe decimal y ficha de emergencia vacía/completa.
- Probar compartir informe y apertura de enlaces externos sin aplicación de destino.
- Revisar accesibilidad, traducciones y tema oscuro.
- No declarar compilación ni funcionamiento extremo a extremo hasta completar CI y pruebas manuales.

## Costes y seguridad

No se añaden servicios facturables, Firebase Storage, Blaze, Workers Paid, APIs de pago ni nuevas credenciales. No se modifican reglas Firestore ni se expone información privada de contacto en perfiles públicos.
