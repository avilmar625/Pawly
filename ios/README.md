# Pawly para iOS

[Auditoría por requisito, configuración y pruebas de noviembre de 2026](AUDITORIA_2026_11.md)

Port nativo SwiftUI que comparte Firebase/Firestore y Cloudinary con Android. La rama iOS más reciente de trabajo es `codex/ios-android-parity-2026-11`; las ramas nativas de Android e iOS siguen separadas. El cliente iOS vive en `ios/` y el código Android continúa en sus carpetas actuales. Para preparar el entorno completo en macOS, sigue [la guía de Mac, Firebase y Cloudflare](../docs/IOS_MAC_FIREBASE_CLOUDFLARE.md).

## Funciones iOS incorporadas
- Acceso, registro, verificación y recuperación de contraseña con Firebase; inicio de sesión Google.
- Perfiles multi-mascota y selector persistente; invitaciones a compartir con roles editor/lector.
- Pasaporte sanitario filtrable, rutinas, diario sincronizado con editor plegable/filtros y hasta ocho fotografías, y exportación PDF.
- Comunidad con muro, categorías, multimedia, likes, comentarios, búsqueda de personas, seguimiento, bloqueo y denuncias.
- Mensajería en tiempo real con fotos/vídeos y confirmación de lectura.
- Perfil público editable: nombre, biografía, foto y privacidad.
- Explorar: búsqueda local de veterinarios, urgencias, parques, protectoras, playas, rutas, restaurantes y alojamientos pet-friendly; mapa, llamadas, indicaciones y favoritos.
- Aprender: guías y noticias RSS temáticas.
- Agenda de recordatorios locales con fecha/hora, repetición y notificaciones del sistema.
- Ajustes simple/elegante, modo oscuro, paletas, idioma preferido y notificaciones.
- Registro de tokens APNs/FCM y notificaciones push de actividad y mensajes mediante el Worker de Cloudflare, cuando esté desplegado y configurado. La eliminación integral sigue requiriendo su propio proceso de backend.

## Firebase iOS
1. Registra una app iOS con bundle ID `com.alexvilarrubla.pawly` en el proyecto Firebase actual.
2. Coloca el plist de iOS en `ios/Pawly/GoogleService-Info.plist`. No reutilices el JSON de Android.
3. Copia los valores `CLIENT_ID` y `REVERSED_CLIENT_ID` del plist a los build settings `GOOGLE_CLIENT_ID` y `GOOGLE_REVERSED_CLIENT_ID`.
4. Activa Email/Password y Google en Firebase Authentication y configura los clientes OAuth.
5. Para push: sube una APNs Auth Key (.p8) en Firebase Console → Project Settings → Cloud Messaging; activa Push Notifications y Background Modes → Remote notifications en Xcode.

## Configuración local y diagnóstico
Conserva `Config.local.xcconfig` y `Pawly/GoogleService-Info.plist` fuera de Git. La configuración local se incorpora explícitamente al Info.plist durante la compilación. En el xcconfig, escribe una URL HTTPS como `https:/$()/tu-worker.workers.dev` para que Xcode no trate la doble barra como un comentario. No añadas secretos. Si cambias project.yml, vuelve a ejecutar xcodegen generate. La cámara y APNs deben verificarse en un dispositivo físico; la galería funciona en el simulador si contiene medios.

## Cloudinary
Configura `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_IMAGE_UPLOAD_PRESET` y `CLOUDINARY_VIDEO_UPLOAD_PRESET` en el target iOS. No incluyas el API Secret en la app. Los presets unsigned y las URLs son públicos para quien los conozca; para producción conviene un backend de firma.

## Backend
Esta rama no incluye Firebase Functions. Las solicitudes de borrado quedan registradas en Firestore, pero no se ejecuta desde aquí un proceso administrativo para borrar Auth y medios Cloudinary. Las notificaciones remotas requieren APNs y la configuración de Firebase Messaging; no se presupone que haya un backend de envío desplegado.

## Compilar
Requisitos: macOS, Xcode 16+, Homebrew y XcodeGen.
```bash
cd ios
# Solo si no tienes configuración local: cp Config.example.xcconfig Config.local.xcconfig
xcodegen generate
open Pawly.xcodeproj
xcodebuild -project Pawly.xcodeproj -scheme Pawly -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```
CI compila el simulador; Firebase/Google/Cloudinary/APNs reales y las pruebas con dos cuentas deben validarse en dispositivo.

## Límites que deben validarse
- En mascotas compartidas, la agenda sincroniza el título, la fecha y la categoría. Notas, repetición, finalización y programación de avisos se conservan por dispositivo.
- Las push requieren APNs, Firebase Messaging, tokens registrados y el Worker de Cloudflare desplegado/configurado. Los avisos de mensajes validan la conversación y no incluyen el texto privado.
- El flujo registra la solicitud de eliminación. El procesador integral de Firebase Auth, subcolecciones y medios sigue pendiente.
- Cloudinary unsigned no ofrece privacidad de archivos ni cifrado E2E.
- Explorar usa MapKit; los resultados dependen de Apple Maps y de la zona.
- El idioma preferido queda guardado, pero no se ha traducido todavía todo el contenido.


La versión iOS incluye un cartel JPG de mascota perdida que se genera localmente y se comparte desde el dispositivo.
