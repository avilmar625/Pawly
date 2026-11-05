# Política de privacidad de Pawly — BORRADOR

> **No publicar esta versión sin completar y verificar los campos entre corchetes.** El responsable debe revisar el documento con asesoramiento legal y contrastarlo con el comportamiento real de la versión que se publique.

**Última revisión del borrador:** 5 de noviembre de 2026

## 1. Responsable del tratamiento

- Responsable: [NOMBRE O RAZÓN SOCIAL DEL RESPONSABLE]
- Domicilio: [DOMICILIO PROFESIONAL O SOCIAL]
- Contacto de privacidad: [EMAIL DE PRIVACIDAD]
- Contacto general y soporte: [EMAIL DE SOPORTE]
- Delegado de Protección de Datos, si procede: [CONTACTO / NO APLICA JUSTIFICADAMENTE]

No publicar la aplicación hasta identificar correctamente al responsable del tratamiento y ofrecer un medio de contacto válido.

## 2. Datos que puede tratar Pawly

La versión y configuración final deben determinar exactamente qué categorías se recogen. El diseño actual contempla:

- Datos de cuenta: correo, identificador Firebase, nombre de usuario y datos básicos del perfil.
- Datos de mascotas: nombre, especie, raza, edad, fotografía, características y descripción.
- Contenido aportado por usuarios: publicaciones, comentarios, mensajes, denuncias, bloqueos y fotografías.
- Datos técnicos: token de notificaciones push, información de dispositivo y registros necesarios para seguridad y funcionamiento.
- Ubicación: solo cuando una función que la necesita la solicita y el usuario concede el permiso. Debe documentarse si se procesa en el dispositivo o se transmite a un proveedor.
- Datos locales: perfiles, tareas, agenda, favoritos y diario que algunas pantallas guardan en el almacenamiento privado del dispositivo.
- Documentos de salud: solo si una versión futura incorpora carga de cartillas, informes u otros documentos. No activar esa función sin actualizar esta política y las medidas de seguridad.

No se deben solicitar datos de salud de personas. La información veterinaria de una mascota puede, no obstante, revelar hábitos o circunstancias de la familia y debe tratarse con cuidado.

## 3. Finalidades y bases jurídicas

| Finalidad | Datos | Base jurídica que debe confirmarse |
|---|---|---|
| Crear y mantener la cuenta | correo, UID, perfil | Ejecución del contrato |
| Mostrar comunidad, mensajes y publicaciones | contenido, perfil, interacciones | Ejecución del contrato |
| Seguridad, bloqueo y gestión de denuncias | registros de abuso, bloqueos y reportes | Interés legítimo y/o cumplimiento de obligaciones |
| Enviar avisos solicitados | token del dispositivo y preferencias | Consentimiento o prestación solicitada, según el aviso |
| Mostrar búsquedas de lugares | consulta y ubicación si se concede | Consentimiento para ubicación y prestación solicitada |
| Cumplir obligaciones legales | datos estrictamente necesarios | Obligación legal |

El responsable debe revisar y documentar las bases jurídicas definitivas. No utilizar el consentimiento como base genérica cuando corresponda otra base.

## 4. Proveedores y destinatarios

La arquitectura actual puede utilizar los siguientes proveedores, cuya configuración y contratos deben revisarse antes de publicar:

- **Google Firebase Authentication y Cloud Firestore:** autenticación y sincronización de datos de comunidad.
- **Firebase Cloud Messaging:** entrega de notificaciones push cuando se active un backend de envío.
- **Cloudinary:** alojamiento de fotografías mediante un preset de carga unsigned. El responsable debe confirmar región, condiciones, retención, medidas contractuales y transferencias internacionales de la cuenta concreta.
- **Google Maps:** se abre externamente para búsquedas de lugares; revisar qué datos comparte el sistema operativo o la aplicación de mapas al abrirlo.
- **Google News RSS y YouTube:** enlaces externos para noticias y búsquedas de vídeos; al abrirlos se aplican las políticas de sus respectivos proveedores.
- **Google Credential Manager:** inicio de sesión mediante Google, si el usuario lo elige.

No se debe afirmar que todos los datos se almacenan en la Unión Europea hasta comprobar la región real de cada servicio y las condiciones contractuales.

## 5. Conservación

Completar antes del lanzamiento una tabla con plazos reales para:
- Cuenta y perfil: [PLAZO].
- Publicaciones y comentarios: [PLAZO / TRATAMIENTO AL BORRAR CUENTA].
- Mensajes privados: [PLAZO / TRATAMIENTO AL BORRAR CUENTA].
- Denuncias y registros antifraude: [PLAZO JUSTIFICADO].
- Fotografías en Cloudinary: [PLAZO Y PROCEDIMIENTO DE BORRADO].
- Solicitudes de eliminación: [PLAZO DE CONSERVACIÓN DE EVIDENCIA].
- Datos locales: se mantienen hasta que el usuario borra los datos de la app o la desinstala, salvo que la función indique otra cosa.

## 6. Derechos de las personas

Las personas pueden solicitar acceso, rectificación, supresión, oposición, limitación y portabilidad cuando proceda, así como retirar consentimientos. Contacto: [EMAIL DE PRIVACIDAD]. También pueden presentar una reclamación ante la Agencia Española de Protección de Datos (AEPD).

La eliminación de una cuenta debe borrar los datos asociados, salvo los que deban conservarse legalmente, explicando de forma transparente cualquier excepción.

## 7. Seguridad

Pawly debe aplicar control de acceso, reglas de Firestore probadas, protección de credenciales, control de abuso, copias y procedimientos de respuesta a incidentes. No incluir secretos de servidor en la aplicación. Revisar periódicamente permisos, proveedores y accesos administrativos.

## 8. Menores

[DEFINIR EDAD MÍNIMA, TRATAMIENTO DE CUENTAS DE MENORES Y MEDIDAS DE VERIFICACIÓN]. La ficha de Play Store y el diseño de la aplicación deben ser coherentes con la audiencia real. No dirigir la aplicación a menores sin adaptar el producto y cumplir las políticas correspondientes.

## 9. Cambios y contacto

Publicar la fecha de entrada en vigor, el historial de cambios y un contacto válido. La política debe estar accesible desde la aplicación y desde la ficha de Google Play.

---

## Comprobaciones obligatorias antes de publicar

- [ ] Sustituir todos los campos entre corchetes.
- [ ] Confirmar responsable, domicilio y correo de privacidad.
- [ ] Verificar región, contratos y transferencias de Firebase y Cloudinary.
- [ ] Auditar permisos y SDK incluidos en el APK/AAB final.
- [ ] Completar Data safety de Play Console con datos reales, no estimaciones.
- [ ] Publicar la política en una URL HTTPS estable y accesible sin iniciar sesión.
- [ ] Añadir un enlace visible desde la aplicación.
- [ ] Implementar eliminación real de cuenta y datos en app y mediante recurso web externo.
- [ ] Definir plazos de conservación y procedimiento para solicitudes.
- [ ] Revisar el texto con asesoramiento legal antes de producción.
