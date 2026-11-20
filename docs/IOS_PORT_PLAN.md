# Estado del port iOS

La app iOS nativa vive en `ios/` y se desarrolla en ramas de trabajo basadas en `codex/streamlined-navigation-2026-11`. Comparte el proyecto Firebase/Firestore y Cloudinary con Android; la interfaz está implementada en SwiftUI y no reutiliza Compose.

## Port funcional existente

- Inicio de sesión por correo y Google, recuperación y verificación de correo.
- Perfiles de mascotas, selección de mascota, edición, foto y acceso compartido.
- Pasaporte sanitario y rutinas de cuidado.
- Diario sincronizado en Firestore por mascota.
- Comunidad: publicaciones, multimedia, comentarios, reacciones, búsqueda de personas, mensajería, bloqueo y denuncias. El compositor admite categorías de historia, adopción, perdida, encontrada y acogida.
- Explorar con MapKit, búsqueda de lugares y favoritos locales.
- Guías y noticias RSS.
- Agenda sincronizada en Firestore y notificaciones locales de iOS, ajustes y perfil público.
- Pawly Market con enlaces a búsqueda externa, búsqueda profesional en Apple Maps y publicación guiada de mascota perdida desde Comunidad.
- Exportación PDF.

## Diferencias conocidas frente a Android

- Las reglas de repetición de avisos se programan localmente en iOS; el calendario compartido guarda título, fecha y categoría como Android.
- La lista de favoritos de Explorar se guarda localmente en iOS.
- La búsqueda profesional iOS usa Apple Maps; todavía no lista los perfiles profesionales publicados en Comunidad.
- Salud: falta el seguimiento de peso/mediciones y la experiencia de salud completa que existe en Android.
- Agenda: iOS ofrece una lista de recordatorios, pero no el calendario mensual que agrega agenda, salud y recuerdos en Android.
- Gastos: falta el registro y resumen mensual de gastos de cada mascota.
- Copias de seguridad: falta exportación/restauración integral del perfil y sus datos.
- Explorar: los favoritos se guardan en el dispositivo; no se sincronizan como las colecciones de Android.
- Profesionales: iOS busca en Apple Maps, pero no consulta los perfiles profesionales publicados por usuarios de Pawly.
- Diario: el contenido se sincroniza, pero el editor iOS es más simple y no reproduce todos los campos multimedia de Android.
- La solicitud de eliminación de cuenta se registra en Firestore; el borrado real de Authentication y de los medios requiere un backend de eliminación desplegado.
- La localización está solo parcialmente traducida.
- El diario iOS comparte contenido, pero su editor y estructura de datos son más simples que los recuerdos multimedia de Android.

## Abrir y compilar

Se necesita macOS con Xcode y XcodeGen. Desde `ios/`:

```sh
xcodegen generate
xcodebuild -resolvePackageDependencies -project Pawly.xcodeproj -scheme Pawly
xcodebuild -project Pawly.xcodeproj -scheme Pawly -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Añade `GoogleService-Info.plist` de la app iOS registrada en Firebase a `ios/Pawly/` localmente. No se debe versionar configuración privada de Firebase. Define los valores OAuth de Google y Cloudinary en la configuración de compilación del target.

La CI de GitHub compila el simulador en macOS en las ramas de trabajo. Todavía no existe una suite de pruebas automatizadas Swift que valide la paridad de comportamiento. Las funciones que requieren APNs, credenciales de Firebase y Cloudinary deben verificarse además en un dispositivo configurado.
