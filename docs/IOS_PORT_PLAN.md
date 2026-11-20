# Estado del port iOS

La aplicación iOS es nativa en SwiftUI y comparte Firebase/Firestore y Cloudinary con Android. El trabajo de paridad de esta rama está en `codex/ios-auth-push-hardening-2026-11`, basada en el port iOS y sin modificar las ramas de Android ni el port original.

## Funcionalidad implementada en iOS

- Autenticación con correo/contraseña y Google, registro, reserva de nombre, recuperación de contraseña y verificación por correo.
- Perfiles de mascotas, selección sincronizada, edición, fotografías e invitaciones para compartir.
- Pasaporte sanitario con producto, dosis, frecuencia, clínica, estado, próxima dosis y hora de aviso.
- Avisos locales para próximas dosis y revisiones cuando el usuario activa «Recordarme».
- Registro de peso a partir de las entradas del tipo Peso.
- Rutinas de cuidado con responsable, frecuencia, dosis, hora, fecha, acción y selección de días semanales.
- Historial de cuidados con autor, acción, distancia, notas y hasta cuatro fotografías.
- Diario sincronizado por mascota con título, texto, categoría, fecha y hasta cuatro fotografías por recuerdo.
- Calendario mensual que reúne agenda, próximas dosis, rutinas y recuerdos.
- Gastos por mascota con resumen mensual y desglose por categorías. La implementación iOS se sincroniza mediante Firestore; la pantalla Android actual continúa siendo local.
- Copias de seguridad JSON versionadas con perfiles, mascotas, pasaporte, rutinas, agenda, diario, gastos, colecciones de Explorar, ajustes profesionales y recordatorios locales.
- Explorar con MapKit y favoritos guardados en Firestore mediante transacciones, conservando caché local.
- Perfil profesional con categorías, ubicación, descripción y contacto privado que solo se muestra con consentimiento explícito.
- Directorio de profesionales de Pawly combinado con resultados de Apple Maps.
- Comunidad con publicaciones de imagen/vídeo, comentarios, reacciones, búsqueda por nombre/usuario, mensajería e indicadores de lectura.
- Lugares propuestos por la comunidad: listado de aprobados, envío limitado a una propuesta diaria, edición/retirada de propuestas propias y revisión previa.
- Centro de moderación protegido por claim `admin`: revisión de propuestas y denuncias, restricción de perfiles y retirada de eventos, lugares y publicaciones con limpieza multimedia cuando los recursos están indexados y el Worker está configurado.
- Denuncias, bloqueo en ambos sentidos, lista de usuarios bloqueados y desbloqueo.
- Eventos y quedadas: creación con fecha/hora, categoría y lugar, marcar/desmarcar interés, abrir ubicación en Mapas, denunciar y consulta paginada de asistentes para quien organiza.
- Mascotas perdidas: publicación en Comunidad, localización de la zona del aviso, suscripción voluntaria a avisos por proximidad con radio de 5–100 km y envío de notificaciones a través del Worker. La ubicación se redondea a una celda aproximada de 1 km.
- Notificaciones locales y registro de tokens APNs/FCM; envío de notificaciones de actividad al Worker cuando `PAWLY_BACKEND_URL` está configurada.
- Índice de propietario de las nuevas subidas de Cloudinary, con `public_id` estable y tipo de recurso.
- Guías, noticias RSS, Pawly Market y exportación PDF.

## Pendiente o condicionado a servicios externos

