# Pawly para iOS

Port nativo SwiftUI que comparte Firebase/Firestore y Cloudinary con Android. El trabajo se desarrolla en `ios-funciones`; no modifica `main` ni el código Android.

## Funciones iOS incorporadas
- Acceso, registro, verificación y recuperación de contraseña con Firebase; inicio de sesión Google.
- Perfiles multi-mascota y selector persistente; invitaciones a compartir con roles editor/lector.
- Pasaporte sanitario, rutinas, diario sincronizado y exportación PDF.
- Comunidad con muro, categorías, multimedia, likes, comentarios, búsqueda de personas, seguimiento, bloqueo y denuncias.
- Mensajería en tiempo real con fotos/vídeos y confirmación de lectura.
- Perfil público editable: nombre, biografía, foto y privacidad.
- Explorar: búsqueda local de veterinarios, urgencias, parques, protectoras, playas, rutas, restaurantes y alojamientos pet-friendly; mapa, llamadas, indicaciones y favoritos.
- Aprender: guías y noticias RSS temáticas.
- Agenda de recordatorios locales con fecha/hora, repetición y notificaciones del sistema.
- Ajustes simple/elegante, modo oscuro, paletas, idioma preferido y notificaciones.
- Registro de tokens APNs/FCM y backend Firebase opcional para notificaciones y eliminación integral.

## Firebase iOS
1. Registra una app iOS con bundle ID `com.pawly.ios` en el proyecto Firebase actual.
2. Coloca el plist de iOS en `ios/Pawly/GoogleService-Info.plist`. No reutilices el JSON de Android.
3. Copia los valores `CLIENT_ID` y `REVERSED_CLIENT_ID` del plist a los build settings `GOOGLE_CLIENT_ID` y `GOOGLE_REVERSED_CLIENT_ID`.
4. Activa Email/Password y Google en Firebase Authentication y configura los clientes OAuth.
5. Para push: sube una APNs Auth Key (.p8) en Firebase Console → Project Settings → Cloud Messaging; activa Push Notifications y Background Modes → Remote notifications en Xcode.

## Cloudinary
Configura `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_IMAGE_UPLOAD_PRESET` y `CLOUDINARY_VIDEO_UPLOAD_PRESET` en el target iOS. No incluyas el API Secret en la app. Los presets unsigned y las URLs son públicos para quien los conozca; para producción conviene un backend de firma.

## Backend opcional (notificaciones push y borrado integral)
En `functions/` se incluye Firebase Functions v2 para avisar de mensajes/publicaciones y procesar solicitudes de eliminación (Auth, Firestore y medios Cloudinary).
Requiere Node 20, Firebase CLI y plan Blaze. Configura secretos `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` y despliega:
```bash
cd functions && npm install && cd ..
firebase functions:secrets:set CLOUDINARY_CLOUD_NAME
firebase functions:secrets:set CLOUDINARY_API_KEY
firebase functions:secrets:set CLOUDINARY_API_SECRET
firebase deploy --only functions
```
No se ha desplegado ni activado facturación desde este repositorio. No actives Blaze sin revisar y aceptar los costes.

## Compilar
Requisitos: macOS, Xcode 16+, Homebrew y XcodeGen.
```bash
cd ios
xcodegen generate
open Pawly.xcodeproj
xcodebuild -project Pawly.xcodeproj -scheme Pawly -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```
CI compila el simulador; Firebase/Google/Cloudinary/APNs reales y las pruebas con dos cuentas deben validarse en dispositivo.

## Límites que deben validarse
- Los recordatorios son locales al dispositivo; no se sincronizan con Android ni con otros iPhones.
- Las push requieren APNs, Firebase Messaging y Functions desplegadas.
- La eliminación integral requiere Functions y secretos Cloudinary configurados.
- Cloudinary unsigned no ofrece privacidad de archivos ni cifrado E2E.
- Explorar usa MapKit; los resultados dependen de Apple Maps y de la zona.
- El idioma preferido queda guardado, pero no se ha traducido todavía todo el contenido.
