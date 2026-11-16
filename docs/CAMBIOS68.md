# cambios68 — Próximo cuidado unificado en Hoy

**Fecha:** 16 de noviembre de 2026  
**Rama:** `cambios68-hoy-proximo-cuidado`  
**Base:** `cambios67-cronologia-y-localizacion`  
**HEAD al documentar:** `f562a37a9123e5c96d46a9c3ec50bcc5ca7210e8`

## Objetivo

Conectar «Próximamente» de Hoy a los datos reales que ya existen en Pasaporte, Salud y Calendario, elegir el elemento temporalmente más cercano y abrir la pantalla que corresponde. Mantener todas las fuentes y los accesos existentes.

## Cambios

- Se incorpora `HomeUpcomingItem.kt` con selección determinista y comprobable.
- Pasaporte: considera entradas futuras con fecha (excepto Peso), cuya fecha no incluye hora.
- Salud: analiza las fechas guardadas en el formato `yyyy-MM-dd|HH:mm`; descarta avisos ya caducados.
- Calendario: lee los recordatorios JSON y conserva su timestamp, título y categoría; descarta los vencidos o sin fecha válida.
- La tarjeta presenta título, tipo y día del elemento más cercano.
- El toque abre Calendario, la sección Agenda de Mi perro o Pasaporte, según la procedencia.
- El destino solicitado se consume una sola vez para evitar que la próxima entrada a Mi perro se quede fijada en una sección anterior.
- Se añadieron tres pruebas unitarias para la selección entre fuentes, recordatorios caducados y ausencia de elementos futuros.
- Se corrigió la llamada a `DateFormat.getDateInstance` detectada al compilar cambios67, pasando `DateFormat.DEFAULT` y la locale de Pawly.

## Limitaciones explícitas

- La tarjeta de Hoy agrega Pasaporte, fechas de Salud y recordatorios del Calendario guardados en la app. No incluye todavía los eventos comunitarios que carga la pantalla Calendario.
- Las entradas del Pasaporte solo tienen fecha, no hora; se ordenan al comienzo del día local.
- No se ha probado todavía la navegación en un dispositivo físico ni el caso con varias mascotas y recordatorios sincronizados.

## Estado de CI

- cambios66: tests Kotlin pasaron; Lint falló por cuatro usos no observables de Locale.
- cambios67: Firestore y Worker pasaron; compilación Kotlin se detuvo por una sobrecarga incorrecta de `DateFormat.getDateInstance`.
- cambios68: la sobrecarga se ha corregido. Las validaciones de Android y Firestore se han relanzado en el HEAD de esta rama; consultar Actions para el resultado definitivo.
- No declarar APK Debug ni AAB Release hasta que el workflow termine correctamente y los artefactos estén publicados.

## Pruebas manuales pendientes

1. Crear un recordatorio futuro en Calendario y comprobar que Hoy lo muestra y abre Calendario.
2. Programar una fecha futura de Vacunación/Desparasitación/Revisión y comprobar que Hoy abre Mi perro → Agenda.
3. Añadir una entrada futura al Pasaporte y comprobar que abre Mi perro → Pasaporte.
4. Combinar varias fuentes y verificar que se selecciona la fecha/hora más cercana.
5. Eliminar o dejar vencer un recordatorio y verificar que el siguiente elemento ocupa su lugar.
6. Repetir con más de una mascota y cambiar idioma a catalán/francés/inglés.

## Restricciones de producto

No eliminar Eventos, Lugares, teléfonos, búsquedas, mapas, Comunidad, Mensajería, moderación, Cloudinary, profesionales, mascotas perdidas, backup, PDF, salud ni cuidados compartidos. Mantener los servicios gratuitos; no activar Blaze, Firebase Storage ni Workers Paid sin autorización.
