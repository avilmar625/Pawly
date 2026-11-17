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
