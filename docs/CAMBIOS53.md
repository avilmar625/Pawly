# Cambios53 — calidad, accesibilidad y estabilidad

Esta rama parte de `cambios52` para que los cambios anteriores no se pierdan.

## Cambios incluidos

- Alinea `compileSdk` y `targetSdk` con Android API 36, que es la plataforma instalada por el flujo de integración continua. Antes, el workflow instalaba API 36 pero el módulo declaraba API 37; esa discrepancia podía impedir que CI configurase el proyecto.
- Completa el catálogo específico de Eventos y Mapa en euskera y amplía los textos de interfaz, validación y errores en inglés, catalán y francés.
- Añade pruebas de regresión para los textos de eventos en los cinco idiomas.
- Refuerza la configuración del WebView del mapa: desactiva el acceso a archivos y contenido local, desactiva DOM storage y bloquea contenido mixto. JavaScript sigue habilitado porque Leaflet lo necesita.
- Hace que el formulario de creación de eventos pueda desplazarse en pantallas pequeñas, evitando que los últimos campos y botones queden fuera de la zona visible.

## Validación pendiente

El workflow de GitHub Actions ejecuta tests, Android Lint, APK debug y AAB release. No se debe considerar la rama validada hasta que ese workflow termine correctamente. La publicación de las reglas de Firestore sigue siendo necesaria para que Eventos funcione en Firebase.

## Cómo abrir esta versión

Abrir directamente la rama `cambios53`:
https://github.com/avilmar625/Pawly/tree/cambios53

No es necesario cambiar la rama principal ni fusionarla para probarla.
