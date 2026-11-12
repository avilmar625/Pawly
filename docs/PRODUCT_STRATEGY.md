# Pawly — estrategia de producto (cambios15)

## Posicionamiento

**Pawly es el espacio de confianza para cuidar de todas las mascotas en el día a día.** No pretende ser otra red social de fotos: ayuda a una familia a recordar, organizar, compartir y resolver lo importante de la vida de su animal.

Propuesta breve para la ficha de tienda:

> La vida de tu mascota, bien organizada. Salud, documentos, rutinas, lugares y una comunidad que ayuda.

## Qué enseña la competencia

| Producto | Fortaleza visible | Espacio que Pawly puede ocupar |
|---|---|---|
| DogPack | Comunidad, mapa dog-friendly, alertas de perros perdidos, servicios y funciones sociales | Ir más allá del perro y de la publicación social: cuidados, documentos y coordinación familiar |
| 11pets | Historial de salud, tratamientos, higiene, alimentación y recordatorios | Conectar el historial con la vida cotidiana, los cuidadores, la comunidad local y la búsqueda de ayuda |
| Directorios y apps de adopción | Catálogos y avisos de animales | Mantener una ficha útil después de adoptar, no solo durante la búsqueda |

No competir inicialmente por número de lugares, usuarios o funciones de IA. Esas ventajas requieren escala, datos fiables y operación constante.

## Diferenciadores que merecen construirse

### 1. Pasaporte digital de la mascota
- Ficha para perro, gato, conejo, ave, roedor, reptil y otros animales.
- Historial de vacunas, desparasitación, alergias, medicación, intervenciones y visitas.
- Adjuntar cartilla, informes, analíticas y facturas como documentos privados.
- Exportar un resumen PDF para una consulta o una urgencia.
- Control de quién puede ver o recibir cada documento.
- Fechas, autor y fuente de cada dato; distinguir siempre lo introducido por la familia de lo validado por una clínica.

### 2. Cuidado compartido de verdad
- Invitar a pareja, familiares o cuidadores a la misma mascota.
- Roles claros: propietario, cuidador y solo lectura.
- Tareas, paseos, alimentación y medicación con responsable y confirmación.
- Registro de quién hizo qué y cuándo, evitando duplicar comidas o dosis.
- Revocar el acceso inmediatamente cuando termina el cuidado compartido.

Este flujo es un diferenciador más defendible que otra red social: resuelve un problema diario y crea un motivo para volver.

### 3. Lugares útiles y fiables
- Fichas de parques, playas, alojamientos, clínicas, tiendas y servicios.
- Indicar requisitos reales: correa, bozal, tamaño, horarios, restricciones estacionales y especies admitidas.
- Mostrar quién confirmó la información y cuándo se revisó por última vez.
- Permitir sugerir correcciones y reportar datos obsoletos.
- No llamar “verificado” a un lugar solo porque aparezca en Google Maps.

### 4. Red de ayuda y mascotas perdidas
- Avisos estructurados para todas las especies, con fecha, zona aproximada y estado.
- Botón para marcar “encontrada / reunida” y cerrar el aviso.
- Cartel compartible con QR o enlace público, sin exponer domicilio ni teléfono.
- Alertas por proximidad solo con consentimiento y backend seguro.
- Contactos y pasos oficiales por municipio; advertir que Pawly no sustituye a policía, veterinarios ni servicios municipales.

### 5. Información que merece confianza
- Guías revisadas por veterinarios, educadores caninos y protectoras identificadas.
- Fecha de revisión, autoría, fuentes y alcance de cada contenido.
- Separar educación general de recomendaciones clínicas.
- No ofrecer diagnóstico por IA, dosis ni cambios de tratamiento.

## Experiencia de producto

La pantalla Inicio debería responder en pocos segundos:
1. ¿Qué necesita hoy cada mascota?
2. ¿Quién se encarga?
3. ¿Hay algo pendiente o urgente?
4. ¿Dónde está su documentación?

Evitar que la navegación se convierta en seis pestañas inconexas. Unificar el acceso a la mascota activa y ofrecer acciones contextuales.

### Primeros 90 segundos
- Crear cuenta o continuar en modo local.
- Añadir nombre, especie y foto.
- Elegir si se quiere configurar cuidados ahora o más tarde.
- Importar documentos opcionalmente.
- Invitar a otro cuidador como paso opcional, nunca obligatorio.

## Hoja de ruta recomendada

