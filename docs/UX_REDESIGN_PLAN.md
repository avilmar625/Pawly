# Pawly UX redesign — cambios65

## Objetivo

Actualizar la experiencia visual de Pawly usando como referencia el PDF "Pawly — pantalla Hoy y navegación" (16-11-2026), sin retirar pantallas ni funciones existentes.

## Dirección visual

- Base cálida: crema / marfil para el fondo.
- Primario: verde bosque profundo.
- Secundarios: verde salvia y coral suave.
- Superficies claras, bordes finos, radios generosos y sombras discretas.
- Tipografía con jerarquía clara; títulos concisos y datos importantes legibles.
- Fotografía real de la mascota como protagonista, evitando imágenes genéricas que puedan confundirse con la mascota del usuario.
- Estados vacíos, carga, error y offline diseñados con el mismo lenguaje visual.
- Soporte completo para modo oscuro, idioma y ajustes simple/elegante.

## Navegación: preservar y reorganizar

La barra inferior ya tiene cinco destinos visibles: Hoy, Explorar, Mi perro, Comunidad y Más. No sustituirla por una navegación nueva que oculte funciones.

Dentro de Mi perro, proponer una navegación secundaria:
- Todo: línea de tiempo combinada.
- Salud: agenda sanitaria, pasaporte, peso, medicación y ficha de emergencia.
- Diario: recuerdos y fotos.
- Citas: calendario, citas y recordatorios.

Las áreas actuales Pasaporte, Cuidados y Compartir deben seguir accesibles dentro de Salud o desde accesos contextuales claros. Profesionales, Aprender y Ayuda siguen accesibles desde Más. Eventos deben seguir disponibles desde calendario y comunidad.

## Hoy — estructura de referencia

1. Cabecera: fecha, selector de mascota, foto/identidad y notificaciones.
2. Tarjeta de progreso diario calculada desde tareas reales y completadas.
3. Lo de hoy: medicación, comida, paseos y otros cuidados, mostrando responsable y hora.
4. Próximamente: próxima cita, vacuna o recordatorio con fecha real.
5. Registrar rápido: Paseo, Peso, Foto, Comida. Cada botón abre su flujo de creación/registro real.
6. Cuidado compartido: actividad reciente y responsable, solo si existen datos compartidos.
7. Recuerdo del día: “hace un año” solo cuando existe un recuerdo con fecha equivalente.
8. Sugerencias de Explorar/Aprender sin desplazar las acciones prioritarias.

No usar contenido ficticio para rellenar tarjetas. Ocultar o sustituir por un estado vacío útil cuando no existan datos.

## Cuidado compartido y medicación

Ya existen modelos `CareTask`, `CareLogEntry`, roles owner/editor/reader y sincronización. La integración del panel Hoy debe reutilizar esos contratos.

Pendiente de especificar y probar:
- Tareas de hoy por fecha local, frecuencia y mascota.
- Responsable, hora y dosis visibles.
- Registro idempotente de cada toma/acción con actor y timestamp.
- Protección contra doble registro y confirmación explícita cuando otra persona ya registró la toma.
- Permisos de edición coherentes con owner/editor/reader.
- Offline: cola/estado pendiente sin mostrar falsamente sincronización.
- Alarmas locales y política de notificaciones sin duplicados.
- Compatibilidad con datos antiguos; no borrar ni reinterpretar tareas existentes.

La pauta actual solo tiene una dosis y hora por tarea y recordatorio diario básico. Una pauta completa de múltiples tomas requiere ampliar modelo, persistencia, reglas, scheduler y migración compatible.

## Peso

Se ha extraído el parser a `care/domain/WeightMeasurementParser.kt`. Debe validar coma/punto decimal, unidad opcional y rango permitido. Próxima fase: introducir un modelo numérico estructurado para nuevas entradas sin romper las entradas antiguas en texto libre; la gráfica debe usar fechas y valores, no deducir datos de cadenas arbitrarias.

## Copias de seguridad

La copia JSON v1 es local y parcial. No presentarla como backup integral de la cuenta. Futuro trabajo:
- inventario de todas las fuentes de datos locales/remotas;
- exportación versionada por cuenta y mascota;
- metadatos de medios y estrategia de exportación de Cloudinary;
- restauración con vista previa, validación, conflictos e idempotencia;
- cifrado opcional para datos personales;
- pruebas de ida/vuelta y compatibilidad de versiones;
- excluir tokens, credenciales, contraseñas y secretos.

## Propuesta de implementación por bloques

### Bloque 1 — fiabilidad y datos
- Verificar parser de peso y CI.
- Auditar cómo se guardan las tareas y logs y sus permisos.
- Definir un estado diario por mascota y cuenta; evitar que las marcas de una mascota se apliquen a otra.
- Corregir flujos de acceso rápido que hoy solo navegan a una pantalla general.

### Bloque 2 — Mi perro
- Construir línea de tiempo Todo con datos de salud, diario y citas.
- Añadir filtros Salud/Diario/Citas.
- Mantener accesos a Pasaporte, Cuidados y Compartir.
- No duplicar registros ni alterar rutas Firestore sin migración.

