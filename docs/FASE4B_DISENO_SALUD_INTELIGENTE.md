# Fase 4B — Salud inteligente (diseño y alcance)

## Objetivo
Convertir el pasaporte sanitario de Pawly en un historial útil y accionable por mascota, sin duplicar rutinas ni introducir servicios de pago.

## Situación técnica de partida
- El modelo actual `PassportEntry` contiene `id`, `type`, `title`, `detail` y `date`.
- `PetCareLocalStore` ya persiste pasaporte, rutinas y registros en SharedPreferences con ámbito de usuario y clave por mascota. Las claves v16 existentes deben conservarse.
- Las rutinas `CareTask` y los registros `CareLogEntry` son conceptos distintos del historial sanitario: no se deben reutilizar como si fueran vacunas o tratamientos.
- Existe un planificador de recordatorios Android (`CareReminderScheduler`); antes de integrarlo se debe revisar su contrato, permisos y comportamiento real. No mostrar recordatorios como activos si el sistema no los ha programado.
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
- Respetar permisos de notificaciones y cambios de Android recientes; si no hay permiso, explicar que el registro se guarda pero no se enviará aviso.
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
