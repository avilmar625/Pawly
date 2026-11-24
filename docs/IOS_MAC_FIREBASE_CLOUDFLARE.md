# Pawly en Mac: desarrollo iOS, Firebase y Cloudflare

Guía para la rama **`codex/firebase-permission-preflight-2026-11`**. Esta es la rama de trabajo más reciente de iOS; parte de `codex/ios-responsive-darkmode-accessibility-2026-11` y conserva separadas las ramas de Android. No trabajes sobre `main` ni sobre la rama Android.

## 1. Descargar y abrir la última rama

Si todavía no has clonado el repositorio:

```bash
git clone https://github.com/avilmar625/Pawly.git
cd Pawly
git switch codex/firebase-permission-preflight-2026-11
git pull --ff-only
```

Si ya lo tienes clonado:

```bash
cd /ruta/a/Pawly
git status
git fetch origin
git switch codex/firebase-permission-preflight-2026-11
git pull --ff-only
```

Antes de cambiar de rama, guarda o aparta cualquier cambio local que no quieras perder. Comprueba que estás en la rama correcta:

```bash
git branch --show-current
git log -1 --oneline
```

## 2. Preparar las herramientas del Mac

Instala Xcode desde la App Store o Apple Developer y abre Xcode al menos una vez para aceptar su licencia e instalar los componentes adicionales. Después, en Terminal:

```bash
xcodebuild -version
xcode-select -p
```

Instala Homebrew si todavía no lo tienes y las herramientas del proyecto:

```bash
brew install xcodegen node
npm install -g firebase-tools
```

El Worker se prueba con Node 22. Si tienes varias versiones de Node, usa Node 22 para esta carpeta.

## 3. Generar y abrir el proyecto iOS

Desde la raíz del repositorio:

```bash
cd ios
xcodegen generate
xcodebuild -resolvePackageDependencies -project Pawly.xcodeproj -scheme Pawly
open Pawly.xcodeproj
```

En Xcode:

1. Selecciona el proyecto **Pawly** y el target **Pawly**.
2. En **Signing & Capabilities**, selecciona tu equipo de Apple Developer y deja el Bundle Identifier como `com.pawly.ios`.
3. Selecciona un simulador de iPhone y ejecuta la app.

Para compilar sin firmar, desde `ios/`:

```bash
xcodebuild -project Pawly.xcodeproj -scheme Pawly -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

XcodeGen genera el proyecto Xcode a partir de `ios/project.yml`. Si cambias ese archivo, vuelve a ejecutar `xcodegen generate`.

## 4. Conectar la app iOS al Firebase que ya utiliza Pawly

**No crees un proyecto Firebase nuevo.** Android ya utiliza el proyecto `pawly-83dca`; iOS debe registrarse como otra app dentro de ese mismo proyecto.

En Firebase Console:

1. Abre el proyecto `pawly-83dca`.
2. Ve a **Configuración del proyecto → General → Tus aplicaciones → Añadir app → iOS**.
3. Registra exactamente el Bundle ID `com.pawly.ios`.
4. Descarga el archivo `GoogleService-Info.plist`.
5. Colócalo en:

   ```
   ios/Pawly/GoogleService-Info.plist
   ```

El archivo está excluido de Git en `.gitignore`; debe permanecer en tu Mac y no subirse al repositorio. No uses el `google-services.json` de Android: es un archivo distinto para cada plataforma. La app iOS detecta si falta el plist y muestra una pantalla indicando que Firebase está sin configurar.

En el mismo plist, copia los valores:

- `CLIENT_ID` → `GOOGLE_CLIENT_ID` en `ios/project.yml`.
- `REVERSED_CLIENT_ID` → `GOOGLE_REVERSED_CLIENT_ID` en `ios/project.yml`.

En `ios/project.yml`, sustituye también los valores vacíos de Cloudinary y el backend cuando ya tengas esos datos:

- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_IMAGE_UPLOAD_PRESET`
- `CLOUDINARY_VIDEO_UPLOAD_PRESET`
- `PAWLY_BACKEND_URL` (se configura en el paso de Cloudflare).

Estos identificadores y presets de cliente no son contraseñas. Aun así, no añadas claves privadas ni secretos al archivo versionado. No publiques tu archivo `.p8`, una clave privada JSON de cuenta de servicio, tokens ni contraseñas.

Vuelve a generar y abrir el proyecto:

```bash
cd ios
xcodegen generate
open Pawly.xcodeproj
```

En **Firebase Console → Authentication → Sign-in method**, comprueba que están habilitados **Correo electrónico/contraseña** y **Google**. Configura también el correo de asistencia que pide Google. Las cuentas nuevas deben verificar el correo para poder utilizar acciones sociales y de publicación que lo exigen.

