# cambios32 · Rediseño visual

## Objetivo
Dar a Pawly una identidad visual más cuidada, cálida y contemporánea, manteniendo las funciones existentes.

## Cambios
- Rediseño de Inicio con jerarquía editorial, saludo contextual, hero de mascota, métricas compactas y rutina agrupada en una única tarjeta.
- Tarjetas con bordes sutiles y superficies limpias en lugar de sombras repetidas.
- Acceso de exploración destacado y tarjeta secundaria para Aprender.
- Cabecera de marca Pawly más reconocible y botón de ajustes compacto.
- Barra inferior con cinco destinos y una superficie elevada con esquinas superiores redondeadas.
- Sistema global de formas y paleta refinado.
- Corrección del contraste del color primario en modo oscuro.

## Estado de integraciones
- Firebase Authentication y comunidad: integrados.
- Conversaciones y mensajes: Firestore en tiempo real.
- Fotografías de publicaciones y chat: Cloudinary con preset unsigned; URLs públicas.
- Pasaporte sanitario y plan de cuidados: sincronización Firestore con copia local y migración de datos.
- Ficha principal/foto de mascota y diario: todavía requieren sincronización cloud completa.
- Push saliente: requiere backend confiable; no se activa facturación.

## Validación manual pendiente
- Probar Inicio en Medium Phone API 37 y en el Redmi Note 8 Pro.
- Revisar modo claro y oscuro, tamaños de fuente grandes y nombres de mascota largos.
- Confirmar que los cinco destinos inferiores y los accesos de Más funcionan.
- Verificar que el hero usa la fotografía seleccionada y que no se corta el texto.
