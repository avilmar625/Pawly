# Cambios18 — rediseño visual de Pawly

## Objetivo

Dar un salto visual perceptible sin eliminar ni sustituir las funciones existentes de cambios17. Esta rama se centra en la pantalla de Inicio y en la navegación principal, manteniendo el resto de módulos y los datos locales.

## Cambios incluidos

### 1. Inicio rediseñado
- Nueva cabecera con fecha localizada en español.
- Tarjeta principal de mascota más protagonista, con foto circular grande, jerarquía tipográfica y acción clara para editar el perfil.
- Resumen de rutinas y recuerdos en tarjetas compactas.
- Indicador visual de progreso diario.
- Rutinas con etiquetas, iconografía, estados y superficies más cuidadas.
- Bloque destacado para descubrir lugares y acceso secundario a Aprender.
- Se conservan los callbacks y el estado que ya utilizaba Inicio: no se cambia el almacenamiento ni las claves de preferencias.

### 2. Navegación inferior
- Sustituida la barra estándar por una barra flotante de seis destinos.
- Estado seleccionado más visible, con icono resaltado y etiqueta.
- Se mantienen los seis destinos y sus acciones: Inicio, Explorar, Aprender, Salud, Diario y Comunidad.

### 3. Compatibilidad y CI
- Se añade cambios18 al workflow de GitHub Actions.
- El workflow instala el SDK Android 37, alineado con compileSdk/targetSdk del proyecto.
- CI ejecuta tests, Lint, APK debug y AAB release sin firmar.

## Cómo probar

1. Abre la rama cambios18 en Android Studio.
2. Sincroniza Gradle con JDK 17 y SDK Android 37 instalados.
3. Ejecuta ./gradlew testDebugUnitTest lintDebug assembleDebug.
4. Instala el APK y comprueba Inicio, el cambio de pestañas, la edición de perfil y los checks de las rutinas.
5. Comprueba también que Salud, Explorar, Aprender, Diario y Comunidad siguen abriendo correctamente.

## Alcance y honestidad

- Esta versión es un primer rediseño importante de la experiencia de Inicio y navegación; no es todavía un rediseño integral de cada pantalla.
- No se han cambiado los modelos de datos, Firebase, Cloudinary ni la lógica de las funciones existentes.
- El progreso diario refleja las dos rutinas que ya estaban disponibles en Inicio; no debe interpretarse como un calendario completo de cuidados.
- Las pantallas interiores conservan en gran medida su diseño de cambios17 y serán el siguiente bloque de trabajo.
- No se afirma que compile hasta que termine la ejecución de GitHub Actions y se pruebe en un dispositivo.

## Próxima fase visual recomendada

1. Unificar el lenguaje visual de Explorar, Salud, Diario, Aprender y Comunidad.
2. Mejorar el contenido visual de perfiles de mascota y comunidad con fotografías, estados vacíos y jerarquía consistente.
3. Incorporar microinteracciones y transiciones discretas.
4. Revisar accesibilidad: tamaños táctiles, contraste, escalado de texto y descripciones de iconos.
