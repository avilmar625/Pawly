# Cambios 59 — Fechas de eventos y cambio horario

- El parser de eventos ya no deja que Java ajuste silenciosamente una hora local inexistente durante el salto al horario de verano.
- También rechaza las horas duplicadas durante el retorno al horario de invierno, porque el formulario no permite elegir cuál de los dos instantes representa.
- Se añaden pruebas para ambos cambios de hora en la zona `Europe/Madrid`.
- Las fechas válidas siguen convirtiéndose usando la zona horaria local del dispositivo y se conserva el identificador de zona al guardar el evento.

Esto evita publicar eventos a una hora distinta de la que introdujo su organizador.
