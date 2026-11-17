# Fase 4B — Salud inteligente (diseño y alcance)

## Objetivo
Convertir el pasaporte sanitario de Pawly en un historial útil y accionable por mascota, sin duplicar rutinas ni introducir servicios de pago.

## Situación técnica de partida
- El modelo actual `PassportEntry` contiene `id`, `type`, `title`, `detail` y `date`.
- `PetCareLocalStore` ya persiste pasaporte, rutinas y registros en SharedPreferences con ámbito de usuario y clave por mascota. Las claves v16 existentes deben conservarse.
- Las rutinas `CareTask` y los registros `CareLogEntry` son conceptos distintos del historial sanitario: no se deben reutilizar como si fueran vacunas o tratamientos.
- Existe un planificador de recordatorios Android (`CareReminderScheduler`) que persiste alarmas y las restaura tras reinicio/actualización. Usa `setAndAllowWhileIdle` (alarma no exacta), por lo que no requiere pedir acceso a alarmas exactas.
- El manifiesto declara `POST_NOTIFICATIONS`, pero el receptor intenta publicar y silencia errores con `runCatching`; la fase debe comprobar el permiso en tiempo de ejecución y dar feedback visible cuando falte. Programar una alarma no equivale a garantizar que se muestre una notificación.
- La rama base es `cambios80-fase4a-calidad-ux`. Esta rama es independiente y no modifica ramas anteriores.

## Alcance funcional previsto

### 1. Pasaporte sanitario cronológico
- Vacunas, desparasitaciones, tratamientos, consultas, cirugías, pruebas y otros hitos.
- Cada registro debe admitir fecha, categoría, nombre del producto/procedimiento, clínica o profesional, notas y adjunto opcional si la infraestructura actual lo permite.
- Mostrar historial ordenado por fecha, filtros por categoría y estado de próxima actuación.
- Permitir editar y borrar con confirmación, sin perder entradas antiguas.

### 2. Próximas dosis y tratamientos
- Una actuación puede tener próxima fecha o periodicidad.
- Tratamientos con fecha de inicio y fin, pauta/frecuencia y dosis descrita por el usuario.
- No calcular dosis veterinarias ni recomendar pautas: Pawly registra lo indicado por el profesional.
- Evitar duplicados accidentales y distinguir “pendiente”, “realizado” y “vencido”.
- Las tomas múltiples deben representarse como eventos separados o una pauta explícita, nunca como un único check ambiguo.

### 3. Recordatorios fiables
- Reutilizar el planificador local existente solo después de verificar implementación y permisos.
- Comprobar `POST_NOTIFICATIONS` en Android 13+ antes de ofrecer el recordatorio como activo; solicitar permiso desde una acción contextual y explicar el estado denegado. No ocultar errores de publicación.
- Mantener la alarma como no exacta salvo que una necesidad funcional justifique el permiso especial; mostrar la hora como aproximada cuando corresponda.
- Reprogramar o cancelar avisos al editar, completar o borrar un registro.
- No prometer sincronización de recordatorios entre dispositivos si no existe backend que la garantice.

### 4. Agenda de salud
- Próximas actuaciones agrupadas por fecha y mascota.
- Acceso directo al detalle para marcar como realizado, posponer o editar.
- Los estados se derivan de fechas y registros reales, no de datos de ejemplo.

## Criterios técnicos y de seguridad
- Mantener compatibilidad de lectura con el JSON v16 existente; añadir campos opcionales con valores por defecto.
- Identificar los datos por ID estable de mascota cuando la arquitectura lo permita. No cambiar claves actuales basadas en nombre sin una migración segura y pruebas de compatibilidad.
- Respetar `storageScope` y el comportamiento offline. No afirmar sincronización remota donde solo exista almacenamiento local.
- No introducir Firebase Storage, Cloudinary adicional, APIs externas ni planes de pago.
- No registrar datos sanitarios en logs ni incluir información privada en notificaciones visibles en pantalla bloqueada.
- Añadir pruebas unitarias para serialización, fechas, estados y duplicados.

## Secuencia de implementación
1. Inspeccionar `CareReminderScheduler`, el editor actual de pasaporte y las rutas de exportación/backup.
2. Ampliar el modelo y almacenamiento con compatibilidad hacia atrás y pruebas.
3. Implementar UI del pasaporte y detalle de actuación.
4. Integrar recordatorios locales con permisos y cancelación/reprogramación.
5. Añadir agenda de salud y documentación.
6. Ejecutar unit tests, lint y build Android antes de pedir pruebas manuales.

## Fuera de alcance de esta fase
- Diagnóstico veterinario, cálculo automático de dosis o recomendaciones clínicas.
- Envío de notificaciones push remotas.
- Sincronización de alarmas entre Android e iOS.
- Adjuntos sanitarios en nube si requieren servicios o costes nuevos.
## Implementación Fase 4B — pasaporte estructurado

- PassportEntry conserva los cinco campos originales (id/type/title/detail/date) y añade campos opcionales con valores por defecto: producto, dosis, pauta, clínica, próxima dosis, estado, recordatorio y hora.
- La fecha date existente se mantiene como fecha de administración/registro. nextDoseDate representa la próxima actuación y alimenta la agenda.
- Los registros antiguos que no tienen los campos nuevos se reconstruyen como REALIZADO, sin modificar ni borrar su contenido.
- PetCareLocalStore sigue usando las claves v16. El JSON antiguo se lee con defaults y al guardar incorpora los nuevos campos.
- Firestore escribe y lee los campos nuevos en los mismos documentos del pasaporte. La lectura de documentos antiguos usa defaults compatibles.
- La agenda deriva el estado de la próxima actuación: fecha futura o de hoy = pendiente; fecha anterior = vencido. El estado del registro sanitario se conserva por separado (pendiente/realizado).
- El formulario incluye fecha de administración, próxima dosis/actuación, producto, dosis, pauta, clínica, observaciones y estado. El contenido del diálogo es desplazable.
- La exportación PDF incorpora producto, dosis, pauta, clínica, próxima actuación y estado.
- Los recordatorios son locales. Para Android 13+ se solicita POST_NOTIFICATIONS cuando se activa un recordatorio; si se deniega, el registro se guarda igualmente y la interfaz informa de que no se enviará el aviso.
- El receptor comprueba el permiso antes de mostrar la notificación y no deja de reprogramar recordatorios recurrentes si el permiso no está concedido.
- Las alarmas siguen usando setAndAllowWhileIdle; no se solicita el permiso de alarmas exactas.
- La pauta, dosis y producto son texto introducido por la persona usuaria según las indicaciones veterinarias. Pawly no calcula dosis ni recomienda tratamientos.

## Verificaciones añadidas

- PassportEntryScheduleTest: compatibilidad con registro antiguo, próxima dosis futura, próxima dosis vencida y actuación pendiente sin próxima dosis explícita.
- Pendiente de validar el resultado final del workflow Android de GitHub Actions para esta rama.
