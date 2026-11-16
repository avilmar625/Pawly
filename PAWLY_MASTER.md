# PAWLY_MASTER.md

> Documento maestro de continuidad técnica. Última auditoría documental: 2026-11-16.
> Este documento distingue **presencia en código**, **configuración externa** y **validación real**. Una función no se considera terminada solo porque existan pantallas o clases.

## 1. Identidad y reglas del proyecto

- **Producto:** Pawly — aplicación para organizar la vida con animales de compañía: cuidados, salud, recuerdos, aprendizaje, lugares, eventos y comunidad.
- **Creadores:** Alex Vilarrubla Martín y Maialen Cruz Tudanca.
- **Repositorio:** https://github.com/avilmar625/Pawly
- **Application ID Android:** `com.pawly.app`.
- **Firebase:** proyecto `pawly-83dca` (Pawly).
- **Principios no negociables:**
  1. No eliminar, ocultar ni sustituir funcionalidades existentes para resolver errores o simplificar pantallas.
  2. Antes de tocar código, identificar rama base, cambios previos y posibles regresiones.
  3. No afirmar que compila, funciona, sincroniza o está desplegado sin evidencia verificable.
  4. Mantener compatibilidad con los datos locales existentes y el esquema Firestore; cualquier migración debe ser explícita, reversible y probada.
  5. Preferir cambios amplios y coherentes, con pruebas y documentación, frente a parches aislados.
  6. Conservar Android e iOS como aplicaciones nativas mientras no exista una migración KMP deliberada.
  7. Respetar la preferencia de costes: servicios gratuitos y sin activar Firebase Blaze, Cloud Functions de pago o Workers Paid sin autorización expresa.
  8. No subir secretos, tokens, archivos de configuración privados ni credenciales al repositorio.

## 2. Estado Git comprobado el 2026-11-16

### Rama Android de trabajo más avanzada localizada

- `cambios64-funciones-integradas-hoy` es la rama con mayor avance documental y de código encontrada para Android.
- Comparación GitHub: está **44 commits por delante de `main`**, sin commits de `main` por delante; su base común coincide con el HEAD de `main` en el momento de la consulta.
- También está **43 commits por delante de `cambios63-consolidacion-main`**, sin divergencia respecto a esa base.
- `cambios63-consolidacion-main` es, por tanto, una base anterior; no debe confundirse con la rama de trabajo más reciente.
- `main` no contiene todavía esos 44 commits de trabajo. No se ha hecho merge ni se debe asumir que `main` es la versión más completa.
- La actividad más reciente visible en `main` incluye commits del 16-11-2026 sobre límites de Cloudflare para alertas de mascotas perdidas y un ajuste de texto de error de eventos. La rama cambios64 incluye trabajo adicional posterior a la base común.

**Decisión de continuidad:** usar `cambios64-funciones-integradas-hoy` como rama de auditoría y desarrollo Android provisional. No fusionar ni mover referencias hasta revisar CI, comparar los archivos afectados y confirmar compilación/pruebas.

### Otras ramas importantes

- `cambios56-arquitectura`: hito de arquitectura y endurecimiento de seguridad; contratos iniciales, pruebas y documentación.
- `cambios57`, `cambios58-integracion`, `cambios59-experiencia`, `cambios60-comunidad-calendario`, `cambios61-lugares-comunidad-eventos`, `cambios62-auditoria-gratis-seguridad`, `cambios63-consolidacion-main`: evolución incremental de integración, experiencia, comunidad, calendario, lugares, eventos, seguridad y consolidación.
- `integracion-cambios57`: rama auxiliar de integración; no asumir que sea la principal.
- `ios-funciones`: port iOS SwiftUI con una cobertura funcional amplia en su propia rama, pero divergente y basado en una historia Android anterior. No es una continuación directa de cambios64.
- `ios`: rama iOS anterior, también divergente.
- `ios-iphone`: prototipo SwiftUI inicial, con Inicio/Explorar/Salud/Diario/Comunidad de alcance local y menos completo.
- Existen además ramas históricas `cambios1` a `cambios64...`. No borrar ramas históricas como limpieza automática.

## 3. Arquitectura Android actual (cambios64)

