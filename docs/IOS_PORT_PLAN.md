# Estado del port iOS

La aplicación iOS es nativa en SwiftUI y comparte Firebase/Firestore y Cloudinary con Android. Esta rama parte del port iOS y conserva intactas las ramas de Android y del port original.

## Funcionalidad implementada en iOS

- Autenticación por correo y Google; creación de perfiles y reserva de nombre compatibles con las reglas de Firestore.
- Perfiles de mascotas, selección sincronizada, edición transaccional de perfiles propios, mascotas compartidas e invitaciones.
- Pasaporte sanitario con vacunas, tratamientos, clínicas, dosis, estado, próxima fecha y recordatorio.
- Registro de peso a partir de las entradas de tipo Peso.
- Rutinas con responsable, frecuencia, dosis, hora, fecha, acción y selección de días semanales.
- Historial de cuidados con autor, acción, distancia, notas y hasta cuatro fotografías.
- Diario sincronizado por mascota, con editor multimedia más sencillo que el de Android.
- Calendario mensual que reúne agenda, próximas dosis, rutinas y recuerdos.
- Gastos sincronizados por mascota, con totales mensuales y desglose por categorías.
- Copias versionadas de perfil, mascotas propias, pasaporte, rutinas, agenda, diario, gastos, favoritos y recordatorios locales.
- Explorar con MapKit y favoritos sincronizados en Firestore, conservando caché local.
- Perfil profesional, categorías, ubicación, descripción y contacto privado con publicación voluntaria.
- Directorio que combina perfiles profesionales de Pawly y resultados de Apple Maps.
- Comunidad: publicaciones, imágenes/vídeos, comentarios, reacciones, búsqueda, mensajería, lectura, bloqueo, denuncias y gestión de usuarios bloqueados.
- Avisos de actividad en Firestore y llamada al Worker para notificaciones push cuando `PAWLY_BACKEND_URL` está configurada.
- Índice de propiedad de las subidas Cloudinary para identificar archivos de cada cuenta.
- Agenda con recordatorios locales y registro APNs/FCM.
- Guías, noticias RSS, Pawly Market, avisos de mascota perdida como publicación de Comunidad y exportación PDF.

## Pendiente o condicionado a servicios externos

- **Activación real de servicios:** registrar `com.pawly.ios` en Firebase, instalar el `GoogleService-Info.plist` local, configurar OAuth, Cloudinary, APNs y desplegar el Worker. Véase `docs/IOS_SETUP.md`.
- **Push real:** depende de que el propietario despliegue el Worker de Cloudflare y suba una clave APNs a Firebase. Sin eso, los avisos permanecen en Firestore y no se entregan al dispositivo.
- **Eliminación integral de cuenta:** por ahora se registra una solicitud. Falta el endpoint fiable que elimine Authentication, subcolecciones Firestore, conversaciones, referencias compartidas y recursos Cloudinary. No presentar la eliminación como completada.
- **Diario:** el editor iOS ya sincroniza texto y fotografías, pero aún no reproduce todas las herramientas multimedia y de edición de Android.
- **Bloqueo y contadores:** el filtrado en ambos sentidos, la lista y el desbloqueo están disponibles. Las reglas actuales no permiten que el cliente ajuste el contador de seguidores del otro perfil en todos los casos al bloquear; ese contador necesita una reparación confiable de backend.
- **Restauración de copias:** los registros de actividad de cuidados se exportan para consulta pero no se recrean, para no cambiar su autoría. Los medios conservan referencias Cloudinary; los recursos antiguos que no tengan índice pueden requerir limpieza manual.
- **Localización:** el idioma se puede seleccionar, pero la traducción integral aún está pendiente.
- **Pruebas en dispositivo:** el simulador no puede validar por sí solo APNs, Google OAuth, subida real a Cloudinary, permisos ni la interacción completa con Firebase.
- **Pruebas automatizadas:** falta una suite Swift de integración que cubra los flujos reales de autenticación, reglas, compartición, comunidad y restauración.

## Abrir y compilar

Se necesita macOS con Xcode y XcodeGen. Desde `ios/`:

```sh
xcodegen generate
xcodebuild -resolvePackageDependencies -project Pawly.xcodeproj -scheme Pawly
xcodebuild -project Pawly.xcodeproj -scheme Pawly -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Añade localmente `ios/Pawly/GoogleService-Info.plist`; no se versiona. Define los valores OAuth, Cloudinary y `PAWLY_BACKEND_URL` en `ios/project.yml`. No incluyas claves privadas ni API secrets en la app.

La CI compila el simulador en macOS. La compilación correcta no sustituye la prueba en un iPhone con Firebase, APNs y Cloudinary configurados.
