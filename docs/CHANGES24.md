# cambios24 · Mensajería enriquecida y colaboración

## Funciones añadidas
- Adjuntar fotografías a mensajes privados.
- Cargar fotografías usando Cloudinary y la sesión verificada.
- Confirmación de lectura por mensaje con el campo `readBy`.
- Etiqueta «Visto» en mensajes propios cuando el destinatario los ha leído.
- Marcado automático de mensajes entrantes como leídos al recibir snapshots.
- Validación y límites de texto y URL de imagen.
- Reglas Firestore de creación/lectura de adjuntos y actualización restringida de recibos de lectura.
- Índice para consultas de mensajes por remitente y fecha.

## Conservación y compatibilidad
Los mensajes de texto anteriores mantienen valores por defecto para `imageUrl` y `readBy`; no se requiere migración de documentos existentes.

## Activación
Desplegar reglas e índices de Firestore desde la raíz del proyecto:
`npx --yes firebase-tools deploy --only firestore:rules,firestore:indexes --project pawly-83dca`

## Pruebas manuales
1. Compilar e instalar en dos dispositivos/cuentas verificadas.
2. Iniciar una conversación y enviar texto.
3. Adjuntar una fotografía y enviarla.
4. Abrir el chat con la segunda cuenta y comprobar que el mensaje se marca como leído.
5. Comprobar que el emisor ve «Visto».
6. Bloquear una cuenta y comprobar que la conversación no permite enviar mensajes.

## Pendiente
- Notificaciones push reales requieren Cloud Functions/otro backend de confianza y decisión explícita sobre el plan de facturación.
- Los perfiles compartidos de mascota todavía no exponen pasaporte y rutinas a cuidadores; la migración debe ser transaccional y no destructiva.
- Sin compilación local ni despliegue de Firebase confirmados en esta rama.