### Bloque 3 — Hoy visual y funcional
- Rediseñar HomeScreen según el PDF.
- Alimentar el progreso desde tareas reales.
- Próximamente desde agenda/recordatorios reales.
- Actividad compartida desde CareLogEntry.
- Recuerdo anual desde fechas reales del diario.
- Acciones rápidas con flujos funcionales.

### Bloque 4 — extras diferenciadores
- Widget Android.
- Seguimiento de paseos y rachas.
- Inventario de comida/pienso.
- Gastos de mascota.
Cada función requiere diseño de datos, privacidad, persistencia y tests antes de la UI.

## Criterios de aceptación

- Las cinco pestañas inferiores siguen visibles.
- Ningún destino actual ni acción existente desaparece.
- Los registros creados antes de la actualización siguen accesibles.
- La mascota seleccionada y sus datos no se mezclan con otra mascota.
- La UI funciona en tema claro/oscuro, español/otros idiomas existentes y pantallas pequeñas.
- Todos los botones hacen lo que anuncian.
- Los estados de sincronización reflejan el resultado real.
- Unit tests, lint, APK debug y bundle release pasan.
- Prueba manual de Android en emulador y dispositivo físico antes de cerrar el bloque.


## Estado de implementación observado en cambios65 (16-11-2026)
- Los accesos rápidos ya están conectados en PawlyApp: Peso abre alta de tipo Peso en Pasaporte; Foto abre el selector de imagen del Diario; Comida abre el alta de cuidado Comida. Validar en dispositivo que los formularios consumen el parámetro inicial una sola vez y persisten.
- Se añade confirmación si otro cuidador ya registró una tarea de medicación ese día. La comprobación es una salvaguarda de UX basada en careLogs descargados, no una exclusión transaccional de doble toma.
- El control de edición arranca deshabilitado en modo autenticado hasta conocer el rol remoto.
- El parser de peso prioriza valores explícitos en kg y solo admite valores sin unidad cuando la cadena es una cifra aislada o tiene el prefijo Peso.
- Tests nuevos cubren duplicados de medicación y fechas confundibles con peso. Resultado de ejecución pendiente de CI/Gradle.

- La misma protección de rol inicial se ha aplicado también al panel completo `PetCarePlanSection`: el usuario compartido no recibe acciones de edición hasta conocer su rol y la función de toggle vuelve a comprobar `canEdit`.


## Perfil visual por mascota
- La portada de Hoy usa la foto local del perfil, o recupera la URL HTTPS de Cloudinary desde el perfil Firebase. Si no hay foto real, muestra el placeholder ilustrado Pawly.
- Las fotos locales se guardan con una clave independiente por mascota; se migra la clave global antigua a la primera mascota activa para conservar los datos existentes.
- Al renombrar una mascota y al restaurar una copia se conserva la asociación de la foto con el perfil correspondiente.
- Validación manual pendiente: cambiar entre dos mascotas con fotos diferentes, cerrar/reabrir la app y comprobar que no se cruzan las imágenes.


## Mejoras de Explorar, autenticación, Comunidad y Eventos — 2026-11-16

### Recomendaciones y puntos exactos
- `CommunityPlacesPanel` ya exigía nombre, dirección, descripción y coordenadas para publicar una recomendación. El formulario incluye mapa OpenStreetMap interactivo con selección del punto exacto y campos de latitud/longitud; la fila abre Maps/OpenStreetMap.
- Se añadió un filtro propio de categorías a las recomendaciones de la comunidad (Todos, Playa, Parque, Ruta, Restaurante, Alojamiento, Veterinario, Otro), con recuento y estado vacío por categoría. Así una recomendación de Playa se consulta en Playa y no mezclada con las demás.
- La acción de mapa ahora es explícita y rotulada «Mapa», además de poder tocar la fila.

### Autenticación
- Se detectó un problema de estado de UI: Firebase Authentication puede aceptar las credenciales y establecer `currentUser`, mientras el aprovisionamiento/carga del perfil de Firestore falla. El catch anterior dejaba la pantalla de acceso abierta, aunque existía una sesión válida.
- Se modificó el flujo de email/contraseña y Google para comprobar `repo.currentUid` en el catch; si la sesión ya existe, continúa a la app y conserva el detalle del error como estado. Si Firebase Auth no creó sesión, muestra el error sin avanzar.
- Credential Manager ya incluía limpieza de sesión antigua y segundo intento con selector de cuentas no autorizadas. Esta actualización no puede demostrar que el proveedor de Google funcione en el emulador: hace falta probarlo con Google Play Services y una cuenta añadida.
- El mapeo de errores ya diferencia credenciales incorrectas, correo inválido, cuenta desactivada, exceso de intentos, método no habilitado, Firestore PERMISSION_DENIED y falta de red.

### Comunidad
- El selector de secciones se ha convertido en una tarjeta de navegación más visual: icono en contenedor, sección activa, descripción contextual y contador de avisos, manteniendo todas las páginas y el acceso de moderación condicionado a admin.

