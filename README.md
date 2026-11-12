# Pawly 🐾

**Más aventuras. Más recuerdos. Más vida juntos.**

Pawly es una aplicación Android en desarrollo para organizar la vida cotidiana con animales de compañía: perfiles multi-especie, cuidados, aprendizaje, lugares, recuerdos y comunidad.

> Estado: proyecto en desarrollo activo. La comunidad utiliza Firebase Authentication y Firestore; la ficha/diario y los datos compartidos de mascotas tienen sincronización por mascota. Las fotos y vídeos de comunidad se alojan en Cloudinary. Las funciones aún locales o pendientes de backend se detallan en [la guía de configuración](docs/FIREBASE_SETUP.md). Para activar Firebase y multimedia hacen falta archivos locales y pasos de consola; no se incluyen credenciales en Git.

## Creadores

- **Alex Vilarrubla Martín**
- **Maialen Cruz Tudanca**

## Funcionalidades actuales

- **Inicio:** selector para varios perfiles, perfil editable con foto de cámara o galería, tareas cotidianas y resumen actualizado.
- **Explorar:** búsqueda y filtros por categorías, con ideas genéricas y favoritos guardados en el dispositivo.
- **Salud:** fechas por mascota y recordatorios locales con fecha y hora para vacunas, desparasitación y revisiones. Android puede retrasar avisos por ahorro de batería; no ofrece pautas veterinarias.
- **Diario:** recuerdos de texto persistentes, con recuento actualizado.
- **Persistencia y sincronización:** la app conserva datos locales para uso sin conexión y sincroniza con Firebase las áreas conectadas cuando hay sesión y red. No todas las preferencias, recordatorios o marcas diarias se sincronizan.

## Exportación y uso compartido

- **Informes PDF:** la ficha combinada de la mascota, el pasaporte sanitario y el plan de cuidados se pueden generar localmente, guardar en el dispositivo mediante el selector de documentos de Android o compartir mediante el selector del sistema. Los documentos incluyen una nota de uso responsable y no sustituyen a la documentación veterinaria oficial.
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

## Arquitectura y preparación multiplataforma (cambios54)

La rama `cambios54` inicia una refactorización compatible orientada a mantener Android nativo y preparar una futura app iOS nativa. Se añade el contrato `care/domain/PetCareRepository.kt` y la implementación Firebase de Salud lo adopta, separando la API de la persistencia concreta sin cambiar el comportamiento ni las rutas de Firestore. El plan de capas, las decisiones y las fases pendientes están documentados en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

**Importante:** cambios54 todavía no incorpora un módulo Kotlin Multiplatform ni una app iOS. Es una primera extracción de responsabilidades; la migración de ViewModels, el desacoplamiento del repositorio de Comunidad y la configuración KMP se realizarán de forma gradual, con pruebas de regresión y preservando los datos existentes.

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

## Acabado visual y accesibilidad (cambios27)

La rama `cambios27` corrige los errores de compilación detectados en cambios26, consolida los esquemas de color claro/oscuro con roles semánticos Material 3, mejora la cabecera y compacta la navegación inferior para priorizar el contenido. También amplía la coherencia de color en ajustes y pantallas de Comunidad. El modo oscuro debe validarse visualmente en dispositivo real y todavía pueden quedar componentes heredados con colores literales. Las capturas reales se deben obtener en un dispositivo o emulador con la configuración local de Firebase; no se incluyen mockups como si fueran capturas de la app.

## Personalización y experiencia (cambios25)

La rama `cambios25` inicia una capa de preferencias globales accesible desde el icono de ajustes de la barra superior:

- Modo oscuro (base de tema Material 3).
- Cuatro paletas de acento: bosque, océano, baya y atardecer.
- Preferencia de idioma guardada localmente: español, catalán, francés e inglés. Se ha incorporado un catálogo compartido y se han traducido las pantallas principales; pueden quedar textos externos, mensajes del backend o cadenas dinámicas heredadas en su idioma original.
- Los ajustes de apariencia e idioma se conservan en el dispositivo y no se envían a Firebase.

El acceso social ya incluye correo/contraseña y Google mediante Android Credential Manager + Firebase Authentication, además de recuperación de contraseña por correo. Google requiere OAuth configurado y el `google-services.json` local. No se muestran proveedores ficticios: Apple y teléfono quedan pendientes de configuración y desarrollo específico.

El rediseño global se inicia con una barra superior común y un panel de ajustes. El modo oscuro y la adaptación de todas las pantallas heredadas todavía necesitan una pasada de coherencia visual. Consulta [docs/CHANGES25.md](docs/CHANGES25.md).

## Colaboración y sincronización (cambios30)

