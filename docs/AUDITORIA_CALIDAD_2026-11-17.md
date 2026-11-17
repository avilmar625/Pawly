# Auditoría de calidad — 17 de noviembre de 2026

## Alcance

Revisión estática de la rama `pawly-mejoras-calidad-ux-2026-11`, con contraste de reglas Firestore, repositorios de cuidados, persistencia local, gestión de imágenes, copias de seguridad, backend Cloudflare y workflows de GitHub Actions.

Esta auditoría no equivale a una prueba manual en un móvil ni a una auditoría externa de penetración.

## Hallazgos y cambios de esta fase

### Corregido: integridad de las copias de seguridad de paseos

Los registros locales y remotos de cuidado ya contienen distancia, enlace al mapa, descripción, paseador y fotografías. El exportador de Pawly omitía esos campos al generar JSON, de modo que restaurar una copia podía perder esos detalles.

La exportación ahora conserva esos campos. La importación:
- Sigue aceptando copias anteriores versión 1 que no incluyan esos campos.
- Valida distancia, URL, longitud de textos y un máximo de cuatro URLs HTTPS de fotografías.
- Decodifica y valida la fotografía incluida antes de modificar los registros locales.
- Acepta enlaces de mapa HTTP y HTTPS con host, en consonancia con el formulario de paseo.

### Revisado: seguridad de recursos multimedia

La regla `cloudinaryAssets` exige usuario autenticado, UID coincidente, identificador bajo `pawly/<carpeta>/<uid>/...`, tipo image/video y fecha de servidor. Las lecturas quedan limitadas al propietario y no se permiten actualizaciones ni borrados directos desde el cliente.

El cargador limita las carpetas admitidas y las extensiones/tipos de vídeo; las credenciales secretas de Cloudinary no se incluyen en el cliente. El preset unsigned sigue siendo una superficie que debe restringirse también en la consola de Cloudinary (formatos, tamaño, carpetas y límites de uso).

### Revisado: preferencias de mascotas perdidas

La regla permite a cada usuario leer/escribir únicamente su documento de suscripción, con radio y celdas geográficas acotadas. No se abre la colección a lectura general.

### Deuda técnica confirmada

- `PawlyApp.kt` conserva navegación, estado global y parte de la persistencia; extraer ViewModels por áreas reduciría acoplamiento y facilitaría pruebas.
- `FirebaseCommunityRepository.kt` sigue concentrando autenticación, perfiles y operaciones comunitarias; conviene dividirlo por responsabilidades sin cambiar rutas Firestore.
- Hay preferencias y partes del diario/calendario que siguen siendo locales; debe decidirse explícitamente qué sincronizar entre dispositivos.
- El proyecto Android continúa en un módulo Gradle; no existe todavía módulo KMP ni aplicación iOS con paridad verificada.
- La documentación histórica contiene notas de ramas antiguas. Mantener una única fuente de verdad para rama base, esquema de datos y estado de CI evitará errores al retomar el trabajo.

## Ampliación: sincronización, backup y comunidad segura

### Colaboración multidispositivo

- Se conserva el modelo de acceso owner/editor/reader en sharedPets; el permiso se aplica en Firestore, no solo en la interfaz.
- Los editores pueden actualizar el perfil compartido, además de los registros de salud y cuidados que ya estaban sincronizados.
- El propietario puede cambiar el rol de un miembro entre editor y lector. La regla Firestore impide que un colaborador cambie su propio rol o el de otra persona.
- La revocación de acceso elimina la pertenencia y marca las invitaciones aceptadas como revocadas en una única escritura por lotes, evitando dejar invitaciones inconsistentes.

**Alcance que sigue pendiente:** la colaboración no sincroniza todavía todos los módulos de Pawly en tiempo real. En particular, las preferencias de calendario, parte del diario y algunos datos de la pantalla Hoy siguen siendo locales. Tampoco se ha añadido resolución automática de conflictos offline; no debe interpretarse la colaboración actual como sincronización universal sin conflictos.

### Copias de seguridad versión 2

- La exportación incluye todos los campos del pasaporte sanitario, rutinas y registros de actividad, incluidos los detalles y fotografías de los paseos.
- Añade las preferencias locales seguras de Pawly (incluidas colecciones y ajustes de módulos que se guardan en preferencias), excluyendo claves que puedan contener credenciales, tokens, UID o rutas de archivos específicas del dispositivo.
- Aumenta el límite del archivo a 25 MB y valida límites de elementos, fotos y enlaces antes de restaurar.
- Mantiene compatibilidad con copias versión 1.
- Si la foto de perfil seleccionada no se puede leer o supera el límite, la exportación falla explícitamente en lugar de crear una copia que parezca completa pero la omita.
- Al exportar con sesión iniciada, primero actualiza pasaporte, rutinas e historial de cuidados desde Firestore. Al restaurar, los registros de salud se escriben en el espacio compartido del propietario, no en una ruta privada distinta.

**Alcance que sigue pendiente:** el backup es una copia de los datos Pawly locales del usuario y del historial de cuidados remoto de la mascota seleccionada; no es todavía una exportación integral de la cuenta comunitaria (mensajes, publicaciones, seguidores) ni de los ficheros alojados en Cloudinary. Las URLs de fotos se conservan, pero el JSON no descarga los binarios de todas las fotos remotas.

### Comunidad: bloqueo recíproco

- El bloqueo mantiene el registro original y crea un índice inverso privado en la cuenta bloqueada.
- El muro, las búsquedas de usuarios, las sugerencias y el directorio profesional excluyen tanto las cuentas bloqueadas por el usuario como las cuentas que lo han bloqueado.
- Desbloquear elimina ambos registros en un lote.
- Las reglas limitan quién puede crear, leer y borrar el índice inverso: el bloqueador lo escribe y la persona bloqueada solo puede leer el suyo.

**Alcance que sigue pendiente:** el índice inverso se crea para nuevos bloqueos; las relaciones antiguas no tienen ese índice hasta que se vuelvan a bloquear. La moderación de publicaciones sigue sin borrar automáticamente el archivo multimedia remoto de Cloudinary; hace falta un flujo de borrado de recursos en backend y una política de retención para evitar ficheros huérfanos.

## Siguiente trabajo recomendado

1. Añadir pruebas de integración para exportar/importar backups con paseos completos y copias antiguas.
2. Revisar la política de retención, borrado y limpieza de recursos Cloudinary huérfanos.
3. Añadir pruebas de reglas para acceso cruzado entre usuarios y roles reader/editor/owner en cada subcolección compartida.
4. Revisar límites de consultas Firestore, paginación y listeners para evitar lecturas excesivas y listeners duplicados.
5. Extraer ViewModels de Hoy, Salud y Diario en pasos pequeños.
6. Probar flujos reales en dispositivo: cámara, selector de fotos, permisos, conectividad intermitente, sincronización y restauración de backup.

## Validación

La rama dispone de workflows para pruebas Kotlin, Android Lint, APK debug, AAB release sin firma, reglas Firestore con Emulator y validación del Worker. El estado de cada ejecución debe consultarse en GitHub Actions para el último commit; una ejecución de un commit anterior no valida automáticamente cambios posteriores.

No se han desplegado reglas Firestore ni Worker en producción desde esta auditoría.
