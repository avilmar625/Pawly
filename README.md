# Pawly 🐾

**Más aventuras. Más recuerdos. Más vida juntos.**

Pawly es una aplicación Android en desarrollo para organizar la vida cotidiana con animales de compañía: perfiles multi-especie, cuidados, aprendizaje, lugares, recuerdos y comunidad.

> Estado: proyecto en desarrollo. La interfaz principal está implementada en Jetpack Compose. Parte del contenido sigue siendo demostrativo y las funciones que no estén conectadas a almacenamiento o servicios externos se indican expresamente en la aplicación.

## Creadores

- **Alex Vilarrubla Martín**
- **Maialen Cruz Tudanca**

## Funcionalidades actuales

- **Inicio:** selector para varios perfiles, perfil editable con foto de cámara o galería, tareas cotidianas y resumen actualizado.
- **Explorar:** búsqueda y filtros por categorías, con ideas genéricas y favoritos guardados en el dispositivo.
- **Salud:** fechas por mascota y recordatorios locales con fecha y hora para vacunas, desparasitación y revisiones. Android puede retrasar avisos por ahorro de batería; no ofrece pautas veterinarias.
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

## Comunidad (ramas cambios11 a cambios15)

La rama `cambios11` incorpora la comunidad y `cambios12` continúa su estabilización. Incluye Firebase Authentication (correo/contraseña y Google), perfiles de personas y mascotas multi-especie, seguimiento, publicaciones, likes, comentarios, conversaciones, bloqueo y denuncias. Las pantallas están separadas por áreas (`CommunityFeedScreens`, `CommunityPeopleScreens`, `CommunityProfileScreens`, `CommunityMessagingScreens` y `CommunityModerationScreens`); modelos, validaciones, mensajes de error y carga de imágenes también tienen ficheros propios. Las fotografías se alojan en Cloudinary mediante un preset unsigned. Firebase Cloud Messaging registra los dispositivos, pero el envío automático de notificaciones requiere un backend de confianza. No requiere Firebase Storage ni el plan Blaze. Consulta [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md).

## Aprender y descubrir (cambios13)

La pestaña **Aprender** reúne cinco áreas: noticias caninas, adopción y protección animal, educación y comportamiento, búsquedas temáticas de vídeos y guías prácticas de Pawly. Las noticias se consultan bajo demanda mediante el feed RSS público de Google News y enlazan a la publicación original; se requiere conexión a Internet. Los artículos guardados se conservan localmente. El directorio enlaza con entidades externas (por ejemplo, Fundación Affinity, FAADA, El Refugio, De Vuelta a Casa, Fundació Gossos, Doogweb y RSCE). Los vídeos abren búsquedas temáticas de YouTube, no son vídeos alojados ni verificados por Pawly. Las guías son material introductorio y no sustituyen asesoramiento veterinario o profesional.

## Comunidad de ayuda y cuidados (cambios14)

La comunidad permite clasificar publicaciones como historia, adopción, mascota perdida, mascota encontrada o casa de acogida. Los perfiles admiten perro, gato, conejo, ave, roedor, reptil y otros animales. El muro incorpora filtros por categoría y las publicaciones mantienen likes, comentarios, perfiles y moderación. Los avisos son publicaciones comunitarias: no equivalen a un servicio oficial de emergencias ni garantizan una respuesta inmediata. Se recomienda compartir solo zonas aproximadas y continuar la coordinación por mensaje privado.

Explorar añade búsquedas directas en Google Maps para parques caninos, protectoras, veterinarios y urgencias veterinarias, playas, restaurantes, alojamientos y rutas. La ubicación solo se solicita cuando la persona pulsa el botón correspondiente; se usa para centrar la búsqueda en el mapa y no se guarda como historial. Los resultados, horarios y condiciones pertenecen a los proveedores externos y deben confirmarse antes de desplazarse.

Aprender añade guías de adopción responsable y actuación ante perros perdidos o encontrados. Si existe una ficha de mascota en Comunidad, muestra una selección de contenidos basada en la edad y características que la persona haya introducido. Salud permite programar avisos locales con fecha y hora para vacunación, desparasitación y revisiones. Android puede retrasarlos por ahorro de batería; los avisos se vuelven a programar al abrir Pawly. Tras reinstalar la app o borrar sus datos habrá que configurarlos de nuevo. Las fechas y pautas sanitarias deben proceder del veterinario.

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
- Android SDK Platform 36 (Android 16) y Build Tools 36.0.0.
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

## Pasaporte y organización de cuidados (cambios16–17)

La pestaña **Salud** se amplía con tres vistas:

- **Agenda:** conserva la agenda de recordatorios y cuidados existente.
- **Pasaporte:** permite registrar vacunas, desparasitaciones, medicación, visitas veterinarias, alergias, intervenciones, peso y otras notas con fecha. Los registros se guardan localmente por nombre de mascota en el dispositivo.
- **Cuidados:** permite crear rutinas, indicar una persona responsable y marcar tareas como realizadas. También se guarda localmente.

Esta es una primera versión funcional y privada, no todavía un historial clínico sincronizado. No adjunta archivos, no genera PDF y no comparte datos con una clínica ni con otros cuidadores. La pantalla lo indica expresamente. Los registros locales no se sincronizan con la Comunidad ni con otros dispositivos; al borrar los datos de la aplicación se perderán.

