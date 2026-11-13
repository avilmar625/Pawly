# Cambios 56 — revisión de seguridad y fiabilidad

Rama: `cambios56-arquitectura`  
Base: `cambios55`

## Cambios implementados en esta iteración

### 1. Notificaciones de actividad verificadas por Firestore

- Se elimina la posibilidad de que un usuario escriba texto libre y suplante el nombre de otra persona en la bandeja ajena.
- Las reglas verifican que:
  - Un LIKE corresponde a una publicación del destinatario y el actor figura realmente en `likedBy`.
  - Un COMMENT corresponde al último comentario registrado en la publicación y el documento de comentario pertenece al actor.
  - Un FOLLOW tiene un documento real en `users/{destinatario}/followers/{actor}`.
  - No existe un bloqueo en ninguno de los dos sentidos.
  - El nombre del actor coincide con su perfil y el mensaje es uno de los textos fijos permitidos.
- Los identificadores de notificación son deterministas para evitar crear múltiples avisos para la misma interacción.
- Un fallo al crear el aviso no convierte un like, comentario o follow ya confirmado en un falso error de la operación principal.

**Límite:** esto protege la bandeja dentro de Firestore. No envía push. Para enviar push hace falta un emisor de confianza, que se implementará en una fase separada con Cloudflare Workers y secretos configurados fuera de Git.

### 2. Restauración de recordatorios tras reinicio

- Las alarmas guardan sus datos mínimos en SharedPreferences.
- Se añade un receptor para `BOOT_COMPLETED` y `MY_PACKAGE_REPLACED`.
- Al recibir esos eventos, Pawly vuelve a programar las alarmas futuras y descarta entradas inválidas o caducadas.
- Cancelar un recordatorio elimina también su registro persistido.

Los recordatorios siguen siendo locales al dispositivo. No se sincronizan entre Android e iOS y se pierden si se borran los datos de la aplicación o se desinstala.

### 3. Fechas estructuradas para eventos

- Los eventos nuevos conservan `dateText` para compatibilidad visual y añaden `startAt` (Timestamp Firestore) y `timeZoneId`.
- La fecha se valida estrictamente como `DD/MM/AAAA HH:mm` (también admite el separador visual `·`).
- No se permiten fechas pasadas.
- La lista ordena los eventos cargados por su hora de inicio. Los eventos antiguos sin `startAt` quedan al final y mantienen su texto histórico.

### 4. Integración del mapa

- Se añade Subresource Integrity (SRI) a Leaflet 1.9.4.
- Se aplica una Content Security Policy limitada a Leaflet y a los recursos de teselas.
- El WebView utiliza un User-Agent identificable de Pawly.
- Se deja de fingir que el documento se ha cargado desde `openstreetmap.org`; el origen base es `pawly.invalid`.

El mapa sigue usando el servicio público de teselas de OpenStreetMap. Antes de escalar la aplicación habrá que revisar su política de uso, volumen de tráfico y, si es necesario, contratar o adoptar un proveedor de teselas adecuado. El puente JavaScript del selector sigue existiendo y solo expone la selección de coordenadas; SRI/CSP reducen, pero no eliminan, el riesgo de cargar código remoto.

## Despliegue obligatorio de reglas

Las reglas de Firestore no se publican automáticamente desde GitHub. Antes de probar las notificaciones, hay que desplegar las reglas de esta rama en el proyecto correcto:

```bash
firebase use
firebase deploy --only firestore:rules
```

Confirma que el proyecto activo es `pawly-83dca`. No actives Blaze ni habilites facturación para este despliegue.

## Pendiente — no declarar listo para producción

1. **Backend de confianza y push:** no se incluye aún un Worker desplegado. No hay claves de servicio ni secretos en el repositorio. El emisor FCM y las operaciones administrativas deben implementarse y probarse con Cloudflare Free.
2. **Eliminación real de cuenta:** la aplicación sigue creando una solicitud pendiente. No se ha implementado la eliminación recursiva de Firestore/Auth ni la eliminación de medios Cloudinary. Sigue siendo un bloqueante de publicación.
3. **Traducciones:** siguen centralizadas en mapas Kotlin. La migración a recursos Android por idioma y pluralización está pendiente.
4. **Profesionales:** el perfil profesional sigue siendo autodeclarado. No mostrar como verificado hasta implantar una comprobación real y un proceso de revisión.
5. **Denuncias de eventos:** no se ha añadido todavía el flujo de denuncia/moderación de eventos.
6. **Arquitectura:** `FirebaseCommunityRepository` y `PawlyApp` siguen necesitando extracción progresiva en repositorios, casos de uso y ViewModels.
7. **Pruebas:** faltan pruebas con Firebase Emulator Suite para las reglas y pruebas de integración para recordatorios, notificaciones y borrado.
8. **Publicación de reglas:** sigue siendo un despliegue manual; la automatización debe añadirse solo cuando exista un entorno seguro de CI y secretos apropiados.

## Verificación

La rama debe pasar:
- `testDebugUnitTest`
- `lintDebug`
- `assembleDebug`
- `bundleRelease` (sin firma)

No considerar esta rama lista para publicar hasta completar y verificar los puntos bloqueantes anteriores.
