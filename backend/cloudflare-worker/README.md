# Pawly API — Cloudflare Workers

Backend de confianza para notificaciones de actividad, alertas de mascotas perdidas y búsqueda de contactos mediante HMAC. Está diseñado para el plan gratuito de Cloudflare Workers y no utiliza Firebase Cloud Functions.

## Qué hace

- Verifica el Firebase ID token del usuario con las claves públicas oficiales de Firebase Authentication.
- Comprueba en Firestore que el like, comentario o seguimiento existe realmente.
- Comprueba bloqueos en ambos sentidos.
- Obtiene los tokens FCM del destinatario mediante paginación acotada (hasta 500 dispositivos) y envía el mensaje mediante FCM HTTP v1.
- Para comentarios, exige que el identificador coincida con el último comentario registrado en la publicación, además de comprobar autoría y existencia.
- Registra un identificador idempotente en `pushDispatches`, con estado, lease temporal y recuperación condicional de envíos fallidos o abandonados.
- Elimina tokens FCM que FCM devuelve como no registrados.
- Envía avisos de mascotas perdidas a usuarios que han activado voluntariamente un radio de 5, 10 o 20 km. Se almacena una ubicación redondeada (~1 km), se respetan bloqueos y el envío se realiza por FCM.
- Permite descubrir contactos mediante HMAC-SHA256. El teléfono se procesa en memoria; Firestore solo conserva un hash HMAC y el UID. No se expone el número ni se sube la agenda completa.
- Mantiene el directorio de teléfonos inaccesible desde clientes; las operaciones pasan por el Worker autenticado.

El cliente nunca recibe ni contiene la clave privada de la cuenta de servicio.

## Requisitos de configuración

1. Crear una cuenta gratuita de Cloudflare y autenticar Wrangler.
2. En Google Cloud Console del proyecto Firebase `pawly-83dca`, crear una cuenta de servicio dedicada para Pawly API.
3. Asignarle los permisos mínimos necesarios para leer los documentos de usuarios/publicaciones y gestionar `pushDispatches`, además del permiso de envío de Firebase Cloud Messaging. Revisar los roles IAM efectivos antes de producción.
4. Generar una clave JSON de esa cuenta de servicio y guardar el archivo fuera del repositorio.
5. No subir nunca el JSON ni la clave privada a GitHub.

Configurar secretos en Cloudflare:

```bash
cd backend/cloudflare-worker
npm install
npx wrangler login
npx wrangler secret put FIREBASE_CLIENT_EMAIL
npx wrangler secret put FIREBASE_PRIVATE_KEY
openssl rand -hex 32 | npx wrangler secret put PHONE_MATCH_HMAC_SECRET
npm run check
npm run deploy
```

El ID del proyecto está en `wrangler.toml`. La clave privada debe pegarse completa como valor secreto PEM; Wrangler la almacena como secreto, no en el repositorio.

## Nuevos servicios y consentimiento

- **Mascotas perdidas:** Android crea el aviso y `lostPetAlerts/{postId}` después de publicar la entrada `LOST`. El Worker comprueba autoría y estado, calcula distancias y envía FCM solo a suscripciones activas dentro del radio. La ubicación se redondea a dos decimales antes de almacenarse; no se guarda la coordenada GPS exacta. Las personas receptoras eligen el radio y pueden desactivarlo desde Más → He perdido a mi mascota.
- **Contactos:** cada persona decide si quiere ser localizable y registra manualmente su teléfono. El Worker normaliza el número y genera un HMAC con `PHONE_MATCH_HMAC_SECRET`; el número original no se persiste. La búsqueda usa el selector de teléfonos del sistema y devuelve solo perfiles públicos con consentimiento. El secreto debe permanecer estable.
- **Lugares de la comunidad:** las propuestas quedan en `communityPlaces` con estado `pending`. Solo una cuenta con custom claim `admin=true` puede aprobarlas o rechazarlas; el cliente solo consulta documentos `approved`. Revisa las propuestas en Firestore o con una herramienta administrativa protegida.
- **Profesionales:** los perfiles profesionales publicados se muestran en el directorio de Pawly. La insignia identifica un tipo de cuenta, no acredita títulos ni colegiación. El contacto solo se muestra cuando el profesional activa expresamente su publicación.

## Conectar Android

Añadir la URL HTTPS del Worker a `local.properties`:

```properties
PAWLY_BACKEND_URL=https://pawly-api.<tu-subdominio>.workers.dev
```

No incluir barras ni rutas al final. Después, volver a compilar Android. Si la propiedad está vacía, Pawly conserva la experiencia actual sin intentar enviar push.

## Límites y seguridad

- El endpoint de actividad solo admite los tres tipos de interacción definidos; no acepta texto arbitrario ni tokens FCM aportados por el cliente.
- Los teléfonos se procesan en memoria y no se guardan en claro. El hash HMAC no es reversible sin el secreto, que nunca debe guardarse en Android ni en Git.
- El radio se calcula en el servidor con una ubicación redondeada; los avisos no garantizan entrega instantánea ni sustituyen otros medios de búsqueda.
- Las reglas de Firestore siguen siendo obligatorias. El Worker no las sustituye.
- Los envíos completados quedan marcados como `sent`; los fallidos se pueden reintentar y los procesos abandonados se recuperan al expirar un lease de cinco minutos. Los registros antiguos sin estado se tratan como completados para evitar duplicados tras la actualización. La entrega FCM es *at-least-once*: si un proceso falla después de entregar a algunos dispositivos pero antes de guardar el resultado, un reintento puede duplicar esa notificación.
- Para limpiar automáticamente `pushDispatches` después de 30 días, configurar una política TTL de Firestore sobre el campo `expiresAt` de esa colección. Sin esa política, el campo por sí solo no elimina documentos.
- El límite gratuito de Workers aplica a las peticiones y CPU; Firestore y FCM tienen sus propias cuotas.
- No se incluye aquí el borrado de cuentas. No se debe presentar el borrado como automático hasta completar y probar la cascada de datos, medios Cloudinary y autenticación.
- No se ha desplegado este Worker desde GitHub: el propietario de la cuenta debe configurar los secretos y ejecutar el despliegue.

Antes de producción, probar con un proyecto Firebase de pruebas y una cuenta de servicio dedicada; comprobar especialmente permisos IAM, tokens caducados, bloqueos, duplicados y tokens FCM inválidos.
