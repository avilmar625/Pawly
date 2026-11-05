# Pawly 🐾

**Más aventuras. Más recuerdos. Más vida juntos.**

Pawly es una aplicación Android para organizar la vida cotidiana con perros: rutinas, cuidados, ideas para salir y recuerdos compartidos.

> Estado: proyecto en desarrollo. La interfaz principal está implementada en Jetpack Compose. Parte del contenido sigue siendo demostrativo y las funciones que no estén conectadas a almacenamiento o servicios externos se indican expresamente en la aplicación.

## Creadores

- **Alex Vilarrubla Martín**
- **Maialen Cruz Tudanca**

## Funcionalidades actuales

- **Inicio:** selector para varios perros, perfil editable con foto de cámara o galería, tareas cotidianas y resumen actualizado.
- **Explorar:** búsqueda y filtros por categorías, con ideas genéricas y favoritos guardados en el dispositivo.
- **Salud:** fechas locales para vacunas, desparasitación y revisiones, editables y separadas por perfil; no ofrece pautas veterinarias ni notificaciones automáticas.
- **Diario:** recuerdos de texto persistentes, con recuento actualizado.
- **Persistencia local:** perfil, tareas, favoritos y diario se conservan entre sesiones en el dispositivo; no hay sincronización ni cuenta.

## Organización del código

La aplicación se organiza por áreas funcionales y responsabilidades para facilitar su mantenimiento y crecimiento:

- `MainActivity.kt`: punto de entrada Android.
- `PawlyApp.kt`: estado de aplicación y navegación principal.
- `ui/home`: pantalla de inicio y componentes visuales compartidos.
- `ui/explore`: exploración, favoritos y ubicación.
- `ui/health`: agenda de cuidados.
- `ui/diary`: diario.
- `ui/profile`: diálogos y edición de perfiles.
- `content`: modelos, lectura RSS, biblioteca educativa y pantalla de actualidad.

Esta separación es un primer paso de refactorización; la lógica de estado y persistencia todavía está en la capa de presentación y será extraída a ViewModels y repositorios en una fase posterior.

## Comunidad (ramas cambios11, cambios12 y cambios13)

La rama `cambios11` incorpora la comunidad y `cambios12` continúa su estabilización. Incluye Firebase Authentication (correo/contraseña y Google), perfiles de personas y perros, seguimiento, publicaciones, likes, comentarios, conversaciones, bloqueo y denuncias. Las pantallas están separadas por áreas (`CommunityFeedScreens`, `CommunityPeopleScreens`, `CommunityProfileScreens`, `CommunityMessagingScreens` y `CommunityModerationScreens`); modelos, validaciones, mensajes de error y carga de imágenes también tienen ficheros propios. Las fotografías se alojan en Cloudinary mediante un preset unsigned. Firebase Cloud Messaging registra los dispositivos, pero el envío automático de notificaciones requiere un backend de confianza. No requiere Firebase Storage ni el plan Blaze. Consulta [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md).

## Aprender y descubrir (cambios13)

La nueva pestaña **Aprender** reúne cinco áreas: noticias caninas, adopción y protección animal, educación y comportamiento, búsquedas temáticas de vídeos y guías prácticas de Pawly. Las noticias se consultan bajo demanda mediante el feed RSS público de Google News y enlazan a la publicación original; se requiere conexión a Internet. Los artículos guardados se conservan localmente. El directorio enlaza con entidades externas (por ejemplo, Fundación Affinity, FAADA, El Refugio, De Vuelta a Casa, Fundació Gossos, Doogweb y RSCE). Los vídeos abren búsquedas temáticas de YouTube, no son vídeos alojados ni verificados por Pawly. Las guías son material introductorio y no sustituyen asesoramiento veterinario o profesional.

## Comunidad de ayuda y cuidados (cambios14)

La comunidad permite clasificar publicaciones como historia, adopción, perro perdido, perro encontrado o casa de acogida. El muro incorpora filtros por categoría y las publicaciones mantienen likes, comentarios, perfiles y moderación. Los avisos son publicaciones comunitarias: no equivalen a un servicio oficial de emergencias ni garantizan una respuesta inmediata. Se recomienda compartir solo zonas aproximadas y continuar la coordinación por mensaje privado.

Explorar añade búsquedas directas en Google Maps para parques caninos, protectoras, veterinarios y urgencias veterinarias, playas, restaurantes, alojamientos y rutas. La ubicación solo se solicita cuando la persona pulsa el botón correspondiente; se usa para centrar la búsqueda en el mapa y no se guarda como historial. Los resultados, horarios y condiciones pertenecen a los proveedores externos y deben confirmarse antes de desplazarse.

