# Pawly API — Cloudflare Workers Free

Backend de confianza de Pawly para notificaciones FCM, avisos de mascotas perdidas, moderación de lugares y descubrimiento privado de contactos. No utiliza Firebase Cloud Functions ni Firebase Storage.

## Estado de despliegue

- El Worker y sus pruebas están versionados en este repositorio.
- **Todavía no está desplegado en una cuenta de Cloudflare.** La conexión real requiere que el propietario inicie sesión en Cloudflare y configure los secretos indicados abajo.
- La app solo utilizará este backend cuando `PAWLY_BACKEND_URL` apunte a la URL real del Worker.
- No se han añadido credenciales privadas a GitHub ni a la app.

## Funciones del Worker

- Verifica Firebase ID tokens y exige correo verificado.
- Comprueba en Firestore el origen de likes, comentarios y follows; respeta bloqueos, idempotencia y tokens FCM.
- Para mensajes, valida pertenencia a la conversación y existencia del mensaje; envía un aviso sin incluir el contenido privado.
- Envía alertas de mascotas perdidas por radios voluntarios de 5, 10 o 20 km, con coordenadas aproximadas y paginación.
- Limita cada ejecución de alertas para respetar el máximo de 50 subpeticiones externas por invocación en Workers Free.
- Recibe propuestas de lugares verificadas, limita a una propuesta diaria por cuenta y las guarda como `pending`.
- Verifica tokens Firebase Phone Number Verification (PNV), extrae el número verificado en el servidor y crea un índice HMAC. Nunca guarda números en claro.
- Permite activar/desactivar voluntariamente la búsqueda por teléfono.
- Busca hasta 30 contactos por lote, máximo 3 lotes diarios por cuenta y 10 coincidencias devueltas. Respeta bloqueos en ambos sentidos.
- Los documentos `phoneDirectory` y `phoneDiscoveryLimits` solo son accesibles para el backend con cuenta de servicio; las reglas de Firestore niegan el acceso directo desde el cliente.
- Devuelve asistentes a eventos solo a la persona organizadora mediante `POST /v1/events/attendees`, pagina 12 por petición y filtra bloqueos en ambos sentidos.

## Límites del plan gratuito

Workers Free incluye 100.000 peticiones al día, 10 ms de CPU por petición y 50 subpeticiones externas por invocación. El flujo de contactos se mantiene por debajo de ese máximo: consulta privada, reserva de cuota, una consulta por hashes y hasta diez candidatos con comprobación de bloqueo. Las cuotas de Firestore y FCM son independientes.

## Requisitos

1. Cuenta gratuita de Cloudflare con `workers.dev` habilitado.
2. Proyecto Firebase `pawly-83dca`.
3. Número de proyecto Firebase (Firebase Console → Configuración del proyecto → General).
4. Cuenta de servicio Google dedicada al Worker, con permisos mínimos para leer/escribir Firestore y enviar FCM.
5. Cuenta Cloudinary existente; el Worker no necesita Cloudinary para las funciones actuales.

## Configuración y despliegue

Desde la raíz del repositorio:

```bash
cd backend/cloudflare-worker
npm ci
npx wrangler login
npx wrangler secret put FIREBASE_CLIENT_EMAIL
npx wrangler secret put FIREBASE_PRIVATE_KEY
npx wrangler secret put PHONE_DISCOVERY_HMAC_SECRET
npm run check
npm test
npm run deploy
```

Genera el secreto HMAC localmente con `openssl rand -hex 32` y pégalo cuando Wrangler lo solicite. No lo envíes por chat ni lo guardes en GitHub. La clave privada de Firebase tampoco debe salir de Cloudflare.

Configura estas variables en `wrangler.toml` o en Cloudflare Workers → Settings → Variables:

| Nombre | Tipo | Valor |
|---|---|---|
| `FIREBASE_PROJECT_ID` | Variable | `pawly-83dca` |
| `FIREBASE_PROJECT_NUMBER` | Variable | Número de proyecto Firebase, no el ID |
| `FIREBASE_CLIENT_EMAIL` | Secreto | Correo de la cuenta de servicio |
| `FIREBASE_PRIVATE_KEY` | Secreto | Clave privada PEM de la cuenta de servicio |
| `PHONE_DISCOVERY_HMAC_SECRET` | Secreto | Secreto aleatorio de al menos 32 caracteres |

No configures `ALLOW_PNV_TEST_TOKENS=true` en el Worker de producción. Solo se permite en un Worker de pruebas aislado y temporal para aceptar los tokens SIM-less ficticios de desarrollo. Los tokens de prueba incluyen `test_number=true` y el Worker de producción los rechaza.

Tras el despliegue, Wrangler mostrará una URL similar a:

```
https://pawly-api.<tu-subdominio>.workers.dev
```

Comprueba:

```bash
curl https://pawly-api.<tu-subdominio>.workers.dev/health
```

La respuesta esperada es `{"service":"pawly-api","status":"ok"}`.

## Conectar Android

Añade a `local.properties` en la raíz del proyecto:

```properties
PAWLY_BACKEND_URL=https://pawly-api.<tu-subdominio>.workers.dev
```

No uses la URL ficticia del ejemplo: sustituye por la URL exacta que devuelva Wrangler. Sin este valor, las acciones seguras del Worker mostrarán un error de configuración y no fingirán que se han completado.

## Verificación telefónica sin costes sorpresa

- Para pruebas locales, genera un token SIM-less en Firebase Console → Security → Phone Verification → Testing y guárdalo en `local.properties` como `FPNV_TEST_TOKEN=...`. El token dura 7 días, no requiere SIM ni facturación y solo sirve para probar el flujo con un número ficticio.
- En debug, la app usa ese token solo si está configurado. En release el token siempre se compila vacío.
- La verificación real de Firebase PNV en producción es facturable. Por decisión del proyecto, `PHONE_VERIFICATION_PRODUCTION_ENABLED` permanece en `false`; no se inicia una verificación real ni se generan cargos.
- Si en el futuro se autoriza explícitamente el coste, se podrá habilitar en `local.properties` con `PHONE_VERIFICATION_PRODUCTION_ENABLED=true` en una build release. El Worker seguirá rechazando tokens de prueba.
- La búsqueda solo funciona si el usuario verifica su propio número y activa el consentimiento. El otro usuario también debe haberlo activado. La app consulta el número seleccionado en el selector de Android; no lee ni sube toda la agenda.

## Lugares de la comunidad

La app llama a `POST /v1/community-places`. El Worker verifica la cuenta, valida nombre/categoría/dirección/coordenadas y limita el envío a una propuesta diaria. Se crea como `pending`; el equipo moderador decide aprobar o rechazar. El autor puede editar y eliminar su propuesta aprobada o pendiente y denunciar lugares de otros usuarios.

Configura TTL de Firestore sobre `communityPlaceSubmissionLimits.expiresAt` para limpiar límites de 90 días, `phoneDiscoveryLimits.expiresAt` para limpiar reservas de búsqueda de 45 días y `phoneVerificationTokens.expiresAt` para eliminar tokens PNV consumidos al caducar.

## Eventos y privacidad

La inscripción a eventos sigue disponible. El documento de inscripción individual solo puede consultarlo su propietario; Firestore niega la enumeración directa. La lista se consulta mediante el Worker, que verifica que el solicitante sea el organizador, pagina 12 asistentes por petición y filtra los bloqueos en ambos sentidos. Así se mantiene por debajo del límite de 50 subpeticiones externas de Workers Free.

## Eliminación de cuenta

El borrado integral de cuenta todavía no está implementado. La app registra una solicitud y las nuevas subidas multimedia se indexan por propietario, pero aún falta eliminar de forma idempotente todas las subcolecciones Firestore, recursos Cloudinary y la cuenta Firebase Authentication. No presentar Pawly como listo para producción hasta completar y probar esa cascada. Consulta `docs/ACCOUNT_DELETION_OPERATIONS.md`.
