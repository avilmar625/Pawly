# cambios49 — Pawly, plataforma integral para mascotas

## Objetivo

Agrupar en una evolución coherente las mejoras previstas para servicios profesionales, organización, salud, comunidad, experiencia visual y mantenimiento técnico. La rama parte de `cambios48`; no modifica esa rama.

## Funcionalidad incorporada

### Directorio de servicios profesionales
- Nuevo acceso desde **Más → Servicios profesionales**.
- Categorías: adiestramiento, etología, veterinarios, urgencias, peluquería, paseadores, cuidadores, residencias, fisioterapia, nutrición, tiendas y otros servicios.
- Búsqueda por especialidad y ciudad/zona.
- Apertura de búsquedas en Google Maps y alternativa web.
- Consejos de comprobación antes de contratar.
- No se inventan proveedores, tarifas, valoraciones, disponibilidad ni credenciales. Los resultados pertenecen a servicios externos y deben verificarse.
- No se simulan reservas ni pagos: requerirán acuerdos con profesionales, backend, gestión de disponibilidad, soporte y cumplimiento legal.

### Navegación y accesos
- Se conserva la navegación inferior compacta introducida en cambios48.
- El directorio se abre como destino interno y ofrece retorno a Más.
- Más agrupa accesos a servicios, Comunidad, avisos de mascotas perdidas/encontradas, Diario, Aprender y Ajustes.
- Salud permanece como destino principal inferior; no se duplica en Más.

### Comunidad y seguridad
- Los avisos de mascotas perdidas/encontradas reutilizan las categorías y herramientas de Comunidad existentes, evitando crear un sistema paralelo.
- Se recuerda que los avisos comunitarios no son un servicio oficial de emergencias y que conviene compartir zonas aproximadas, no domicilios.

### Salud y organización
- Se aprovechan el pasaporte, rutinas, recordatorios, sincronización por mascota y exportación PDF existentes.
- No se presenta Pawly como historia clínica oficial ni se generan pautas veterinarias.
- La sincronización y permisos actuales se mantienen sin alterar el esquema de Firestore.

## Validación

La validación automatizada se ejecuta mediante el workflow Android del repositorio sobre la rama de trabajo. La prueba final de interacción debe realizarse también en un teléfono/emulador, ya que CI no puede confirmar permisos de ubicación ni la apertura de aplicaciones externas.

- Añadidas pruebas unitarias para composición de consultas del directorio.
- La compilación, lint y pruebas de CI deben completarse en GitHub Actions.
- La validación visual y funcional final requiere instalar la build en un dispositivo Android y comprobar navegación, tema claro/oscuro y apertura de Maps/web.

## Pendiente para una fase de marketplace real

1. Incorporación de profesionales mediante registro y verificación documental.
2. Perfiles con especialidades, zona, tarifas y disponibilidad declaradas.
3. Contacto y solicitudes de cita con consentimiento.
4. Backend para reservas, cancelaciones, notificaciones y protección frente a spam.
5. Valoraciones únicamente de servicios realizados y con mecanismos de moderación.
6. Pagos solo cuando existan proveedores, términos, soporte y obligaciones legales resueltas.

## Nota de alcance

La búsqueda de profesionales está disponible desde el apartado Más y mantiene el mismo destino activo en la barra inferior mientras se navega por sus resultados externos.

Esta rama mejora el descubrimiento y la experiencia de Pawly, pero no afirma haber implementado una contratación transaccional. Los enlaces a mapas o buscadores son una puerta de acceso a proveedores externos, no una recomendación ni verificación de Pawly.
