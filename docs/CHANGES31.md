# cambios31 · Navegación más clara

## Cambios
- Se reduce la barra inferior de seis destinos a cinco: Inicio, Explorar, Salud, Comunidad y Más.
- Diario y Aprender pasan a un nuevo centro "Más", con tarjetas grandes, descripciones claras y accesos directos.
- La barra mantiene "Más" seleccionado mientras el usuario navega a Diario o Aprender, evitando perder el contexto de navegación.
- No se elimina ninguna sección ni sus datos: los destinos siguen accesibles desde el nuevo centro.

## Validación
- Pendiente de CI: compilación debug, tests y lint.
- Probar en emulador de teléfono estrecho y en un dispositivo real; revisar que las etiquetas de navegación no se corten.
- Confirmar que Salud, Comunidad, Diario y Aprender conservan sus rutas y estados.

## Próximas integraciones prioritarias
- Sincronizar ficha de mascota y foto con Firestore/Cloudinary.
- Completar sincronización remota de salud y diario, con estrategia offline y conflictos.
- Terminar invitaciones y gestión real de cuidadores para mascotas compartidas.
- Mejorar privacidad de imágenes: las cargas unsigned de Cloudinary siguen usando URLs públicas.
- Preparar notificaciones push salientes solo cuando exista backend confiable; no introducir facturación sin decisión explícita.
