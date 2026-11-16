# Lista de preparación para publicar Pawly

Estado de cambios17: **desarrollo / no lista para producción**. La rama añade comprobaciones de CI y documentación de configuración; no sustituye las pruebas en dispositivos ni los requisitos legales/operativos. Esta lista distingue lo que ya existe de lo que sigue siendo requisito.

## A. Build y distribución Android

- [x] `applicationId` estable: `com.pawly.app`.
- [x] `targetSdk = 36` (Android 16), alineado con el requisito de nuevas apps de Google Play desde el 31 de agosto de 2026.
- [x] CI configurado para cambios17: tests unitarios, Android Lint, APK debug y bundle release sin firmar.
- [x] CI usa SDK estable Android 16, no plataforma preview.
- [ ] Confirmar CI verde en el último commit de cambios17 (no marcar como completado hasta que GitHub Actions termine correctamente).
- [ ] Probar en móviles reales de distintos tamaños y versiones Android.
- [ ] Corregir crashes, ANR, errores de permisos, navegación y estados sin conexión.
- [x] CI construye un AAB release sin firmar como comprobación de compilación.
- [ ] Generar Android App Bundle (.aab) firmado para release; no distribuir el APK debug como publicación.
- [ ] Configurar Play App Signing y guardar de forma segura la clave de subida.
- [ ] Configurar versionCode/versionName y política de actualización.
- [ ] Revisar R8/minificación, tamaño, icono, splash, accesibilidad y rendimiento.

## B. Cuenta y presencia en Google Play

- [ ] Crear/verificar cuenta de Play Console y completar identidad y datos de contacto del desarrollador.
- [ ] Preparar nombre, descripción corta y larga, categoría, icono, capturas reales, gráfico de funciones y correo de soporte.
- [ ] Completar clasificación de contenido, audiencia objetivo y declaraciones de anuncios/compras.
- [ ] Añadir URL HTTPS de política de privacidad y recurso web externo de eliminación de cuenta.
- [ ] Completar Data safety y formularios de contenido con el comportamiento real del AAB.
- [ ] Facilitar credenciales e instrucciones de acceso al revisor si la app requiere inicio de sesión.
- [ ] Si la cuenta personal de Play Console se creó después del 13 de noviembre de 2023: completar una prueba cerrada con al menos 12 testers que permanezcan inscritos continuamente durante 14 días y solicitar acceso a producción.
- [ ] Planificar lanzamiento gradual y monitorizar Android Vitals, reseñas y fallos.

## C. Privacidad, RGPD y cuentas

- [ ] Completar y revisar `PRIVACY_POLICY_DRAFT.md`; publicarla en URL estable y accesible.
- [ ] Completar y revisar `TERMS_OF_SERVICE_DRAFT.md`.
- [ ] Identificar responsable, domicilio y correos válidos.
- [ ] Verificar contratos, región y transferencias internacionales de Firebase y Cloudinary.
- [ ] Auditar permisos y SDK; se ha retirado Firebase Analytics no utilizado para reducir recopilación.
- [ ] Definir retención, exportación, derechos y respuesta a incidentes.
- [x] Añadir un mecanismo dentro de la app que registra solicitudes de eliminación de cuenta en Firestore.
- [ ] **Bloqueante:** automatizar la eliminación completa de Firebase Auth, documento de usuario, subcolecciones, publicaciones, comentarios, conversaciones/mensajes, tokens, nombre reservado y archivos Cloudinary.
- [ ] **Bloqueante:** publicar un recurso web externo para solicitar la eliminación y conectarlo al mismo proceso.
- [ ] No anunciar “eliminación completa” hasta verificar el borrado de todos los sistemas y proveedores.
- [ ] Añadir App Check y probar reglas de Firestore con Firebase Emulator Suite.
- [ ] Revisar el uso del preset unsigned de Cloudinary: límites, formatos, tamaño, abuso y costes.

## D. Comunidad y confianza

- [ ] Definir equipo/persona responsable de moderación y tiempos de respuesta.
- [ ] Probar bloqueo, denuncia, retirada de contenido y apelación.
- [x] Preparar un borrador de normas en `COMMUNITY_GUIDELINES.md`.
- [ ] Publicar las normas en la app y la web, y aprobar el procedimiento de moderación.
- [ ] Establecer un proceso para avisos de adopción, acogida, pérdida y hallazgo.
- [ ] No presentar publicaciones como protectoras verificadas ni alertas como servicio de emergencia.
- [ ] Configurar backend seguro para push de mensajes y alertas de proximidad; no enviar FCM desde el cliente.
- [ ] Añadir controles anti-spam, límites de frecuencia y protección de imágenes.
- [ ] Verificar que los usuarios pueden editar y eliminar su contenido según las reglas anunciadas.

## E. Calidad de producto

- [ ] Revisar que cada botón haga una acción real y que los estados de error/vacío/carga sean comprensibles.
- [ ] Eliminar datos de ejemplo, texto provisional y enlaces sin verificar.
- [ ] Revisar soporte multi-especie en todas las áreas, no solo en la ficha social.
- [ ] Probar onboarding, recuperación de contraseña, verificación de correo y acceso con Google.
- [ ] Probar pérdida de red, sesión caducada, permisos denegados y reinstalación.
- [ ] Comprobar notificaciones en Android 13+, ahorro de batería y cambio de zona horaria.
- [ ] Realizar beta con propietarios de perros, gatos y otras especies; incluir cuidadores y protectoras.
- [ ] Recoger feedback estructurado y resolver los problemas más frecuentes antes de abrir al público.

## F. Operación después del lanzamiento

- [ ] Publicar correo y canal de soporte con responsable real.
- [ ] Revisar denuncias, solicitudes de eliminación y consultas de privacidad periódicamente.
- [ ] Monitorizar crashes, errores de sincronización, uso de Firestore y Cloudinary.
- [ ] Definir copias, restauración, continuidad y respuesta a incidentes.
- [ ] Mantener un calendario de actualización y compatibilidad con nuevas versiones Android.

## Referencia de requisitos de Play

- Target API: las apps nuevas y actualizaciones deben apuntar a Android 16 / API 36 o superior desde el 31-08-2026.
- Prueba cerrada: para cuentas personales creadas después del 13-11-2023, Google exige 12 testers inscritos de forma continua durante 14 días antes de solicitar acceso a producción.

Consulta siempre la documentación oficial enlazada desde Google Play Console, ya que los formularios y políticas pueden cambiar.


## Notas de cambios17

- La separación de modelos y persistencia del pasaporte es parcial; todavía no se ha migrado a Room ni a ViewModels.
- `local.properties.example` es solo una plantilla. Los valores reales deben permanecer en el archivo local ignorado por Git.
- Firebase/Cloudinary siguen siendo opcionales para compilar la app base, pero obligatorios para probar las funciones remotas y la subida de imágenes.
- Las funciones de backend y eliminación completa de cuenta continúan siendo bloqueantes para producción.
