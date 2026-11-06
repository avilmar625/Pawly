# cambios21 — Identidad estable de mascotas

## Cambios
- La pantalla principal resuelve un identificador persistente por mascota y cuenta.
- El identificador inicial conserva exactamente el algoritmo determinista anterior, por lo que los documentos ya existentes en Firestore siguen en la misma ruta.
- Al cambiar el nombre de la mascota, Pawly traslada la asociación del identificador al nuevo nombre antes de actualizar el perfil.
- Pasaporte y Cuidados reciben el identificador desde el perfil, en lugar de calcularlo de forma independiente a partir del nombre.
- El identificador no se elimina al borrar el perfil local, evitando borrar o desvincular accidentalmente el historial remoto.

## Compatibilidad y alcance
- No requiere cambios en las reglas ni en los índices de Firestore.
- No requiere volver a desplegar Firebase.
- No mueve ni duplica documentos existentes.
- Los datos locales de pantallas que todavía usan el nombre como clave (por ejemplo, diario y agenda) siguen pendientes de migración.
- Si ya existía una asociación para el nombre de destino, se conserva para evitar sobrescribir su identidad.

## Prueba recomendada
1. Actualizar la app e iniciar sesión con la misma cuenta.
2. Comprobar que el pasaporte y las rutinas actuales siguen apareciendo.
3. Cambiar temporalmente el nombre de una mascota desde su perfil.
4. Entrar de nuevo en Salud y verificar que conserva los mismos registros.
5. Volver a dejar el nombre original si se desea.

## Siguiente fase
Unificar los perfiles de mascotas en un repositorio propio, con ID, especie, fecha de nacimiento, fotografía y metadatos compartidos entre Inicio, Salud y Comunidad. Después, migrar Diario y Agenda a ese mismo ID.