Aplicación Android nativa con Kotlin, Jetpack Compose y Navigation Compose. El repositorio contiene un único módulo Gradle `:app`; todavía no existe un módulo Kotlin Multiplatform compartido.

### Entrada y presentación

- `MainActivity.kt`: punto de entrada, reducido frente a la implementación inicial monolítica.
- `PawlyApplication.kt`: inicialización de aplicación.
- `PawlyApp.kt`: composición raíz, navegación, estado de aplicación y parte de la coordinación/persistencia.
- `PawlyTheme.kt`, `PawlySettings.kt`, `PawlyI18n.kt`: tema, preferencias y textos/traducciones.
- `PawlyOnboarding.kt`: onboarding.
- `ui/home/HomeScreen.kt`, `ui/explore/ExploreScreen.kt`, `ui/health/HealthScreen.kt`, `ui/diary/DiaryScreen.kt`, `ui/profile/ProfileDialogs.kt`: pantallas por área.
- `MoreHubScreen.kt`, `PetCareHubScreen.kt`, `PawlyCalendarScreen.kt`, `PawlyEvents.kt`, `LostPetHelpScreen.kt`, `ProfessionalServicesScreen.kt`: hubs y funcionalidades especializadas.

### Dominio, datos e infraestructura

- `care/model/PetCareModels.kt`: modelos de cuidados.
- `care/domain/PetCareRepository.kt`: contrato de dominio para cuidados.
- `care/data/FirebasePetCareRepository.kt`, `FirebasePetProfileRepository.kt`, `PetCareLocalStore.kt`, `SharedPetRepository.kt`: adaptadores Firebase/local y datos compartidos.
- `care/CareReminderScheduler.kt` y `CareReminderDateTimeParser.kt`: recordatorios locales y análisis de fechas.
- `care/export/PetCarePdfExporter.kt`, `LostPetPosterExporter.kt`: exportación PDF/cartel de mascota perdida.
- `community/`: modelos, pantallas, políticas, validación, mensajería, moderación, Firebase y subida Cloudinary.
- `content/`: noticias RSS, biblioteca educativa, modelos y artículos guardados.
- `backend/cloudflare-worker/`: backend HTTP de confianza para operaciones que no deben ejecutarse desde el cliente.

### Deuda arquitectónica confirmada

- `PawlyApp.kt` todavía concentra navegación, estado global y parte de la persistencia.
- `FirebaseCommunityRepository.kt` sigue siendo una clase grande con varias responsabilidades.
- Parte del estado y almacenamiento siguen siendo locales; no todo se sincroniza entre dispositivos.
- No hay todavía ViewModels/repositorios uniformes para todas las áreas.
- No hay módulo KMP ni una capa compartida de dominio probada con iOS.
- No migrar a Room ni cambiar claves locales sin una migración compatible: hay datos locales históricos (incluidas claves v16 de cuidados).

## 4. Arquitectura iOS: ramas separadas y estado real

No existe una aplicación iOS integrada en `cambios64-funciones-integradas-hoy`: no se encontraron `ios/`, `iosApp/` ni `shared/` en esa rama.

Hay dos líneas de trabajo iOS identificadas:

### `ios-funciones` — port funcional SwiftUI

La documentación y el código inspeccionados incluyen:
- SwiftUI, iOS mínimo 16, XcodeGen (`ios/project.yml`), bundle ID `com.pawly.ios`.
- Firebase Apple SDK 12.x y GoogleSignIn 8.x.
- Vistas y stores separados: `PawlyApp`, `AuthenticationView`, `PawlySession`, `PawlyTabView`, `PawlyCommunityStore`, `PawlyPetStore`, `PawlyReminderStore`, vistas de funciones/paridad, Cloudinary uploader y PDF exporter.
- La documentación declara login/registro/verificación/recuperación, Google, perfiles multi-mascota, compartir mascotas, pasaporte, rutinas, diario, PDF, comunidad, mensajería, Explorar, Aprender, recordatorios y ajustes.
- Se encontró workflow `.github/workflows/ios.yml` para compilar simulador en macOS y resolver paquetes.

**No verificado:** que el workflow esté verde en el último commit, que el proyecto compile con Xcode actual, que todas las pantallas declaradas funcionen extremo a extremo, ni que Firebase/Google/Cloudinary/APNs estén configurados en la cuenta real. La propia documentación de esa rama reconoce traducciones incompletas, recordatorios locales no sincronizados y dependencias de backend para push y borrado.

