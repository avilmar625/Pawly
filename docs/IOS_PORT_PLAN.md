# Estado del port iOS

La app iOS nativa vive en `ios/` y se desarrolla en ramas de trabajo basadas en `codex/streamlined-navigation-2026-11`. Comparte Firebase/Firestore y Cloudinary con Android; la interfaz está implementada en SwiftUI.

## Funcionalidad disponible

- Inicio de sesión por correo y Google, registro, recuperación y verificación de correo.
- Perfiles de mascotas, selección, edición, fotografías y acceso compartido mediante invitaciones.
- Pasaporte sanitario, rutinas de cuidado y agenda sincronizada con Firestore.
- Diario sincronizado por mascota, con un editor más sencillo que el de Android.
- Comunidad: publicaciones, fotos/vídeos, comentarios, reacciones, búsqueda de usuarios y mensajería con indicadores de lectura.
- Avisos de actividad en Firestore para «me gusta», comentarios y nuevos seguidores; envío push al Worker configurado.
- Bloqueo en ambos sentidos, denuncias y filtrado de usuarios bloqueados en el muro, búsqueda y chats.
- Explorar con MapKit, búsqueda de lugares y favoritos sincronizados mediante `users/{uid}/exploreCollections/ios-favourites`.
- Guías, noticias RSS, Pawly Market y exportación PDF.
- Notificaciones locales y registro de APNs cuando el usuario ha autorizado avisos.

## Pendiente para alcanzar paridad con Android

- Salud: pasaporte ampliado con producto, dosis, frecuencia, clínica, estado, próxima dosis y avisos; el gráfico de peso se obtiene de los registros tipo Peso.
- Agenda: calendario mensual que reúne agenda, próximas dosis, rutinas y recuerdos.
- Gastos: registro sincronizado y resumen mensual por mascota.
- Copias de seguridad: exportación/restauración versionada de los perfiles, datos sanitarios, rutinas, agenda, diario, gastos y favoritos. Los registros de actividad no se reescriben al restaurar para no suplantar su autoría; fotos y vídeos conservan sus referencias de Cloudinary.
- Explorar: los favoritos se sincronizan con la cuenta en Firestore y se mantienen en caché local.
- Profesionales: alta de cuenta profesional, categorías, ubicación, descripción y contacto privado con opción explícita de publicación; el directorio consulta perfiles de Pawly y Apple Maps.
- Diario: faltan campos y herramientas multimedia del editor Android.
- Rutinas: el editor incluye dosis, hora, fecha, tipo de acción y selector de días semanales.
- Bloqueos: el filtrado en ambos sentidos está implementado, pero falta una pantalla completa para gestionar la lista y desbloquear usuarios. Además, las reglas actuales no autorizan al cliente a corregir el contador de seguidores cuando bloquea a alguien que ya le seguía; requiere una operación confiable de backend o un ajuste específico de reglas.
- Avisos push: iOS registra tokens APNs/FCM, guarda avisos en Firestore y llama al endpoint `/v1/activity-notifications` del Worker cuando `PAWLY_BACKEND_URL` está configurada. El Worker de Cloudflare todavía debe desplegarse y configurarse (véase `backend/cloudflare-worker/README.md`). Los avisos aún no tienen navegación profunda al tocarlos.
- Eliminación de cuenta: la app registra una solicitud, pero el borrado real de Authentication, subcolecciones Firestore y recursos Cloudinary aún requiere un backend desplegado.
- La localización todavía está parcialmente traducida.
- El entitlement APNs del proyecto está configurado para desarrollo; la distribución necesitará el entitlement de producción y perfiles de firma adecuados.
- No existe todavía una suite automatizada de pruebas Swift que cubra los flujos de autenticación, reglas de Firestore, compartición y comunidad.

## Abrir y compilar

Se necesita macOS con Xcode y XcodeGen. Desde `ios/`:

```sh
xcodegen generate
xcodebuild -resolvePackageDependencies -project Pawly.xcodeproj -scheme Pawly
xcodebuild -project Pawly.xcodeproj -scheme Pawly -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Añade localmente el `GoogleService-Info.plist` de la app iOS registrada en Firebase a `ios/Pawly/`. No se debe versionar configuración privada de Firebase. Define los valores OAuth de Google y Cloudinary en la configuración de compilación del target.

La CI de GitHub compila el simulador en macOS en las ramas de trabajo. Las funciones que requieren APNs, credenciales de Firebase y Cloudinary deben probarse además en un dispositivo configurado.
