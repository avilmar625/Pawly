# Eventos y mapa de Pawly

La rama `cambios52-final` incorpora una primera versión de eventos comunitarios y un mapa de sedes de eventos.

## Firestore

La colección raíz `events` contiene datos del evento y del lugar público de celebración: título, descripción, categoría, ubicación, fecha, coordenadas, organizador, contador de interés y fecha de creación.

Las personas interesadas se guardan en `events/{eventId}/interested/{uid}`. Se exige correo verificado para publicar eventos o marcar interés. Las reglas de `firestore.rules` limitan la creación, los cambios de contador y el acceso a las participaciones.

## Publicación de reglas

Después de actualizar la rama, publica las reglas de Firestore desde Firebase Console o con Firebase CLI. Sin ese paso, la interfaz de eventos no podrá leer ni escribir la colección.

## Mapa

La vista representa las coordenadas de los eventos publicados. Utiliza Leaflet y teselas de OpenStreetMap, con atribución visible. El mapa no solicita ni almacena la ubicación del usuario y no coloca personas en el mapa.

Las coordenadas deben corresponder al lugar público del evento, nunca al domicilio privado de una persona.

## Privacidad y evolución

Las sugerencias consultan exclusivamente perfiles marcados como públicos y excluyen cuentas bloqueadas o ya seguidas. La ubicación de personas no se expone. Una futura capa de profesionales o perfiles cercanos requerirá consentimiento explícito y controles de precisión.
