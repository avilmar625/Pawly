# Estado del port iOS

La app iOS nativa vive en `ios/` y se desarrolla en ramas de trabajo basadas en `codex/streamlined-navigation-2026-11`. Comparte Firebase/Firestore y Cloudinary con Android; la interfaz está implementada en SwiftUI.

## Funcionalidad disponible

- Inicio de sesión por correo y Google, registro, recuperación y verificación de correo.
- Perfiles de mascotas, selección, edición, fotografías y acceso compartido mediante invitaciones.
- Pasaporte sanitario, rutinas de cuidado y agenda sincronizada con Firestore.
- Diario sincronizado por mascota, con un editor más sencillo que el de Android.
- Comunidad: publicaciones, fotos/vídeos, comentarios, reacciones, búsqueda de usuarios y mensajería con indicadores de lectura.
- Avisos de actividad en Firestore para «me gusta», comentarios y nuevos seguidores.
- Bloqueo en ambos sentidos, denuncias y filtrado de usuarios bloqueados en el muro, búsqueda y chats.
- Explorar con MapKit, búsqueda de lugares y favoritos locales.
- Guías, noticias RSS, Pawly Market y exportación PDF.
- Notificaciones locales y registro de APNs cuando el usuario ha autorizado avisos.

## Pendiente para alcanzar paridad con Android

- Salud: falta el historial completo de peso y mediciones.
- Agenda: falta el calendario mensual que reúne agenda, salud y recuerdos.
- Gastos: falta el registro de gastos y su resumen mensual por mascota.
- Copias de seguridad: falta exportar y restaurar integralmente perfiles, mascotas, recuerdos y datos asociados.
- Explorar: los favoritos siguen siendo locales; no se sincronizan como las colecciones de Android.
- Profesionales: iOS busca en Apple Maps, pero todavía no muestra los perfiles profesionales publicados en Comunidad.
- Diario: faltan campos y herramientas multimedia del editor Android.
- Rutinas: el modelo de Firestore ya incluye dosis, hora, fecha y tipo de acción; falta completar la interfaz de planificación semanal con días concretos.
- Bloqueos: el filtrado en ambos sentidos está implementado, pero falta una pantalla completa para gestionar la lista y desbloquear usuarios. Además, las reglas actuales no autorizan al cliente a corregir el contador de seguidores cuando bloquea a alguien que ya le seguía; requiere una operación confiable de backend o un ajuste específico de reglas.
- Avisos push: iOS puede registrar tokens APNs/FCM y guardar avisos de actividad en Firestore, pero el Worker de Cloudflare que envía push todavía no está desplegado (véase `backend/cloudflare-worker/README.md`). Los avisos tampoco tienen aún navegación profunda al tocar la notificación.
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
