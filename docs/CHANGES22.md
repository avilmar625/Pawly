# cambios22 — Informes PDF compartibles

## Cambios
- Añadido un generador local de PDF para el pasaporte sanitario.
- Añadido un generador local de PDF para el plan de cuidados y responsables.
- Los informes admiten varias páginas y texto largo con ajuste de línea.
- Los archivos se guardan en la caché privada y se comparten mediante Android Sharesheet y FileProvider; no se publica ningún enlace abierto.
- Los documentos incorporan fecha de generación y una advertencia de que no sustituyen a la cartilla oficial ni a la historia clínica veterinaria.
- Se incorporó el workflow Android para validar cambios22.

## Prueba recomendada
1. Abrir Salud > Pasaporte y generar un PDF con y sin registros.
2. Compartirlo con Archivos/Drive/WhatsApp y comprobar que se abre correctamente.
3. Repetir desde Salud > Cuidados, con varias rutinas y texto largo.
4. Verificar que no se solicita permiso de almacenamiento y que el PDF no se sube automáticamente a ningún servidor.

## Próximas fases
- Ficha completa de mascota (perfil, foto, datos básicos y salud) exportable como un único PDF.
- Perfiles compartidos mediante invitaciones, roles y permisos por mascota.
- Mensajería privada en tiempo real con Firestore; notificaciones push mediante backend seguro cuando esté configurado.