- **Activación Firebase:** registrar `com.pawly.ios` en el proyecto `pawly-83dca`, añadir el `GoogleService-Info.plist` local y configurar OAuth. No se debe crear otro proyecto Firebase.
- **Cloudinary:** configurar el cloud name y presets unsigned para imágenes/vídeos. Las claves privadas no se incluyen en la app. Las subidas anteriores a la creación del índice `cloudinaryAssets` pueden requerir limpieza manual.
- **Push real:** requiere subir una clave APNs a Firebase Cloud Messaging, habilitar Push Notifications en el App ID y desplegar el Worker. Sin ello, los avisos quedan disponibles en Firestore pero no se entregan como notificación push.
- **Eliminación integral de cuenta:** la app registra una solicitud, pero el Worker aún no ejecuta la cascada de borrado de Firebase Authentication, subcolecciones Firestore, contenido compartido y recursos Cloudinary. No afirmar que la solicitud haya borrado la cuenta.
- **Navegación al pulsar un push:** iOS muestra las notificaciones, pero todavía no enruta automáticamente al post, conversación o evento relacionado.
- **Cartel compartible de mascota perdida:** iOS publica el aviso y envía alertas geográficas con consentimiento, pero todavía no genera el cartel JPG local que Android puede compartir por Mensajes/WhatsApp.
- **Búsqueda por contactos/teléfono:** el Worker tiene endpoints privados de descubrimiento, pero la integración cliente actual está implementada en Android mediante Phone Number Verification; iOS no consulta ni sube la agenda y necesita una solución de verificación compatible antes de portar esa función.
- **Diario:** el editor iOS permite título, texto, categoría, fecha y hasta cuatro fotografías. Android admite hasta ocho y dispone de herramientas de selección/edición que aún no están en iOS.
- **Búsqueda por teléfono:** no está portada. Android usa Firebase Phone Number Verification (PNV), cuya guía oficial actual documenta el SDK de Android y describe el servicio como facturado. No se ha sustituido por SMS en iOS para no introducir cargos sin decisión explícita. La búsqueda por nombre y usuario sí está disponible.
- **Eliminación de mascotas compartidas:** iOS bloquea la eliminación de un perfil que ya está compartido para evitar dejar miembros con un perfil huérfano. Hace falta una pantalla completa para revocar miembros y cerrar el espacio compartido antes de habilitar ese borrado.
- **Bloqueo y contadores:** filtrado, denuncias, listado y desbloqueo están disponibles. La corrección de algunos contadores de seguidores requiere una operación confiable de backend o ajustes coordinados de reglas.
- **Restauración de copias:** los registros de auditoría de cuidados se incluyen en el archivo, pero no se recrean para no suplantar su autoría. Se restaura contenido recuperable de la cuenta y mascotas que administra el propietario; no se sobrescriben mascotas compartidas de terceros.
- **Moderación de publicaciones y medios antiguos:** la retirada multimedia utiliza el índice `cloudinaryAssets`. Los recursos anteriores a la creación del índice pueden requerir limpieza manual; si no están indexados o el Worker no está configurado, la app conserva la publicación y muestra el error en vez de dar el borrado por hecho.
- **Localización:** está disponible el selector de idioma, pero la traducción integral de todas las pantallas aún está pendiente.
- **Pruebas funcionales en dispositivo:** el simulador no puede validar APNs real, OAuth de Google, permisos del sistema, subida de Cloudinary y todos los flujos Firebase. Debe hacerse una prueba en iPhone tras configurar las cuentas.
- **Pruebas automatizadas:** falta una suite Swift de integración que cubra autenticación, reglas, compartición, comunidad, notificaciones y restauración.

## Configuración externa

Consulta `docs/IOS_SETUP.md` para los pasos de Firebase, Cloudinary, APNs y Cloudflare, incluidos el bundle ID, los nombres de los ajustes y los secretos que deben guardarse en Cloudflare.

## Abrir y compilar

Se necesita macOS con Xcode y XcodeGen. Desde `ios/`:

```sh
xcodegen generate
xcodebuild -resolvePackageDependencies -project Pawly.xcodeproj -scheme Pawly
xcodebuild -project Pawly.xcodeproj -scheme Pawly -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Añade localmente `ios/Pawly/GoogleService-Info.plist`; no se versiona. Define los valores OAuth, Cloudinary y `PAWLY_BACKEND_URL` en la configuración del proyecto. No incluyas claves privadas ni API secrets en la app.

La CI de esta rama está configurada para compilar el simulador en macOS. Una compilación correcta no sustituye la prueba en un iPhone con Firebase, APNs y Cloudinary configurados.
