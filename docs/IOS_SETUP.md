# Puesta en marcha de Pawly para iOS

Esta guía corresponde a la rama `codex/ios-auth-push-hardening-2026-11` y al bundle ID `com.pawly.ios`.

## 1. Firebase: una app iOS dentro del proyecto existente

No crees un proyecto Firebase nuevo. Registra una aplicación iOS adicional dentro del mismo proyecto que utiliza Android: `pawly-83dca`.

1. Firebase Console → proyecto `pawly-83dca` → Configuración del proyecto → General → Tus aplicaciones → Añadir app → iOS.
2. Bundle ID: `com.pawly.ios` (debe coincidir exactamente).
3. Descarga `GoogleService-Info.plist`.
4. Coloca el archivo en `ios/Pawly/GoogleService-Info.plist`. Está excluido de Git y no debe subirse al repositorio.
5. En Authentication → Sign-in method, comprueba que están habilitados Correo electrónico/contraseña y Google. Los proveedores son del proyecto Firebase, no necesitas activarlos por separado para cada plataforma.
6. En el `GoogleService-Info.plist`, copia:
   - `CLIENT_ID` → `GOOGLE_CLIENT_ID` en `ios/project.yml`.
   - `REVERSED_CLIENT_ID` → `GOOGLE_REVERSED_CLIENT_ID` en `ios/project.yml`.

Vuelve a generar el proyecto Xcode después de editar los ajustes:

```sh
cd ios
xcodegen generate
```

