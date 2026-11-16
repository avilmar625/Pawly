# Cambios 57 — Fiabilidad de recordatorios y validación

## Recordatorios locales
- Se extrae el análisis de fecha/hora a `CareReminderDateTimeParser`, una unidad independiente y comprobable.
- El parser rechaza fechas imposibles, valores incompletos y texto sobrante. El parser anterior podía aceptar un prefijo válido aunque el valor almacenado tuviera caracteres adicionales.
- Se conserva la compatibilidad con el separador histórico `|` que aparece en entradas antiguas de preferencias.
- El programador de alarmas utiliza ahora el parser común, sin cambiar el formato persistido ni la restauración tras reinicio/actualización.

## Pruebas
- Fecha válida con zona horaria explícita.
- Día imposible.
- Entrada incompleta y texto sobrante.
- Compatibilidad con el separador histórico.

## Estado
Los cambios están en la rama `cambios56-arquitectura`. La validación definitiva corresponde al workflow de GitHub Actions asociado al último commit. No requiere ninguna configuración adicional del propietario ni servicios de pago.
