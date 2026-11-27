# Probar Comunidad y mapas en iOS

La rama de desarrollo es codex/ios-auditoria-integral-2026-11. El proyecto se genera con XcodeGen. Si actualizas otra copia en tu Mac, haz Pull de la rama y ejecuta lo siguiente desde la raíz para que Xcode incluya los nuevos archivos Swift.

~~~sh
(cd ios && xcodegen generate)
~~~

Abre Pawly.xcodeproj, selecciona el esquema Pawly y pulsa Run. Para un iPhone físico selecciona tu Personal Team en Signing & Capabilities. La firma gratuita conserva las notificaciones locales de Agenda. La bandeja de avisos de Comunidad funciona con Firestore.

## Recorrido de prueba en el iPhone

| Pantalla | Acción | Resultado esperado |
|---|---|---|
| Más → He perdido mi mascota | Sin rellenar la zona ni conceder GPS, pulsa Localizar la zona del aviso. Introduce 41.3874, 2.1686, busca y confirma. | El mapa se abre inmediatamente. El aviso muestra el marcador aproximado en 41.39, 2.17. Abrir en Mapas lleva a esa zona. Este recorrido hasta la confirmación pasó en XCTest. |
| Selector del mapa | Busca un barrio con varios resultados, elige uno, desplaza el mapa y confirma. Repite con una dirección desconocida y con el permiso GPS denegado. | Se puede elegir manualmente el punto. Permitir GPS centra el mapa cuando llega una posición. La confirmación funciona aunque todavía no se haya obtenido el nombre de la calle. |
| Aviso publicado | Publica un aviso de prueba. Ábrelo desde otra cuenta. Edita la descripción, sustituye la foto por un vídeo y comprueba el mapa. | El mapa nuevo usa la celda incluida en la publicación. En Editar aviso se puede mover el punto y guardarlo. El mapa de la otra cuenta cambia a la nueva celda. Retirar zona del aviso elimina la línea Zona y las coordenadas, y pausa la alerta de proximidad activa. Al cambiar solo la dirección por escrito se conserva esa dirección pública y se descarta el marcador anterior. |
| Cartel | Genera y comparte el cartel con el contacto desactivado. Guárdalo como imagen. Activa el contacto y vuelve a generarlo. | La primera imagen omite el contacto. La segunda incluye el dato autorizado. Comprueba también la vista previa para imprimir si tu iPhone ofrece esa acción. |
| Multimedia | Abre la segunda foto de una galería de Diario, amplía y desplázala. Luego adelanta un vídeo en un chat y cierra el visor. | Se conserva la página elegida, se puede cambiar de foto y el vídeo deja de sonar al cerrar. Prueba también sin conexión. |
| Muro y comentarios | Busca una palabra con y sin tilde y un @usuario. Combina categoría, fotos y orden. Desde dos cuentas responde a un comentario y después a su respuesta. | Cargar más recorre las páginas. Avatar y nombre abren el perfil. Las respuestas se pliegan bajo el original. El aviso de la bandeja de su destinatario abre la respuesta. |
| Eventos | Crea un evento con vídeo, elige la ubicación, cambia el punto, marca interés desde otra cuenta y edita el evento como organizador. | La edición conserva los asistentes. Los filtros permiten combinar fechas, zona, radio, autoría e interés. La lista carga más eventos al solicitarlo. |
| Más → Compartir perfil | Selecciona dos destinatarios, uno desde una conversación y otro desde seguidos. Abre la tarjeta con la otra cuenta. Repite con un perfil privado. | Los destinatarios autorizados pueden abrir el perfil. Un perfil privado queda excluido del envío. Compartir fuera de Pawly entrega el nombre y @usuario como texto. |
| Accesibilidad e icono | Comprueba Más en iPhone pequeño, letras grandes, VoiceOver y modo oscuro. Cambia la lengua. Instala la app y vuelve a Inicio. | Comprueba los nombres de los controles del mapa, el espacio del encabezado y el icono de la huella en Inicio y en la biblioteca. |

## Configuración de staging

El selector de mapa y el redondeo funcionan en el dispositivo. La búsqueda de direcciones y la cartografía usan los servicios de Apple. Para intercambiar respuestas, editar eventos o avisos con medios y enviar tarjetas entre cuentas hacen falta las reglas del repositorio. Conecta los dos clientes al mismo Firebase de pruebas. Ejecuta desde la raíz sustituyendo el identificador por el proyecto de staging real.

~~~sh
firebase deploy --only firestore:rules,firestore:indexes --project ID_DE_STAGING
~~~

La búsqueda textual del muro utiliza términos completos normalizados. Las nuevas publicaciones de iOS escriben su índice. Para los datos históricos y las publicaciones posteriores de Android, usa credenciales de aplicación de Google con acceso al proyecto de staging. El primer comando del script informa y el segundo aplica las escrituras.

~~~sh
python3 -m venv /tmp/pawly-post-index
/tmp/pawly-post-index/bin/pip install google-cloud-firestore
/tmp/pawly-post-index/bin/python tools/index_pawly_posts.py --project ID_DE_STAGING
/tmp/pawly-post-index/bin/python tools/index_pawly_posts.py --project ID_DE_STAGING --apply
~~~

Para pasadas posteriores, el script admite --since con una fecha ISO y zona horaria. Los filtros de adjuntos y zona se aplican por página. La distancia de eventos se calcula sobre los eventos con coordenadas que se han cargado. Cargar más amplía ese conjunto.

El Worker valida el destinatario de las nuevas respuestas. Para usarlo en staging, prepara una copia de wrangler.toml junto al original con un nombre de Worker distinto, el ID y número del Firebase de prueba y la URL del Worker de prueba. En la carpeta backend/cloudflare-worker prepara los secretos con valores de staging y despliega esa configuración.

~~~sh
npx wrangler secret put FIREBASE_CLIENT_EMAIL --config wrangler.staging.toml
npx wrangler secret put FIREBASE_PRIVATE_KEY --config wrangler.staging.toml
npx wrangler secret put PHONE_DISCOVERY_HMAC_SECRET --config wrangler.staging.toml
npx wrangler deploy --config wrangler.staging.toml
~~~

Para las subidas reales, el cliente necesita los presets de imagen y vídeo configurados en Config.local.xcconfig. Las pruebas en el emulador verifican el propietario, tipo y carpeta de los medios registrados. Las cuotas y la entrega real dependen de las cuentas de staging. La bandeja interna y las pruebas del mapa se pueden probar con firma gratuita. El envío remoto de Apple queda pospuesto.

## Compatibilidad y límites

Los comentarios previos siguen siendo raíces válidas. Android puede mostrar el texto de las respuestas en su lista plana y el texto legible de las tarjetas en sus chats. Los participantes anteriores y los contadores de eventos no cambian al sustituir el adjunto. Eventos y avisos admiten una foto o un vídeo por registro. El vídeo admite hasta 60 segundos y 40 MB. Las colecciones fotográficas existentes mantienen su galería. Pawly comparte el identificador público externamente como texto mientras no exista un enlace público de perfil que funcione para sus destinatarios.

La matriz de la auditoría anterior contiene las comprobaciones aún pendientes para una publicación pública, incluidas la eliminación integral de cuenta, la decisión sobre el acceso equivalente a Google, la privacidad de los medios y la validación de servicios reales. [Matriz de la fase anterior](AUDITORIA_2026_11.md)