Documentación oficial: [Añadir Firebase a una app Apple](https://firebase.google.com/docs/ios/setup) y [Google Sign-In con Firebase en Apple](https://firebase.google.com/docs/auth/ios/google-signin).

### Reglas de Firestore

La nueva rama incluye cambios de reglas para reservas de nombre, tokens iOS, solicitudes de borrado iOS y gastos compartidos. Después de revisar la rama, despliega esas reglas en el proyecto correcto desde la raíz del repositorio:

```sh
firebase login
firebase deploy --only firestore:rules --project pawly-83dca
```

No despliegues reglas desde la rama Android antigua o desde otra copia del repositorio. La app usa Firestore; no hace falta crear otra base de datos ni activar Firebase Storage.

## 2. Cloudinary: reutiliza la cuenta actual

No hace falta crear otra cuenta ni otro producto Cloudinary. Pawly sigue guardando fotos y vídeos en Cloudinary; las nuevas subidas de iOS se indexan en Firestore para relacionarlas con su propietario.

En Cloudinary Console → Settings → Upload → Upload presets:

- Reutiliza los presets de Android si ya aceptan los tipos de archivo y carpetas que necesita iOS, o crea uno para imágenes y otro para vídeos.
- Ambos presets deben ser **Unsigned** (el cliente iOS no contiene el API Secret).
- Restringe los formatos aceptados y los tamaños. El cliente comprime imágenes y aplica límites propios, pero el preset debe imponer también límites en el servidor.
- Comprueba que una subida de prueba devuelve un `public_id` que empieza por `pawly/{carpeta}/{uid}/`. El índice de propiedad de Firestore valida ese prefijo.

Copia estos datos en los ajustes de `ios/project.yml`:

| Ajuste | Qué debes poner |
|---|---|
| `CLOUDINARY_CLOUD_NAME` | Cloud name de la cuenta |
| `CLOUDINARY_IMAGE_UPLOAD_PRESET` | Nombre del preset unsigned para imágenes |
| `CLOUDINARY_VIDEO_UPLOAD_PRESET` | Nombre del preset unsigned para vídeos |

No pongas `API_SECRET` en iOS, en `project.yml`, ni en el repositorio. Los presets unsigned son visibles desde la aplicación; trátalos como valores públicos y limita su uso en Cloudinary.

Documentación oficial: [Subidas iOS de Cloudinary](https://cloudinary.com/documentation/ios_image_and_video_upload), [Upload Presets](https://cloudinary.com/documentation/upload_presets) y [Folder Modes](https://cloudinary.com/documentation/folder_modes).

## 3. Apple Push Notifications y Firebase Cloud Messaging

Para notificaciones push reales, sí hay que configurar Apple además de Firebase:

1. En Apple Developer → Certificates, Identifiers & Profiles → Keys, crea una clave con **Apple Push Notifications service (APNs)**.
2. Conserva el archivo `.p8`, el Key ID y el Team ID en un lugar seguro.
3. Firebase Console → Configuración del proyecto → Cloud Messaging → configuración de la app iOS → sube la clave APNs e introduce Key ID y Team ID.
4. En Apple Developer, confirma que el App ID `com.pawly.ios` tiene habilitada la capacidad Push Notifications.
5. En Xcode, la firma y el provisioning profile deben incluir Push Notifications.

El proyecto ya tiene el entitlement configurado para desarrollo en Debug y producción en Release. No necesitas subir el archivo `.p8` a GitHub ni a esta conversación.

Documentación oficial: [Firebase Cloud Messaging para apps Apple](https://firebase.google.com/docs/cloud-messaging/ios/get-started).

## 4. Cloudflare: necesario para push y funciones de backend

La rama ya llama al endpoint `POST /v1/activity-notifications`, pero el Worker del repositorio todavía no está desplegado. Sin ese despliegue, los avisos se guardan en Firestore, pero no se entregan como push al móvil.

1. Crea o utiliza tu cuenta de Cloudflare.
2. Desde la raíz del repositorio:

```sh
cd backend/cloudflare-worker
npm ci
npx wrangler login
```

3. Configura las credenciales como secretos (no como variables de texto):

```sh
npx wrangler secret put FIREBASE_CLIENT_EMAIL
npx wrangler secret put FIREBASE_PRIVATE_KEY
npx wrangler secret put PHONE_DISCOVERY_HMAC_SECRET
```

- `FIREBASE_CLIENT_EMAIL` y `FIREBASE_PRIVATE_KEY`: cuenta de servicio dedicada del proyecto Firebase `pawly-83dca`, con permisos mínimos para Firestore y envío de Firebase Cloud Messaging.
- `PHONE_DISCOVERY_HMAC_SECRET`: secreto aleatorio de al menos 32 caracteres. No reutilices una contraseña. Es necesario para las funciones de búsqueda telefónica del Worker.

El `wrangler.toml` ya contiene el ID del proyecto Firebase y su número de proyecto. Comprueba ambos antes de desplegar.

4. Ejecuta las verificaciones y despliega:

```sh
npm run check
npm test
npm run deploy
```

5. Copia la URL `workers.dev` que te devuelva Wrangler y pégala en `PAWLY_BACKEND_URL` dentro de `ios/project.yml`. Por ejemplo, será una URL con el formato `https://pawly-api.<tu-subdominio>.workers.dev`.
6. Comprueba que `https://tu-worker/health` devuelve el estado `ok`.

Si también vas a usar desde el Worker el borrado de recursos Cloudinary, configura allí `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY` y `CLOUDINARY_API_SECRET` como secretos/variables protegidos. Esos datos nunca deben llegar a la app iOS.

Documentación oficial: [Secretos de Cloudflare Workers](https://developers.cloudflare.com/workers/configuration/secrets/) y [Variables de entorno](https://developers.cloudflare.com/workers/configuration/environment-variables/).

## 5. Qué sigue sin poder darse por terminado

- El Worker debe desplegarse en tu cuenta; los secretos no se pueden crear desde este repositorio.
- El borrado integral de cuenta (Authentication + todas las subcolecciones y recursos Cloudinary) sigue pendiente en el backend. La app registra una solicitud, pero no debe afirmar que ya ha borrado la cuenta.
- Las notificaciones push requieren la clave APNs cargada en Firebase y el Worker desplegado.
- Los medios subidos antes de que se añadiera el índice `cloudinaryAssets` pueden no tener un registro de propietario; no se podrán localizar todos automáticamente con el índice nuevo.
- Es necesario hacer al menos una compilación firmada y probar autenticación, subida multimedia y push en un dispositivo Apple. La compilación de simulador no puede validar APNs reales.

## 6. Lista corta de comprobación

- [ ] Registrar `com.pawly.ios` dentro de Firebase `pawly-83dca`.
- [ ] Añadir localmente `ios/Pawly/GoogleService-Info.plist`.
- [ ] Configurar los dos valores OAuth de Google en `ios/project.yml`.
- [ ] Confirmar email/contraseña y Google en Firebase Authentication.
- [ ] Configurar el Cloud name y los dos presets unsigned de Cloudinary.
- [ ] Desplegar las reglas Firestore de esta rama.
- [ ] Crear la clave APNs y subirla a Firebase Cloud Messaging.
- [ ] Desplegar el Worker y añadir su URL en `PAWLY_BACKEND_URL`.
- [ ] Generar el proyecto con XcodeGen y compilar una build Debug/Release según corresponda.