### Eventos
- Ya existía selector de orden cronológico «Más próximos primero / Más lejanos primero».
- Se corrigió la inversión del comparador: los eventos con fecha válida se ordenan ascendente/descendente, mientras que los eventos sin fecha permanecen al final en ambos modos. No se eliminan eventos sin fecha.

### Validación pendiente
- Ejecutar CI (tests/lint/build) en HEAD actualizado y corregir cualquier error.
- Probar en emulador/dispositivo: publicar recomendación con pin, filtrar Playa/Parque, abrir mapa, autenticar email/Google y ordenar eventos con y sin fecha.
- No se ha probado la interacción en dispositivo real todavía.


## Ajuste visual aplicado — 16-11-2026
- La identidad de mascota en Hoy pasa a una fila compacta con avatar circular (foto local, foto HTTPS o inicial), nombre destacado y acción de edición. Se elimina el banner de portada alto para dar prioridad al contenido operativo del día.
- Se conserva la tarjeta de progreso basada en rutinas reales. La tarjeta de próxima cita ahora se muestra entre las tareas de hoy y los accesos rápidos.
- La barra inferior mantiene cinco destinos. Diario y Calendario resaltan «Mi perro» como sección activa; Aprender, Profesionales y Ayuda resaltan «Más». El conjunto usa el verde de marca y el contenedor salvia del tema, en lugar de cinco colores independientes.
- No se elimina ningún destino ni acción: los tabs secundarios continúan usando sus pantallas existentes.
- Revisión de compilación y visual en dispositivo pendiente.

- El bloque «Próximamente» presenta la fecha en formato legible según el idioma del dispositivo (día y número) y añade «hoy», «mañana» o el tiempo restante; reconoce variantes de texto de vacunas para usar el icono sanitario.


## Actualización funcional y de localización — 16-11-2026
- Los textos nuevos del panel Hoy/cuidados pasan por `tr()`; se han añadido traducciones explícitas para catalán, francés e inglés, incluidos estados offline/sync, progreso, próxima cita, acciones rápidas y confirmación de posible doble medicación.
- Los identificadores persistidos de acción (`Paseo`, `Comida`, `Medicación`) permanecen en español como valores de dominio estables. Solo se traducen sus etiquetas visibles; no se usan cadenas traducidas para comparar datos guardados.
- En «Mi perro» se añaden accesos secundarios directos a Diario y Citas, manteniendo Agenda, Pasaporte/Salud, Cuidados y Compartir. Diario y Citas reutilizan las pantallas existentes; no se duplican ni se sustituyen.
- La vista «Todo» del PDF como cronología realmente unificada todavía no está implementada: requiere combinar entradas de pasaporte, rutinas/logs, diario y calendario con fechas/ordenación coherentes. No se etiqueta Agenda como Todo para no prometer una cronología que aún no existe.
- Limitación de modelo detectada: las rutinas semanales solo guardan frecuencia «Semanal», sin días de semana configurables; no se puede calcular de forma fiable qué días concretos vencen. Hace falta ampliar modelo/UI/reglas/migración compatible antes de cambiar esa lógica.
- El panel de Hoy muestra hasta tres registros de actividad reales. La salvaguarda de doble medicación sigue siendo una confirmación UX, no una exclusión atómica/transaccional entre cuidadores.
- La copia JSON actual sigue siendo local y parcial; no se presenta como copia completa de todos los datos de la cuenta ni de medios Cloudinary.

- La navegación secundaria de «Mi perro» ofrece ahora accesos directos a Diario y Citas reutilizando las pantallas existentes, además de Agenda, Salud/Pasaporte, Cuidados y Compartir. El destino principal se etiqueta «Mi perro» y sus nombres tienen traducciones para catalán, francés e inglés.
- La cronología «Todo» del PDF sigue pendiente y se mantiene como deuda explícita: no se debe mostrar ese nombre hasta que la pantalla agregue y ordene de verdad registros de salud, cuidados, diario y citas.

- Test unitario añadido para las traducciones del panel Hoy/cuidados y navegación «Mi perro» (inglés, catalán y francés, además del fallback español).


## Cronología Mi perro → Todo — 16-11-2026
- Se implementa una cronología que combina registros reales del pasaporte, logs de cuidados, recuerdos del diario (formato Pawly v1), recordatorios del calendario y fechas sanitarias. Se ordena de más reciente a más antiguo.
- En cuentas autenticadas intenta cargar pasaporte y logs desde Firebase; si no hay conexión, mantiene la información local y muestra el estado correspondiente. Si el pasaporte remoto está vacío y existen entradas locales, intenta sincronizarlas con el espacio privado de la mascota.
- Incluye enlaces a Salud, Diario y Citas. La pestaña principal de Mi perro abre Todo; Agenda, Pasaporte/Salud, Cuidados y Compartir se mantienen disponibles.
- La cronología no incorpora todavía eventos comunitarios ni todos los elementos de la agenda externa. No debe describirse como un historial completo de absolutamente todas las actividades.
- Tests añadidos para mezcla/orden cronológico y para ignorar registros JSON inválidos. Pendiente ejecutar tests y validar sincronización en Android con dos cuentas/dispositivos.