### P0 — Antes de beta pública
- Compilación estable, pruebas en dispositivos reales y corrección de errores.
- Privacidad, condiciones, soporte, moderación y proceso real de eliminación de cuenta.
- Perfiles multi-especie coherentes en todas las pantallas.
- Seguridad de Firebase y Cloudinary; App Check y pruebas de reglas.
- Corregir datos demo y estados vacíos; no mostrar botones que no hagan lo que prometen.

### P1 — Producto que la gente conserva
- Pasaporte de salud y documentos privados.
- Compartir mascota con familiares y cuidadores.
- Registro de medicación y tareas con confirmación para evitar duplicados.
- Exportar historial para el veterinario.
- Notificaciones fiables, configurables y con control de frecuencia.

### P2 — Diferenciación local
- Directorio curado con clínicas, protectoras y lugares dog/pet-friendly.
- Sistema de verificación con fecha, fuente y reportes de usuarios.
- Avisos de mascotas perdidas por proximidad y carteles compartibles.
- Colaboraciones con clínicas y protectoras, con perfiles profesionales verificados.

### P3 — Escala
- Más especies y necesidades específicas.
- Portal profesional para clínicas y protectoras.
- Integraciones de citas solo con acuerdos reales.
- Suscripción opcional para almacenamiento adicional, varios cuidadores y herramientas avanzadas.

## Monetización y confianza

- Mantener gratis las funciones esenciales de cuidado, exportación básica y avisos de emergencia.
- Premium opcional: más almacenamiento documental, varios cuidadores, informes avanzados y personalización.
- No vender datos de mascotas ni ubicación.
- No cobrar por aumentar la visibilidad de un aviso de pérdida.
- Patrocinios y recomendaciones comerciales siempre identificados.
- No introducir marketplace ni telemedicina hasta disponer de proveedores, soporte, garantías y cumplimiento legal.

## Métricas que importan

- Activación: porcentaje que crea una ficha de mascota y completa una acción útil.
- Retención D7 y D30, no solo descargas.
- Mascotas con al menos un cuidado o documento registrado.
- Recordatorios completados y descartados.
- Familias con más de un cuidador activo.
- Lugares confirmados recientemente y correcciones atendidas.
- Tiempo de respuesta a denuncias y avisos.
- Sesiones sin crash, errores de sincronización y tiempos de carga.

## Principio de diseño

Mantener la identidad visual actual de Pawly —verde bosque, tonos cálidos, tarjetas limpias, iconografía amable y jerarquía clara— pero con una interfaz más funcional que ornamental. Priorizar legibilidad, accesibilidad, rapidez y estados claros. La confianza debe notarse en los detalles, no en promesas grandilocuentes.


## Evolución cambios49 — servicios profesionales y navegación integrada

La rama `cambios49` incorpora una primera capa de descubrimiento de servicios profesionales desde Más: adiestramiento, etología, veterinaria, urgencias, peluquería, paseos, cuidadores, residencias, fisioterapia, nutrición y tiendas. La búsqueda se deriva a Google Maps o a la web con especialidad y zona. No se muestran fichas, precios, reseñas ni disponibilidad inventados y no se presenta esta fase como un marketplace con reservas o pagos.

Los avisos de mascotas perdidas/encontradas se mantienen dentro de Comunidad para reutilizar las categorías, moderación y mensajería existentes. Salud permanece en la navegación principal y conserva el pasaporte, los cuidados, los recordatorios, la sincronización disponible y el PDF; no se duplica su acceso en Más.

### Siguiente salto para servicios profesionales

Para convertir el directorio en contratación real se necesita una fase de producto independiente: alta profesional, verificación de identidad y credenciales, fichas gestionadas por cada proveedor, disponibilidad, solicitud de cita, cancelaciones, notificaciones, moderación, soporte y marco legal. No se debe habilitar cobro ni publicar valoraciones sin una infraestructura que permita gestionarlos de forma fiable.

### Criterios de calidad para las siguientes versiones

- Mantener una única fuente de verdad para cada dato de mascota.
- Separar los datos locales de los sincronizados y mostrar el estado real.
- No presentar datos de demostración como servicios reales o profesionales verificados.
- Mantener Salud como destino principal sin accesos duplicados.
- Priorizar accesibilidad, legibilidad y estados vacíos útiles sobre decoración.
- Probar en modo local, sesión Firebase, modo oscuro y dispositivos de tamaño reducido antes de integrar.