El pasaporte sanitario y las rutinas pueden almacenarse en el espacio Firestore compartido de cada mascota. Los permisos de servidor distinguen propietario/editor (lectura y escritura) y lector (solo lectura). El modo sin cuenta conserva el almacenamiento local. Es necesario desplegar las reglas de `firestore.rules` actualizadas antes de probar la sincronización compartida. La navegación para abrir directamente mascotas que otra persona comparte contigo y el envío push automático siguen pendientes; FCM en Android solo registra y recibe tokens hasta desplegar un backend de confianza.

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
firebase use
firebase deploy --only firestore:rules,firestore:indexes
```

La subida de imágenes en mensajes reutiliza Cloudinary. Las notificaciones push automáticas siguen necesitando una función backend de confianza; no se han añadido credenciales privadas ni se ha activado facturación.


### Permisos de perfiles compartidos
En la pestaña de compartir se puede invitar con dos niveles: **Puede editar** o **Solo lectura**. La estructura de permisos queda almacenada en la invitación y en el miembro. La exposición del pasaporte y de las rutinas a esos roles sigue pendiente de la migración segura a rutas compartidas; hasta entonces, los registros sanitarios continúan privados.


## cambios35 · Sincronización integral de mascota y colaboración

- La ficha principal sincroniza nombre y URL de fotografía por mascota en Firestore; las imágenes se comprimen y suben a Cloudinary.
- El Diario de recuerdos se sincroniza por mascota y combina entradas locales y remotas al inicializar la nube.
- Foto, diario y rutinas rápidas quedan aislados por ID estable de mascota, con migración de las preferencias antiguas.
- Desde Salud → Compartir se pueden abrir perfiles aceptados. Pasaporte y rutinas respetan los roles editor/solo lectura.
- Se amplían las reglas de Firestore para proteger ficha y Diario compartidos. Despliega las reglas antes de probar.
- Si Firebase o Cloudinary no están disponibles, se conserva la copia local.

Limitaciones: Cloudinary unsigned entrega URLs públicas para quien las conozca; no hay cifrado privado E2E. Agenda rápida y checks del día siguen siendo locales. Push automático requiere backend confiable y Blaze/Cloud Functions, que no se activa. El borrado integral de cuentas también necesita backend y operación. Requiere pruebas físicas y revisión legal antes de publicar.


## cambios36 · Preparación de Firebase y multimedia

- Se separan los presets unsigned de Cloudinary para imágenes y vídeos, manteniendo compatibilidad temporal con el antiguo `CLOUDINARY_UPLOAD_PRESET`.
- La guía de Firebase se ha actualizado para reflejar el estado real de la app, el despliegue de reglas/índices, las limitaciones del plan gratuito y las pruebas con dos cuentas.
- La guía de Cloudinary explica formatos admitidos, límites aplicados por el cliente, privacidad de las URL y los riesgos de presets unsigned.
- La configuración real sigue siendo local: `app/google-services.json` y `local.properties`. No se suben al repositorio.
- Esta rama no crea ni modifica proyectos en las consolas de Firebase/Cloudinary y no activa facturación.


## Plataforma integral y servicios profesionales (cambios49)

La sección **Más** reúne accesos a servicios profesionales, Comunidad y avisos de mascotas perdidas/encontradas, Diario, Aprender y Ajustes. Salud sigue siendo un destino principal independiente para evitar duplicidades.

El directorio inicial de **Servicios profesionales** permite buscar adiestradores, etólogos, veterinarios, urgencias, peluquerías, paseadores, cuidadores, residencias, fisioterapeutas, especialistas en nutrición y tiendas. La búsqueda se abre en Google Maps o en la web, con filtros por especialidad y zona. Los resultados son externos: Pawly no verifica automáticamente a los proveedores, ni gestiona todavía reservas, pagos, disponibilidad o valoraciones. Antes de contratar hay que comprobar credenciales, identidad, condiciones y precios.

La sección de avisos de mascotas perdidas/encontradas reutiliza las categorías de Comunidad ya existentes; no crea un sistema paralelo ni sustituye los servicios oficiales. Salud conserva el pasaporte, las rutinas, los recordatorios locales, la sincronización disponible por mascota y la exportación PDF. Los datos introducidos por la familia no se convierten por ello en documentación clínica oficial.

Detalles y alcance de esta evolución: [docs/CHANGES49.md](docs/CHANGES49.md).

## Perfiles, idiomas y cuenta (cambios51)

- **Perfiles particulares y profesionales:** la cuenta puede identificarse como particular o profesional. El perfil profesional incluye especialidad, zona, descripción y contacto opcional, visibles cuando el perfil es público. La etiqueta no verifica credenciales y los perfiles aún no aparecen como fichas propias en el directorio ni ofrecen reservas o pagos.
- **Más:** invita a un amigo, contacto por correo, enlaces a los borradores de privacidad y condiciones, y cierre de sesión con confirmación. El logout ya no aparece en Comunidad.
- **Idiomas:** español, catalán, francés e inglés, con un contexto compartido que actualiza las pantallas principales sin reiniciar la app. Aún puede haber contenido externo o mensajes dinámicos que no se traduzcan.
- **PDF:** guardar en el dispositivo o compartir la ficha completa con datos de perfil, pasaporte sanitario y rutinas.
- **Legal y soporte:** los documentos de privacidad y términos siguen siendo borradores. El correo oficial de soporte y los datos del responsable deben completarse antes de una publicación.

Detalles: [docs/CHANGES51.md](docs/CHANGES51.md).