### `ios-iphone` — prototipo anterior

- Usa `iosApp/`, SwiftUI, XcodeGen y iOS 17.
- README lo describe como prototipo local; comunidad, sincronización, fotos y notificaciones no se consideran funcionales en esa línea.
- No debe confundirse con `ios-funciones`.

### Decisión iOS

No hacer merge directo de `ios-funciones` con cambios64: el historial está divergente y puede sobrescribir o perder mejoras Android recientes. La futura consolidación debe portar por funcionalidades, comparar esquemas y reglas Firestore, preservar el trabajo iOS y probarlo en Xcode/iPhone. No convertir la app Android a KMP de golpe.

## 5. Funcionalidades Android: inventario documental

La siguiente tabla refleja código/documentación localizado, no una certificación funcional.

| Área | Presencia localizada | Pendiente de validar |
|---|---|---|
| Onboarding y perfiles de mascotas | Onboarding, perfiles multi-mascota y selector; persistencia de selección | Reapertura, cambio de usuario, edición/borrado y sincronización |
| Inicio y rutina diaria | Inicio, accesos rápidos, tareas y selección de mascota | Persistencia real y que no vuelva a seleccionar Nala/mascota por defecto |
| Salud y pasaporte | Agenda, pasaporte, cuidados, recordatorios y PDF | Sincronización completa, alarmas, permisos y casos límite |
| Diario | Pantalla y datos compartidos/locales según ruta | Prueba de persistencia, edición, adjuntos y acceso multi-dispositivo |
| Explorar y lugares | Búsqueda OSM, categorías, favoritos/colecciones, lugares propuestos | Ubicación real, teléfono/llamada, enlaces, moderación y todos los destinos clicables |
| Eventos y calendario | Crear/listar eventos, fecha estructurada, interés y asistentes | Registro real de asistentes, edición, permisos, denuncias y fechas/zona horaria |
| Comunidad | Feed, categorías, perfiles, follows, likes, comentarios, búsqueda, bloqueos y denuncias | Registro/login, reglas, sincronización y flujos con dos cuentas |
| Mensajería | Conversaciones, mensajes, adjuntos, contadores/lecturas | Envío/recepción real, FCM, adjuntos y restricciones entre usuarios |
| Mascotas perdidas | Publicaciones LOST, ayuda/cartel y alertas por radio vía Worker | Worker desplegado, geolocalización aproximada y entrega push |
| Contactos por teléfono | Política/índice HMAC y consentimiento mutuo; PNV de pruebas | Verificación real; producción desactivada para evitar coste SMS |
| Profesionales | Directorio y datos públicos con política de contacto | Verificación profesional real; no presentar autodeclaraciones como verificadas |
| Aprender | RSS/noticias, biblioteca, guías y artículos guardados | Enlaces, contenido y traducción completa |
| Ajustes y apariencia | Tema oscuro, paletas, simple/elegante, idioma | Persistencia, traducciones exhaustivas y consistencia entre pantallas |
| Exportaciones | PDF de cuidados y cartel de mascota perdida | Permisos, contenido final y pruebas en dispositivos |
| Copias de seguridad | `PawlyBackupManager.kt` presente en cambios64 | Restauración real, cobertura de datos y pruebas de pérdida/reinstalación |

### Requisitos de producto que deben preservarse

- No quitar eventos, lugares, colecciones, llamadas/búsqueda de teléfonos, navegación a fichas ni acciones de Explorar.
- Las tarjetas y categorías de Explorar deben llevar a resultados o acciones reales, no ser elementos decorativos.
- Mantener perfiles de distintas especies, perfiles de personas/mascotas, comunidad, chat, salud, calendario, diario, aprendizaje, profesionales, avisos y moderación.
- Corregir duplicidades de navegación (por ejemplo, Salud repetida en Más) sin suprimir la función original.
- Mantener selección de mascota persistente y aislada por cuenta.
- Mantener opciones visuales simple/elegante y personalización.

## 6. Errores conocidos y pruebas fallidas comunicadas

### Registro y login Android — BLOQUEANTE ACTUAL