Salud permite programar avisos locales con fecha y hora para vacunación, desparasitación y revisiones. Android puede retrasarlos por ahorro de batería; los avisos se vuelven a programar al abrir Pawly. Tras reinstalar la app o borrar sus datos habrá que configurarlos de nuevo. Las fechas y pautas sanitarias deben proceder del veterinario.

Las reglas de Firestore han cambiado para validar las nuevas categorías. Despliega la versión actualizada antes de probar las publicaciones: `firebase deploy --only firestore:rules`. El envío automático de push entre usuarios sigue necesitando un backend de confianza; no se simula desde el cliente ni se expone una clave de servidor.

## Tecnología

- Kotlin y Android nativo.
- Jetpack Compose y Material 3.
- Navigation Compose.
- Gradle Kotlin DSL.
- Java 17.
- Android Gradle Plugin 9.4.0 y Kotlin Compose plugin 2.4.21.

## Requisitos

- Android Studio compatible con Android Gradle Plugin 9.4.0.
- JDK 17.
- Android SDK Platform 37 Preview y Build Tools 37.0.0.
- Android 8.0 (API 26) o superior para ejecutar la aplicación.

## Abrir y compilar

1. Clona el repositorio y abre la carpeta raíz en Android Studio.
2. En **Settings → Build, Execution, Deployment → Build Tools → Gradle**, selecciona **Gradle Wrapper** y JDK 17.
3. Sincroniza el proyecto y espera a que se descarguen Gradle y las dependencias.
4. Selecciona la configuración `app` y ejecuta en un emulador o dispositivo.

En macOS/Linux:

```bash
chmod +x gradlew
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

En Windows:

```bat
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Transparencia y privacidad

Los elementos de Explorar son ideas genéricas y no representan establecimientos verificados: no se consultan ubicaciones, horarios ni restricciones de acceso. Los recordatorios de Salud son ejemplos de organización, no recomendaciones veterinarias. La agenda de cuidados permite registrar fechas por perro, pero todavía no programa notificaciones automáticas. Los datos de perfiles, tareas, favoritos, agenda de salud y diario se guardan localmente en el almacenamiento privado de la app; no se sincronizan entre dispositivos y se excluyen de copias automáticas del sistema. Los datos de la Comunidad sí se sincronizan mediante Firebase.

No introduzcas información médica sensible. Antes de una publicación real habrá que completar la política de privacidad, validar el comportamiento en dispositivos y definir una estrategia de exportación o recuperación de datos, ya que actualmente no hay sincronización ni copia de seguridad.

## Configuración necesaria para usar la Comunidad

La pantalla de acceso se muestra aunque falte Firebase, pero el inicio de sesión real no puede funcionar hasta configurar el proyecto. Sigue [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md): añade `app/google-services.json` solo en tu copia local, activa Email/Password (y Google si lo quieres usar) en Firebase Authentication, despliega las reglas e índices de Firestore y configura el SHA-1 de la firma debug. Para subir fotos, añade `CLOUDINARY_CLOUD_NAME` y `CLOUDINARY_UPLOAD_PRESET` a `local.properties`. No publiques esos ficheros ni secretos.

Las cuentas creadas con correo reciben un enlace de verificación. Hasta verificarlo se permite entrar y completar el perfil, pero las operaciones sociales que modifican contenido (publicar, seguir, comentar, bloquear y enviar mensajes) permanecen protegidas.

## Próximas mejoras

1. Migrar la persistencia local a Room y extraer la lógica a ViewModels.
2. Guardar fotos y datos independientes para cada perro.
3. Añadir recordatorios locales configurables para los cuidados.
4. Incorporar mapas y lugares reales con fuentes y fecha de verificación.
5. Añadir un backend de confianza para avisos push de mensajes, además de pruebas automatizadas, accesibilidad y preparación de publicación.

## Principios de producto

- Priorizar la seguridad y el bienestar animal.
- No presentar como verificados datos que no lo estén.
- Explicar el origen y la fecha de actualización de la información.
- Recopilar únicamente los datos necesarios y dar control al usuario.
- No sustituir el criterio veterinario ni los servicios de emergencia.

---

© 2026 Alex Vilarrubla Martín y Maialen Cruz Tudanca. Todos los derechos reservados.
