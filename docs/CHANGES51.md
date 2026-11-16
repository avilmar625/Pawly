# cambios51 — perfiles, internacionalización y experiencia de cuenta

## Perfiles particulares y profesionales
- El perfil de Comunidad distingue entre cuenta particular y profesional.
- Los perfiles profesionales pueden indicar especialidad/servicio, zona de trabajo, descripción y contacto opcional.
- Los campos se guardan en Firestore y se muestran en el perfil público cuando la persona habilita su visibilidad.
- La etiqueta identifica el tipo de cuenta; no equivale a una verificación de identidad, titulación o licencia.
- Los datos de contacto pueden quedar visibles públicamente. La interfaz advierte que se use un correo/teléfono profesional.

Limitación: los perfiles profesionales todavía no se incorporan como resultados propios al directorio de Servicios ni admiten reservas, pagos o reseñas. El directorio actual sigue enlazando búsquedas externas en Maps/web.

## Cuenta, ayuda e información legal
- Se retiran los controles de cierre de sesión de Comunidad.
- El cierre de sesión pasa a Más, con confirmación y limpieza del estado de Credential Manager.
- Se añaden Invita a un amigo (menú nativo de compartir), Contacta con nosotros (abre un borrador de correo), Política de privacidad y Términos y condiciones.
- Los enlaces legales apuntan a los documentos de trabajo del repositorio. Los dos documentos siguen siendo BORRADORES NO PUBLICABLES hasta completar responsable, domicilio, correos de contacto, fecha y revisión jurídica.
- El destinatario de soporte aún no está configurado; el borrador de correo se abre sin destinatario para no inventar una dirección.
- La invitación todavía no incluye un enlace de descarga de tienda porque Pawly no tiene una ficha de distribución confirmada.

## Idiomas
- Selector: español, catalán, francés e inglés.
- Se añade un contexto de idioma compartido para las pantallas principales y se amplía el catálogo de traducciones en Inicio, Explorar, Salud, Diario, Aprender, Comunidad, perfiles, cuidados, servicios y Más.
- El cambio de idioma se aplica sin reiniciar la app y la preferencia se conserva localmente por cuenta/dispositivo.
- Se añaden pruebas unitarias de traducción de navegación, idiomas y texto dinámico con nombre de mascota.

Limitación: textos que llegan de fuentes externas, mensajes del backend y algunas cadenas dinámicas antiguas pueden conservar su idioma original. Hace falta una revisión visual de cada flujo y una pasada final de localización antes de publicar.

## Ficha PDF
- La ficha combinada incluye nombre, especie, raza, edad, características, descripción, historial del pasaporte sanitario y rutinas.
- Se ofrecen dos acciones distintas: guardar en el dispositivo mediante el selector de documentos de Android y compartir mediante el selector del sistema.
- El archivo se genera localmente; no se sube a Firebase ni a Cloudinary.

## Acabado visual
- La navegación inferior utiliza acentos cromáticos diferenciados por destino.
- Las tarjetas de Más emplean iconos y fondos de color por categoría, manteniendo contraste y jerarquía.
- Se evita repetir accesos a Comunidad y Salud dentro de Más.

## Configuración Cloudinary
Mantener en local.properties las propiedades CLOUDINARY_CLOUD_NAME, CLOUDINARY_IMAGE_UPLOAD_PRESET y CLOUDINARY_VIDEO_UPLOAD_PRESET.
Los dos presets deben ser unsigned y el API Secret nunca debe incluirse en el cliente ni subirse al repositorio.

## Validación y pendientes
- Validación automática de Kotlin/tests, Android Lint, APK debug y AAB release unsigned en GitHub Actions.
- Pendiente: prueba funcional en dispositivo físico, completar los datos de soporte y responsable legal, revisión jurídica, verificación de profesionales, integración de perfiles profesionales en el directorio y port de estos cambios a la rama iOS.