- En las pruebas comunicadas el 15-11-2026, crear cuenta y login seguían fallando.
- En cambios64 se documenta una corrección de reglas Firestore: cambiar `exists()` por `existsAfter()` al comprobar una reserva de username creada en el mismo batch atómico (`usernameReservations/{uid}`, `usernames/{username}`, `users/{uid}`).
- El propio documento de cambios64 indica que la confirmación requiere probar Firebase Authentication + Firestore real y Firebase Emulator Suite.
- Por tanto, la corrección está presente en el código/reglas pero **no se declara resuelta** hasta repetir registro/login y revisar Logcat y reglas desplegadas en el proyecto correcto.
- Revisar también disponibilidad de username, verificación de correo, escritura del perfil, sesión persistida y estados de error. No relajar reglas de Firestore para “hacer que funcione”.

### Google Credential Manager / emulador

Errores comunicados previamente:
- `No credentials available`.
- `GetCredentialCancellationException: [16] Account reauth failed`.
- `Cached Google account reauth failed; clearing credential state and retrying once`.
- Mensaje de acceso Google cancelado antes de terminar.

La guía de emulador indica usar una imagen **Google Play**, añadir una cuenta al emulador, comprobar SHA-1 debug y volver a descargar `google-services.json` con el cliente OAuth Web correcto. Esto es una hipótesis de configuración a comprobar, no una solución confirmada. También hay que probar en el Redmi Note 8 Pro y distinguir cancelación del usuario de fallo OAuth.

### Cloudinary

- En pruebas iOS de la rama anterior se comunicó el error `Configura CLOUDINARY_CLOUD_NAME` / preset no configurado al publicar historias/fotos y en Comunidad.
- Android requiere `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_IMAGE_UPLOAD_PRESET` y `CLOUDINARY_VIDEO_UPLOAD_PRESET` en ajustes de servicios multimedia o `local.properties`.
- Los presets unsigned son visibles y no son un mecanismo de privacidad; no incluir API Secret en la app.
- La carga real debe probarse con imagen y vídeo, y comprobar URLs persistidas.

### Salud / conexión

- En iOS se comunicó “Sin conexión: se conserva la copia local”. Debe comprobarse qué datos son locales, cuáles remotos y cómo se resuelven conflictos; no afirmar sincronización completa.

### Interfaz / persistencia

- Incidencia comunicada: Inicio vuelve a Nala al reabrir en lugar de conservar la última mascota seleccionada.
- Incidencia comunicada: Salud aparece duplicada en Más.
- Revisar sin retirar accesos o funcionalidades.

## 7. Configuración, servicios y secretos

### Android build

Valores observados en `app/build.gradle.kts` de cambios64:
- Namespace/Application ID: `com.pawly.app`.
- Min SDK 26.
- Target SDK por defecto 36; configurable mediante Gradle property.
- Compile SDK por defecto 37 en el archivo actual; configurable mediante `pawlyCompileSdk`. Confirmar SDK instalado/CI.
- JDK 17, Kotlin JVM target 17.
- Gradle Wrapper documentado como 9.6.0; AGP 9.4.0 y Kotlin Compose plugin 2.4.21 en notas técnicas. Verificar wrapper/plugin exactos en una compilación limpia.
- Compose BOM 2026.09.00, Navigation Compose 2.10.2, Firebase BOM 35.1.0, Firebase Auth/Firestore/Messaging/PNV, Credentials/Google ID, OkHttp 4.12.0, osmdroid 6.1.20, Coil 3.3.0, coroutines Play Services 1.10.2.
- No se localizó un catálogo `gradle/libs.versions.toml`; las dependencias están declaradas directamente en el módulo.

### Firebase

- Proyecto: `pawly-83dca`.
- Android config local: `app/google-services.json`, no subir a Git.
- Auth: correo/contraseña y Google.
- Firestore: reglas e índices versionados; despliegue manual con Firebase CLI.
- FCM: cliente registra token; push saliente requiere Worker/servicio de confianza.
- Firebase Storage no se usa; no activar Blaze para almacenar fotos.
- La configuración iOS requiere registrar app iOS separada y usar su propio `GoogleService-Info.plist`, nunca reutilizar el JSON Android.

### Cloudinary

