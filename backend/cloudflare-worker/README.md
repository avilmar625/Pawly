# Pawly API — Cloudflare Workers (plan gratuito)

Backend de confianza para notificaciones de actividad, avisos de mascotas perdidas y propuestas moderadas de lugares. Se ejecuta en Cloudflare Workers y no utiliza Firebase Cloud Functions.

## Estado real

- El código y los tests están en este repositorio.
- **El Worker no está desplegado todavía en ninguna cuenta de Cloudflare.**
- Para conectarlo a la app hay que desplegarlo con Wrangler, configurar los secretos y añadir su URL a `PAWLY_BACKEND_URL` en el `local.properties` local de Android.
- La búsqueda por teléfono está desactivada: no se puede registrar ni buscar un número sin demostrar su titularidad.
- La eliminación completa de cuentas todavía no está implementada. El índice de recursos Cloudinary ya se registra para facilitar esa tarea, pero no existe aún un proceso que elimine en cascada Firestore, Cloudinary y Firebase Authentication.

## Funciones implementadas en el Worker

- Verifica Firebase ID tokens y exige correo verificado.
- Comprueba en Firestore que likes, comentarios y seguimientos existen realmente antes de enviar notificaciones.
- Comprueba bloqueos en ambos sentidos.
- Envía notificaciones FCM HTTP v1 y limpia tokens no registrados.
- Gestiona envíos idempotentes para evitar notificaciones duplicadas.
- Envía avisos de mascotas perdidas según radios consentidos de 5, 10 o 20 km, usando celdas geográficas aproximadas.
- Procesa los destinatarios de avisos por lotes paginados. Cada ejecución procesa como máximo cuatro candidatos y consulta como máximo dos dispositivos por destinatario, para mantenerse por debajo del límite de 50 subpeticiones externas del plan Free.
- Recibe propuestas de lugares con coordenadas exactas, valida el contenido y limita a una propuesta por cuenta verificada y día UTC. Las propuestas se crean como `pending`; solo moderación puede aprobarlas.
- No contiene endpoints de búsqueda por teléfono.

## Límites del plan gratuito

La documentación de Cloudflare indica 100.000 peticiones diarias y 50 subpeticiones externas por invocación para Workers Free. El Worker pagina los destinatarios de alertas para no superar ese límite. Las cuotas de Firestore y Firebase Cloud Messaging son independientes y también deben supervisarse.

## Requisitos previos

1. Cuenta gratuita de Cloudflare con un subdominio `workers.dev` habilitado.
2. Proyecto Firebase `pawly-83dca`.
3. Cuenta de servicio dedicada de Google para el Worker, con permisos mínimos de lectura/escritura de Firestore y envío de Firebase Cloud Messaging.
4. Wrangler instalado mediante las dependencias del proyecto.

## Despliegue inicial

Desde la raíz del repositorio:

```bash
cd backend/cloudflare-worker
npm ci
npx wrangler login
npx wrangler secret put FIREBASE_CLIENT_EMAIL
npx wrangler secret put FIREBASE_PRIVATE_KEY
npm run check
npm test
npm run deploy
```

Al ejecutar cada `wrangler secret put`, pega el valor solicitado en el terminal. La clave privada PEM debe mantenerse como secreto de Cloudflare. No subas el JSON de la cuenta de servicio, claves privadas ni secretos a GitHub.

El identificador de Firebase ya está definido como variable no secreta en `wrangler.toml`. El Worker utiliza estas variables/secretos:

| Nombre | Tipo | Contenido |
|---|---|---|
| `FIREBASE_PROJECT_ID` | Variable Wrangler | `pawly-83dca` |
| `FIREBASE_CLIENT_EMAIL` | Secreto Wrangler | Correo de la cuenta de servicio |
| `FIREBASE_PRIVATE_KEY` | Secreto Wrangler | Clave privada PEM de la cuenta de servicio |

No configures `PHONE_MATCH_HMAC_SECRET`: la búsqueda por teléfono se ha retirado.

Tras desplegar, Wrangler mostrará una URL similar a:

```
https://pawly-api.<tu-subdominio>.workers.dev
```

Comprueba el servicio:

```bash
curl https://pawly-api.<tu-subdominio>.workers.dev/health
```

Debe responder `{"service":"pawly-api","status":"ok"}`.

## Conectar Android

Añade la URL real del Worker a `local.properties` (en la raíz del proyecto Android):

```properties
PAWLY_BACKEND_URL=https://pawly-api.<tu-subdominio>.workers.dev
```

Después sincroniza Gradle y recompila. Esta propiedad se incorpora a `BuildConfig`; no se debe escribir la URL ficticia del ejemplo ni subir el archivo `local.properties` al repositorio.

## Lugares de la comunidad

El cliente llama a `POST /v1/community-places`. El Worker comprueba el ID token, valida nombre/categoría/dirección/coordenadas y reserva una única propuesta diaria por usuario. La creación usa estado `pending`. El cliente no puede crear lugares directamente en Firestore. Las reglas permiten que el autor edite su propuesta/publicación y elimine su propio lugar; el equipo de moderación gestiona aprobación, rechazo y retirada.

Configura una política TTL de Firestore sobre `communityPlaceSubmissionLimits.expiresAt` para limpiar reservas de más de 90 días. Sin TTL, el límite sigue funcionando pero los documentos de cuota se acumulan.

## Privacidad y eliminación de cuenta

- No se verifica la titularidad de teléfonos; por eso la búsqueda telefónica está deshabilitada.
- Los registros de asistentes a eventos son privados; la app solo muestra el contador de interés.
- Las imágenes y vídeos nuevos registran su `public_id` y tipo en `cloudinaryAssets`, bajo el UID propietario. Esto es un índice para la futura cascada de borrado, no significa que el borrado esté ya automatizado.
- **No publiques Pawly como lista para producción mientras la eliminación de cuenta no borre y verifique todos los datos y recursos multimedia.** Consulta `docs/ACCOUNT_DELETION_OPERATIONS.md`.

