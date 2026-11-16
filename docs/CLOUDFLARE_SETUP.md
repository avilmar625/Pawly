# Configurar Cloudflare Workers para Pawly

Esta guía deja preparado el backend de Pawly sin activar planes de pago. **El Worker está en GitHub, pero no está desplegado hasta que se complete este procedimiento en tu cuenta de Cloudflare.**

## 1. Crear el Worker gratuito

1. Crea/inicia sesión en Cloudflare y activa Workers & Pages.
2. Asegúrate de que tienes habilitado el subdominio gratuito `workers.dev`.
3. En tu Mac, abre Terminal y clona/actualiza la rama `cambios62-auditoria-gratis-seguridad`.
4. Entra en el backend:

```bash
cd Pawly/backend/cloudflare-worker
npm ci
npx wrangler login
```

Se abrirá el navegador para autorizar Wrangler con tu cuenta. No compartas tokens de Cloudflare ni claves privadas.

## 2. Crear credenciales de Firebase para el Worker

En Google Cloud Console del proyecto `pawly-83dca`:

1. Crea una cuenta de servicio dedicada, por ejemplo `pawly-worker`.
2. Dale solo los permisos necesarios para leer/escribir los documentos Firestore que usa el Worker y enviar Firebase Cloud Messaging (FCM).
3. Genera una clave JSON y guárdala fuera de la carpeta del repositorio, por ejemplo en una carpeta segura de tu usuario.
4. No subas ese JSON a GitHub ni lo envíes por chat.

En la terminal, configura los secretos (Wrangler solicitará el valor de cada uno):

```bash
npx wrangler secret put FIREBASE_CLIENT_EMAIL
npx wrangler secret put FIREBASE_PRIVATE_KEY
```

- `FIREBASE_CLIENT_EMAIL`: el campo `client_email` del JSON.
- `FIREBASE_PRIVATE_KEY`: el campo `private_key` completo, incluyendo las líneas BEGIN/END.

El ID de proyecto `pawly-83dca` ya está configurado en `wrangler.toml`; no hace falta introducirlo como secreto.

## 3. Comprobar y desplegar

```bash
npm run check
npm test
npx wrangler deploy
```

Wrangler mostrará la dirección del Worker. Será parecida a:

```
https://pawly-api.<tu-subdominio>.workers.dev
```

Comprueba la dirección sustituyendo el subdominio real:

```bash
curl https://pawly-api.<tu-subdominio>.workers.dev/health
```

Respuesta esperada:

```json
{"service":"pawly-api","status":"ok"}
```

## 4. Conectar la aplicación Android

En el archivo `local.properties` de la raíz del proyecto Android, añade la URL real:

```properties
PAWLY_BACKEND_URL=https://pawly-api.<tu-subdominio>.workers.dev
```

No copies literalmente el subdominio de ejemplo. Después sincroniza Gradle y vuelve a compilar Pawly.

La URL es configuración pública del endpoint; las claves de Firebase permanecen en Cloudflare. No introduzcas `FIREBASE_PRIVATE_KEY` ni secretos de Cloudinary en Android.

## 5. Qué funciona al desplegarlo

- Notificaciones verificadas de likes, comentarios y seguimientos.
- Alertas de mascotas perdidas, con consentimiento y ubicación aproximada. Se envían por lotes que respetan el límite de 50 subpeticiones externas por ejecución del plan Free.
- Propuestas de lugares con coordenadas exactas, una por usuario y día, en estado `pending` hasta su revisión.

## 6. Qué sigue desactivado o pendiente

- **Búsqueda por teléfono:** desactivada porque no se verifica la titularidad. No configuraremos Firebase Phone Auth si implica activar Blaze o pagar SMS.
- **Borrado integral de cuenta:** todavía no está implementado. La app registra la solicitud, pero no elimina automáticamente Firebase Authentication, todos los documentos Firestore ni los medios Cloudinary. No publiques la app como lista para producción hasta completar y probar esa cascada.
- Los recursos Cloudinary subidos desde esta rama registran su `public_id` para preparar el borrado. Para eliminar medios en Cloudinary habrá que configurar más adelante `CLOUDINARY_API_KEY` y `CLOUDINARY_API_SECRET` como secretos Wrangler; no se deben añadir ahora a Android.

## Coste

El Worker se configura en el plan gratuito y `wrangler.toml` fija explícitamente el máximo de 50 subpeticiones externas por ejecución. No ejecutes comandos para cambiar a Workers Paid. Firestore y FCM conservan sus cuotas independientes; este despliegue no activa Firebase Blaze ni Firebase Cloud Functions.
