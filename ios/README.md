# Pawly para iOS

Port nativo para iPhone con SwiftUI. Android permanece intacto en `app/`. La app iOS utiliza el mismo proyecto Firebase para compartir cuentas y datos compatibles.

## Funciones implementadas en esta rama

- Firebase Authentication: acceso con correo y contraseña, registro, verificación y recuperación.
- Sesión persistente.
- Perfiles de mascotas sincronizados con `users/{uid}.dogProfiles`, con selector persistente entre aperturas.
- Pasaporte sanitario: alta, consulta y borrado de vacunas, revisiones y tratamientos.
- Rutinas de cuidado: alta, consulta, marcar como completadas y borrar.
- Diario compartido por mascota en `sharedPets/{ownerUid}_{petId}/diary`, con alta, consulta y borrado.
- Comunidad: muro en tiempo real, publicaciones de texto por categoría, likes y comentarios.
- Mensajería privada en tiempo real: iniciar chat por nombre de usuario, mensajes de texto/foto/vídeo y confirmación de lectura.
- Publicaciones con texto, foto o vídeo; las imágenes se comprimen y los archivos se suben a Cloudinary mediante presets unsigned.
- Cierre de sesión.

## Funciones aún pendientes

Esta rama amplía la primera base, pero todavía no es una paridad total con Android. Faltan:
- Mejoras de edición de medios, reproducción en segundo plano y gestión/borrado de archivos remotos en Cloudinary.
- Búsqueda de perfiles, seguir/dejar de seguir, perfiles públicos y bloqueo/denuncia desde iOS.
- Invitaciones y administración de perfiles de mascotas compartidos.
- Notificaciones push entrantes: registrar APNs/FCM en iOS y disponer de un emisor confiable en backend.
- Sincronización de las preferencias y recordatorios locales, exportación PDF y eliminación integral de cuenta/datos.
- Pruebas de extremo a extremo con dos cuentas Firebase reales.

## Requisitos

- macOS y Xcode 16 o posterior.
- Homebrew y XcodeGen (`brew install xcodegen`).
- Proyecto Firebase actual de Pawly.

## Configurar Firebase iOS

1. En Firebase Console, dentro del proyecto que ya utiliza Android, registra una aplicación iOS.
2. Identificador de paquete: `com.pawly.ios`.
3. Descarga el archivo específico de iOS `GoogleService-Info.plist`.
4. Colócalo en `ios/Pawly/GoogleService-Info.plist`. No reutilices el `google-services.json` de Android.
5. Mantén Email/Password habilitado en Authentication. Las cuentas existentes de Firebase podrán iniciar sesión en iOS.

El plist está excluido de Git para no publicar configuración privada del proyecto.

## Generar y ejecutar

Desde la raíz del repositorio:

```bash
cd ios
xcodegen generate
open Pawly.xcodeproj
```

En Xcode selecciona un simulador o conecta el iPhone, configura tu equipo en Signing & Capabilities y pulsa Run. Para compilar sin firma:

```bash
xcodebuild -project ios/Pawly.xcodeproj -scheme Pawly -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## Configurar Cloudinary para iOS

La app usa el mismo Cloudinary que Android. En Xcode, selecciona el target Pawly y añade estos valores en Build Settings (User-Defined), o completa las claves vacías equivalentes en `ios/project.yml` y vuelve a ejecutar `xcodegen generate`:

- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_IMAGE_UPLOAD_PRESET`
- `CLOUDINARY_VIDEO_UPLOAD_PRESET`

Utiliza los mismos unsigned presets que Android. No incluyas nunca el API Secret de Cloudinary en la app. Las fotos se limitan a 8 MB tras compresión y los vídeos a 40 MB.

## Compatibilidad de datos

- Los perfiles se leen/escriben en `users/{uid}.dogProfiles`.
- El pasaporte y las rutinas privadas siguen las rutas Android `users/{uid}/petCare/{petId}/passport` y `tasks`.
- El diario se sincroniza en el perfil compartido `sharedPets/{uid}_{petId}/diary`. Para crear ese espacio, el usuario debe haber verificado su correo. Si el perfil ya existe pero el usuario no figura como miembro, la app muestra un error y no intenta saltarse las reglas.
- Comunidad y chat usan las colecciones Firestore existentes `posts`, `comments`, `conversations` y `messages`. Publicar, comentar, dar like e iniciar conversaciones exige correo verificado.
- El contenido multimedia todavía no se sube desde iOS; esta versión no simula ni guarda URLs ficticias.
