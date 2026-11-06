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

## Exportación y uso compartido

- **Informes PDF:** el pasaporte sanitario y el plan de cuidados se pueden generar localmente y compartir mediante el selector del sistema Android. Los documentos incluyen una nota de uso responsable y no sustituyen a la documentación veterinaria oficial.
- **Privacidad:** los PDF se generan en la caché privada de la aplicación y se comparten únicamente cuando el usuario elige una aplicación o destinatario.

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

## Rediseño visual (cambios26)

La rama `cambios26` introduce navegación Material 3, fotografías editoriales en Inicio/Explorar/Diario/Comunidad, estados vacíos más cuidados y adaptación inicial al tema. Las imágenes editoriales son remotas y requieren internet. La adaptación de las pantallas heredadas al modo oscuro sigue en progreso. Detalles en [docs/CHANGES26.md](docs/CHANGES26.md).

## Personalización y experiencia (cambios25)

La rama `cambios25` inicia una capa de preferencias globales accesible desde el icono de ajustes de la barra superior:

- Modo oscuro (base de tema Material 3).
- Cuatro paletas de acento: bosque, océano, baya y atardecer.
- Preferencia de idioma guardada localmente: español, inglés o catalán. **La traducción integral de las pantallas existentes aún no está completada**; la selección queda como preferencia para la siguiente fase de internacionalización.
- Los ajustes de apariencia se conservan en el dispositivo y no se envían a Firebase.

El acceso social ya incluye correo/contraseña y Google mediante Android Credential Manager + Firebase Authentication, además de recuperación de contraseña por correo. Google requiere OAuth configurado y el `google-services.json` local. No se muestran proveedores ficticios: Apple y teléfono quedan pendientes de configuración y desarrollo específico.

El rediseño global se inicia con una barra superior común y un panel de ajustes. El modo oscuro y la adaptación de todas las pantallas heredadas todavía necesitan una pasada de coherencia visual. Consulta [docs/CHANGES25.md](docs/CHANGES25.md).

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
- Android SDK Platform 37 y Build Tools 37.0.0.
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


## Rediseño visual (cambios18)

La rama `cambios18` introduce un rediseño más marcado de Inicio y de la navegación inferior: mascota protagonista, resumen de rutinas y recuerdos, progreso diario, tarjetas de acciones y barra flotante de seis destinos. Mantiene los estados y funciones de cambios17. Consulta [docs/CHANGES18.md](docs/CHANGES18.md) para el alcance y las pruebas recomendadas. Las pantallas interiores se irán unificando visualmente en iteraciones posteriores.


## Últimos cambios de desarrollo

- [cambios20: sincronización del pasaporte sanitario y rutinas con Firestore](docs/CHANGES20.md).
- [cambios21: identidad estable de mascotas y continuidad del historial al cambiar el nombre](docs/CHANGES21.md).
- [Configuración de Firebase](docs/FIREBASE_SETUP.md).


## Colaboración en perfiles (cambios23)

La pestaña **Salud → Compartir** incorpora invitaciones privadas a perfiles de mascota mediante nombre de usuario de Pawly. La persona invitada puede aceptar o rechazar; el propietario puede consultar los miembros y retirar el acceso. Los perfiles aceptados aparecen en el apartado «Perfiles compartidos contigo».

**Alcance importante:** cambios23 comparte el acceso y los metadatos básicos del perfil, pero todavía no comparte ni sincroniza el pasaporte sanitario, las rutinas, la agenda, las fotos privadas o el diario. Esos datos permanecen en la cuenta propietaria. La siguiente iteración debe migrar los datos de cuidado a un espacio compartido con permisos por rol y una estrategia segura para conservar el historial existente.

Para activar esta función hay que desplegar las reglas e índices de Firestore actualizados desde la raíz del proyecto:

```bash
npx --yes firebase-tools deploy --only firestore:rules,firestore:indexes
```

La función requiere iniciar sesión y verificar el correo electrónico. La invitación no envía un push por sí sola: el backend de notificaciones sigue pendiente. No se han cambiado las reglas de acceso del pasaporte privado existente.


## cambios24 · Mensajería enriquecida

- Mensajes privados con fotografía adjunta mediante el cargador de imágenes ya existente (Cloudinary).
- Confirmación de lectura por mensaje: el destinatario añade su UID a `readBy`; el emisor ve «Visto».
- La pantalla marca como leídos los mensajes entrantes al recibir la actualización en tiempo real.
- Validación de tamaño y contenido de mensajes, y reglas Firestore para impedir que un usuario marque como leído un mensaje propio.
- Las fotografías se cargan con la cuenta verificada y se envían como URL, sin almacenar bytes en Firestore.

Para activar los cambios de backend:
```bash
npx --yes firebase-tools deploy --only firestore:rules,firestore:indexes --project pawly-83dca
```

La subida de imágenes en mensajes reutiliza Cloudinary. Las notificaciones push automáticas siguen necesitando una función backend de confianza; no se han añadido credenciales privadas ni se ha activado facturación.


### Permisos de perfiles compartidos
En la pestaña de compartir se puede invitar con dos niveles: **Puede editar** o **Solo lectura**. La estructura de permisos queda almacenada en la invitación y en el miembro. La exposición del pasaporte y de las rutinas a esos roles sigue pendiente de la migración segura a rutas compartidas; hasta entonces, los registros sanitarios continúan privados.