La sincronización familiar requiere diseñar primero permisos por mascota, invitaciones seguras, revocación de accesos y reglas de Firestore. No se simula una invitación que todavía no existe. La selección de especie en la Comunidad ya está disponible desde la rama anterior, pero los perfiles locales de Inicio y varias guías siguen siendo principalmente caninos: la experiencia multi-especie completa continúa pendiente.

Consulta [docs/CHANGES16.md](docs/CHANGES16.md) para el alcance, límites conocidos y siguientes pasos.

## Preparación técnica (cambios17)

La rama `cambios17` refuerza la separación del módulo de cuidados y prepara la configuración para que puedas conectar tus propios servicios:

- `care/model/PetCareModels.kt` contiene los modelos del pasaporte y las rutinas.
- `care/data/PetCareLocalStore.kt` contiene el adaptador local. Conserva las claves de SharedPreferences v16 para no perder los registros ya creados al actualizar.
- `docs/FIREBASE_SETUP.md` explica desde cero cómo crear Firebase, activar Authentication, configurar SHA-1, desplegar Firestore y configurar Cloudinary.
- `local.properties.example` sirve de plantilla para los dos valores públicos de configuración de Cloudinary; copia sus líneas a tu `local.properties` local.
- El workflow de `cambios17` ejecuta tests, Android Lint, APK debug y bundle release sin firmar. El AAB de CI no se puede publicar en Play hasta que configures firma y datos de la ficha.

La integración de cuidados continúa siendo local. La invitación real a cuidadores, sincronización, exportación PDF y adjuntos requieren trabajo adicional. Tampoco se ha añadido un backend que genere cargos. Consulta [docs/CHANGES17.md](docs/CHANGES17.md) y [docs/TECHNICAL_NOTES.md](docs/TECHNICAL_NOTES.md).

## Estado de preparación para publicación (cambios15)

Pawly sigue siendo un proyecto en desarrollo y **no está lista para producción**. Esta rama añade perfiles de varias especies en la Comunidad, solicitudes registradas de eliminación de cuenta, una estrategia de producto, borradores legales y una lista de verificación de lanzamiento.

- `docs/PRODUCT_STRATEGY.md`: posicionamiento, diferenciación y hoja de ruta.
- `docs/PRIVACY_POLICY_DRAFT.md` y `docs/TERMS_OF_SERVICE_DRAFT.md`: borradores que requieren completar datos del responsable, proveedores y revisión legal.
- `docs/RELEASE_CHECKLIST.md`: requisitos técnicos, de Play Console, privacidad, moderación y operación.
- `docs/COMMUNITY_GUIDELINES.md`: normas de uso y procedimiento de moderación que debe ponerse en marcha.
- `docs/ACCOUNT_DELETION_OPERATIONS.md`: alcance real y trabajo pendiente para eliminación completa.

La pantalla de perfil permite registrar una solicitud de eliminación en Firestore, pero **no borra automáticamente** la cuenta ni todos los datos. Sigue siendo bloqueante implementar la cascada de borrado en un backend seguro y publicar el recurso web externo exigido por Google Play. No anunciar esta función como eliminación completada.

Los perfiles multi-especie ya se guardan en la Comunidad; otras pantallas y contenidos todavía tienen elementos diseñados principalmente para perros. Revisar la coherencia de todas las especies antes de una campaña generalista.

Los datos de perfiles locales, tareas, favoritos y diario se guardan en el almacenamiento privado del dispositivo; no se sincronizan entre dispositivos. Los datos sociales se sincronizan mediante Firebase. Los recordatorios de Salud son locales y no equivalen a un servicio de notificación garantizada.

## Configuración necesaria para usar la Comunidad

La pantalla de acceso se muestra aunque falte Firebase, pero el inicio de sesión real no puede funcionar hasta configurar el proyecto. Sigue [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md): añade `app/google-services.json` solo en tu copia local, activa Email/Password (y Google si lo quieres usar) en Firebase Authentication, despliega las reglas e índices de Firestore y configura el SHA-1 de la firma debug. Para subir fotos, añade `CLOUDINARY_CLOUD_NAME` y `CLOUDINARY_UPLOAD_PRESET` a `local.properties`. No publiques esos ficheros ni secretos.

Las cuentas creadas con correo reciben un enlace de verificación. Hasta verificarlo se permite entrar y completar el perfil, pero las operaciones sociales que modifican contenido (publicar, seguir, comentar, bloquear y enviar mensajes) permanecen protegidas.

## Próximas mejoras

1. Completar la preparación legal y el borrado completo de cuentas y datos.
2. Añadir pasaporte sanitario privado, documentos y exportación para veterinarios.
3. Compartir el cuidado de mascotas con familiares y cuidadores.
4. Construir un directorio local con verificación y fecha de actualización.
5. Añadir backend de confianza para push y alertas de proximidad.
6. Migrar persistencia local a Room y extraer lógica a ViewModels.

## Principios de producto

- Priorizar la seguridad y el bienestar animal.
- No presentar como verificados datos que no lo estén.
- Explicar el origen y la fecha de actualización de la información.
- Recopilar únicamente los datos necesarios y dar control al usuario.
- No sustituir el criterio veterinario ni los servicios de emergencia.

---

© 2026 Alex Vilarrubla Martín y Maialen Cruz Tudanca. Todos los derechos reservados.