Documentación oficial: [Firebase para apps Apple](https://firebase.google.com/docs/ios/setup) y [Google Sign-In con Firebase](https://firebase.google.com/docs/auth/ios/google-signin).

## 5. Probar y desplegar Firestore

Pawly utiliza **Cloud Firestore** para cuentas, perfiles, comunidad, chat y mascotas compartidas. No utiliza Firebase Storage: las fotografías y los vídeos se cargan en Cloudinary.

Desde la raíz del repositorio, instala Firebase CLI si todavía no lo hiciste y entra en tu cuenta:

```bash
firebase login
firebase projects:list
```

Comprueba que aparece `pawly-83dca`. Antes de desplegar reglas, ejecuta la batería local del emulador. El comando está preparado para usar las reglas de este repositorio:

```bash
npm --prefix tests/firestore install
firebase emulators:exec --only firestore --project demo-pawly "npm --prefix tests/firestore test"
```

No continúes con el despliegue si falla alguna prueba. Cuando la validación esté verde, publica reglas e índices desde la **raíz** del repositorio:

```bash
firebase deploy --only firestore:rules,firestore:indexes --project pawly-83dca
```

Esto actualiza las reglas e índices del proyecto real. No pegues reglas abiertas ni uses `allow read, write: if true`. El proyecto ya contiene los ficheros `firebase.json`, `firestore.rules` y `firestore.indexes.json`.

## 6. Configurar Cloudinary para fotografías y vídeos

Usa la cuenta Cloudinary que ya utiliza Android; no hace falta crear otra. En **Settings → Upload → Upload presets**, revisa o crea:

- Un preset **Unsigned** para imágenes.
- Un preset **Unsigned** para vídeos.

Configura en `ios/project.yml` el Cloud name y los dos nombres de preset. La app envía los medios a carpetas propias de Pawly y registra en Firestore el identificador del recurso y su propietario.

**No introduzcas el API Secret de Cloudinary en iOS.** Los presets unsigned son visibles en la app y no hacen privadas las URLs: no subas material sensible. Los medios existentes en Cloudinary no se migran automáticamente.

## 7. Desplegar el backend Cloudflare Worker

El Worker contiene la parte de backend de confianza para notificaciones push, avisos de mascotas perdidas, propuestas de lugares y consultas protegidas de asistentes a eventos. Su código está en `backend/cloudflare-worker`, pero el repositorio no puede desplegarlo dentro de tu cuenta de Cloudflare sin que tú autorices el acceso y configures las credenciales.

Desde la raíz:

```bash
cd backend/cloudflare-worker
npm ci
npm run check
npm test
npx wrangler login
```

Se abrirá el navegador para autorizar Wrangler con tu cuenta Cloudflare.

### Crear la cuenta de servicio de Firebase

En Google Cloud Console del proyecto `pawly-83dca`, crea una cuenta de servicio dedicada al Worker, por ejemplo `pawly-worker`. Asígnale únicamente los permisos que necesita el código actual:

- **Cloud Datastore User** (`roles/datastore.user`) para las operaciones de Firestore.
- **Firebase Cloud Messaging API Admin** (`roles/firebasecloudmessaging.admin`) para enviar mensajes FCM.

Comprueba que la API Firebase Cloud Messaging está habilitada. Genera una clave JSON para la cuenta y guárdala fuera del repositorio. De esa clave solo necesitas introducir en Wrangler el campo `client_email` y el campo `private_key`. No subas el JSON a GitHub ni lo envíes por chat.

El `wrangler.toml` ya contiene el ID del proyecto (`pawly-83dca`) y el número de proyecto (`87732467956`). Comprueba que coinciden con la consola Firebase.

### Crear el Worker y guardar sus secretos

Haz un primer despliegue del Worker desde `backend/cloudflare-worker`:

```bash
npm run deploy
```

Después guarda los secretos en Cloudflare. Wrangler te pedirá el valor de cada uno:

```bash
npx wrangler secret put FIREBASE_CLIENT_EMAIL
npx wrangler secret put FIREBASE_PRIVATE_KEY
npx wrangler secret put PHONE_DISCOVERY_HMAC_SECRET
```

- `FIREBASE_CLIENT_EMAIL`: el `client_email` de la cuenta de servicio.
- `FIREBASE_PRIVATE_KEY`: el `private_key` completo de esa cuenta, incluyendo las líneas BEGIN/END.
- `PHONE_DISCOVERY_HMAC_SECRET`: secreto aleatorio de al menos 32 caracteres. Puedes generarlo localmente con `openssl rand -hex 32`.

Los secretos se guardan en Cloudflare, no en `wrangler.toml`, `.dev.vars`, `ios/project.yml` ni GitHub. Al modificar un secreto Wrangler publica una nueva versión del Worker.

Comprueba la URL que muestra Wrangler. La ruta de salud debe responder:

```bash
curl https://TU-WORKER.workers.dev/health
```

Debe devolver un JSON con `"status":"ok"`. Usa la URL real que te haya dado Wrangler, no la de ejemplo.

Vuelve a `ios/project.yml` y coloca esa URL HTTPS en `PAWLY_BACKEND_URL`. Genera el proyecto de nuevo con XcodeGen. La URL es pública; las credenciales de Firebase siguen estando únicamente en Cloudflare.

Documentación oficial: [Secretos de Cloudflare Workers](https://developers.cloudflare.com/workers/configuration/secrets/) y [variables de entorno](https://developers.cloudflare.com/workers/configuration/environment-variables/).

## 8. Activar las notificaciones push de Apple (APNs)

Firebase Messaging no puede entregar notificaciones push a iPhone hasta que esté configurado APNs. Se necesita una cuenta del programa Apple Developer y un dispositivo físico para comprobar el flujo real.

1. En Apple Developer, registra el App ID `com.pawly.ios` y habilita **Push Notifications**.
2. Crea una clave APNs (`.p8`) y guarda de forma segura el archivo, el **Key ID** y el **Team ID**.
3. En Firebase Console → Configuración del proyecto → Cloud Messaging, sube esa clave APNs e introduce Key ID y Team ID.
4. En Xcode → target Pawly → **Signing & Capabilities**, habilita **Push Notifications** y **Background Modes → Remote notifications**. Comprueba que tu Team y provisioning profile corresponden al App ID.

No subas el archivo `.p8` a GitHub. El entitlement y los ajustes de desarrollo/producción ya están en el proyecto; la firma y la clave APNs deben pertenecer a tu cuenta Apple.

## 9. Orden recomendado para probar Pawly

Haz las comprobaciones en este orden para localizar los errores antes de probar toda la app:

1. **Compilación:** genera el proyecto con XcodeGen y compila para un simulador.
2. **Firebase:** inicia sesión con correo, registra una cuenta de prueba, comprueba la verificación, cierra sesión y vuelve a entrar. Revisa en Firebase Authentication que se creó el usuario y en Firestore que aparecen su nombre reservado y su perfil.
3. **Google Sign-In:** pruébalo después de confirmar los valores OAuth y el URL scheme de iOS.
4. **Mascotas compartidas:** utiliza dos cuentas verificadas. Crea una mascota con la primera, invita a la segunda como editor y vuelve a probar con rol lector. Comprueba que editor puede modificar y lector solo consultar.
5. **Cloudinary:** añade una foto de mascota y envía una foto y un vídeo de prueba en Comunidad o Mensajes. Comprueba que la URL se guarda y que aparece el índice `cloudinaryAssets` asociado al UID.
6. **Worker:** comprueba primero `/health`. Después, con dos dispositivos/cuentas y APNs configurado, envía un mensaje y verifica que llega la push y que al tocarla se abre la conversación correcta.
7. **Estados de notificación:** prueba con la app abierta, en segundo plano y cerrada; prueba también desactivar notificaciones y cerrar sesión para comprobar que se elimina el token de esa cuenta.
8. **Regresión:** prueba muro, comentarios, likes, seguimiento, bloqueo, agenda, diario, pasaporte, gastos y copia de seguridad con dos usuarios cuando corresponda.

En Firebase, los tokens iOS deben aparecer bajo `users/{uid}/fcmTokens` con `platform: "ios"`. No basta con que iOS muestre permiso de notificaciones: comprueba que el token se registra y que el Worker puede enviar una push real.

## 10. Qué no debes dar por terminado todavía

- La app iOS compila para simulador, pero las credenciales reales, OAuth, APNs y Cloudinary se tienen que probar con tu configuración.
- El Worker está versionado, no se considera operativo hasta desplegarlo y añadir sus secretos.
- La búsqueda por número de teléfono utiliza el flujo PNV de Android; no está implementada como flujo equivalente en iOS. No actives verificación telefónica de producción ni servicios facturables para esta prueba.
- La eliminación integral de una cuenta (Firebase Authentication + todas las subcolecciones y recursos Cloudinary) sigue pendiente en el backend. La app registra una solicitud, pero no completa el borrado.
- Las subidas unsigned de Cloudinary no son privadas ni ofrecen cifrado de extremo a extremo.
- Los recordatorios locales de iOS no se convierten automáticamente en recordatorios sincronizados con Android.

**No publiques Pawly como terminada** hasta completar las pruebas en dispositivos reales, cerrar la eliminación integral de cuenta y revisar privacidad, moderación y configuración de producción.