- Almacena fotos/vídeos de comunidad.
- Presets unsigned separados para imágenes y vídeos.
- Configuración Android por ajustes o `local.properties`; iOS por build settings.
- No guardar API Secret en cliente. URLs de medios pueden ser públicas para quien las conozca.
- Para producción pública, valorar firma de subida y controles de abuso mediante backend seguro.

### Cloudflare Worker

- Fuente en `backend/cloudflare-worker/`; TypeScript/Workers, configuración Wrangler.
- Funciones documentadas: notificaciones de actividad, avisos de mascotas perdidas, propuestas de lugares y operaciones auxiliares.
- Requiere desplegar en una cuenta Cloudflare, configurar secretos de cuenta de servicio Firebase y URL real en Android.
- El README/guía dicen que el Worker no se considera desplegado por el mero hecho de estar en Git.
- Mantener Workers Free y sus límites; no activar Workers Paid sin autorización.

### Firebase Functions (rama iOS-funciones)

La documentación de `ios-funciones` incluye `functions/` para push y eliminación integral, pero requiere Node 20, secretos Cloudinary y **plan Blaze**. Está expresamente sin desplegar y no encaja con la preferencia actual de no activar facturación. No desplegar ni activar Blaze sin aprobación explícita y análisis de coste.

### Archivos locales que nunca se deben compartir/subir

- `local.properties`, `app/google-services.json`, `GoogleService-Info.plist`.
- Keystores, passwords, OAuth secrets, Firebase service-account JSON/private key.
- Cloudinary API Secret, tokens Cloudflare/Wrangler, claves APNs.
- `local.properties.example` sí puede versionarse si contiene solo nombres/valores de ejemplo vacíos.

## 8. Backend, privacidad y riesgos pendientes

- **Borrado integral de cuenta:** el checklist actual lo considera bloqueante; la app puede registrar una solicitud, pero no hay prueba de borrado completo de Firebase Auth, Firestore, conversaciones, tokens y medios Cloudinary en el backend gratuito actual.
- **Push salientes:** requieren Worker desplegado y probado; el token registrado por sí solo no envía notificaciones.
- **Cloudinary unsigned:** no proporciona privacidad ni protección robusta contra abuso.
- **Contactos por teléfono:** producción permanece desactivada; no activar SMS facturable.
- **Profesionales:** autodeclaración no equivale a verificación.
- **OpenStreetMap:** revisar política de teselas y proveedor antes de escalar tráfico.
- **RGPD/legal:** política de privacidad y términos son borradores; completar datos del responsable, retención, derechos, contratos de proveedores y recursos externos de eliminación.
- **Moderación:** definir responsable, tiempos y proceso de apelación.
- No publicar como lista para producción hasta cerrar bloqueantes de privacidad, eliminación, seguridad, soporte y pruebas.

## 9. Documentación de referencia ya presente en el repositorio

- `README.md`: resumen de producto y funcionalidades.
- `docs/ARCHITECTURE.md`: arquitectura objetivo Android + SwiftUI + KMP futuro.
- `docs/TECHNICAL_NOTES.md`: configuración y notas técnicas Android.
- `docs/FIREBASE_SETUP.md`: Firebase y Cloudinary.
- `docs/CLOUDFLARE_SETUP.md`: despliegue Worker.
- `docs/ANDROID_EMULATOR.md`: pruebas en emulador.
- `docs/CAMBIOS56.md` a `docs/CAMBIOS64.md`: registros de cambios.
- `docs/RELEASE_CHECKLIST.md`: publicación y bloqueantes.
- `docs/IOS_PORT_PLAN.md` y `ios/README.md` en las ramas iOS correspondientes.
- `docs/COMMUNITY_GUIDELINES.md`, borradores de privacidad y términos.

## 10. Próximo plan de trabajo recomendado

### Fase 0 — congelar el punto de partida (sin cambios funcionales)
1. Checkout de `cambios64-funciones-integradas-hoy`.
2. Registrar SHA exacto de HEAD y comparar con `main`, `cambios63` y ramas iOS.
3. Revisar estado de GitHub Actions y resultados de tests asociados al HEAD exacto.
4. Ejecutar en limpio: `./gradlew testDebugUnitTest lintDebug assembleDebug`; validar también el build release sin firma.
5. En `backend/cloudflare-worker`: `npm ci`, `npm run check`, `npm test`.
6. Ejecutar Firebase Emulator Suite para reglas de registro, usuarios, comunidad, lugares, eventos, mensajes y mascotas compartidas.
7. No modificar reglas/servicios reales hasta confirmar el proyecto Firebase activo.

