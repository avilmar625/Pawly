# Cambios 62 — endurecimiento de conversaciones

- Al crear una conversación, los nombres y las fotos de ambos participantes deben coincidir con sus perfiles reales de Firestore. Evita que un cliente suplante el nombre o avatar de la otra persona en la cabecera del chat.
- Se exige que ambos perfiles existan y que los mapas de participantes tengan exactamente las dos claves esperadas.
- La previsualización del último mensaje se limita a 120 caracteres y la fecha de actualización debe ser la hora de servidor.
- Los recibos de lectura dejan de poder actualizarse cuando existe un bloqueo en cualquiera de los sentidos.
- Se mantienen las reglas de pertenencia, correo verificado y bloqueo ya existentes para enviar y leer mensajes.

## Comprobación pendiente
Estas reglas deben probarse en Firebase Emulator Suite con cuentas de prueba (creación de chat, nombre/foto manipulados, conversación bloqueada, mensajes y recibos de lectura) antes de desplegarlas en el proyecto de producción.
