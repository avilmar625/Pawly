# Pawly Firebase Functions v2

Funciones opcionales de notificaciones y eliminación integral. Requieren Node.js 20, Firebase CLI y plan Blaze (Cloud Functions). No se despliegan automáticamente.

```bash
cd functions
npm install
cd ..
firebase functions:secrets:set CLOUDINARY_CLOUD_NAME
firebase functions:secrets:set CLOUDINARY_API_KEY
firebase functions:secrets:set CLOUDINARY_API_SECRET
firebase deploy --only functions
```

Los mensajes nuevos generan un push al destinatario y las publicaciones avisan a seguidores. La eliminación procesa solicitudes pendientes, borra datos Firestore/Auth y elimina medios de Cloudinary bajo las carpetas Pawly identificadas por UID.

Antes de producción: prueba con cuentas y medios desechables, configura la clave APNs en Firebase Cloud Messaging, revisa cuotas de Cloudinary/Firestore y valida reglas. No se ha desplegado ni activado facturación desde este repositorio. No actives Blaze sin revisar y aceptar sus costes.