### Fase 1 — solucionar autenticación sin regresiones
1. Reproducir registro con correo nuevo y username nuevo.
2. Capturar el error completo en Auth/Firestore/Logcat y reglas Emulator.
3. Probar batch y reglas `existsAfter/getAfter`.
4. Probar login, logout, recuperación, verificación y persistencia de sesión.
5. Probar Google en emulador Google Play y Redmi; confirmar SHA-1/OAuth Web client.
6. Añadir tests de regresión y registrar resultado exacto.

### Fase 2 — auditoría funcional integral
Probar todas las áreas del apartado 5 con checklist por flujo, cuenta, dispositivo, red y resultado. No cerrar una función solo por inspección de código.

### Fase 3 — integración iOS segura
1. Auditar HEAD y CI de `ios-funciones`.
2. Comparar modelos, rutas Firestore, reglas y nombres de campos con cambios64.
3. Crear rama de integración desde cambios64.
4. Portar por funcionalidades y adaptadores SwiftUI, preservando el código Android.
5. Compilar en Xcode, instalar en iPhone y probar Firebase/Google/Cloudinary con dos cuentas.
6. Mantener las funciones sin equivalencia iOS visibles como pendientes, nunca fingir paridad.

### Fase 4 — arquitectura y calidad
- Extraer ViewModels/casos de uso/repositorios gradualmente.
- Separar `FirebaseCommunityRepository` en servicios pequeños manteniendo contratos.
- Aumentar tests unitarios, de reglas y de integración.
- Evaluar KMP solo cuando los límites de dominio sean estables.
- Corregir persistencia de mascota seleccionada, navegación duplicada y traducciones sin quitar funcionalidades.

## 11. Registro de verificación

| Elemento | Estado a 2026-11-16 |
|---|---|
| Repositorio y ramas identificados | Verificado mediante GitHub |
| cambios64 por delante de main y cambios63 | Verificado por comparación de commits |
| Código Android modularizado parcialmente | Verificado por archivos y documentación |
| Compilación Android del HEAD cambios64 | Pendiente de ejecutar/confirmar |
| CI verde del HEAD cambios64 | Pendiente de comprobar |
| Registro/login corregidos | No confirmado; usuario reportó fallos el 15-11 |
| Google Sign-In Android | Pendiente; errores previos en emulador |
| Cloudinary Android/iOS | Configuración y pruebas reales pendientes |
| iOS SwiftUI en ios-funciones | Código localizado; compilación/paridad no certificadas |
| iOS integrado en cambios64 | No existe |
| Worker Cloudflare desplegado | No confirmado; documentación dice que requiere despliegue |
| Firebase Storage / Blaze | No usar por defecto |
| Borrado integral de cuenta | Pendiente/bloqueante |
| Pruebas completas de todas las funciones | Pendientes |

---

## 12. Protocolo para cada nueva conversación de Pawly

1. Leer primero este archivo y los últimos registros `docs/CAMBIOS*.md`.
2. Consultar GitHub para conocer HEAD, ramas y CI actuales; este documento puede quedar desactualizado.
3. Declarar explícitamente rama base, objetivo, archivos afectados, riesgos y pruebas previstas.
4. No modificar código hasta completar la revisión inicial solicitada para esa tarea.
5. Implementar el conjunto coherente de cambios sin eliminar funciones.
6. Ejecutar pruebas disponibles y distinguir:
   - inspeccionado en código;
   - compilado;
   - tests automatizados pasados;
   - probado en emulador;
   - probado en dispositivo real;
   - servicio externo desplegado y probado.
7. Actualizar este documento y crear un registro `docs/CAMBIOSxx.md` con cambios, archivos, pruebas, resultados y pendientes.
8. Informar al usuario de la rama exacta, commit, estado CI, pruebas realizadas y qué debe probar personalmente.

**Regla final:** “implementado” no significa “verificado”; “compila” no significa “funciona”; “está en GitHub” no significa “está desplegado”.
