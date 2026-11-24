# Cierre y validación de Pawly para iOS

Esta lista separa los ajustes cerrados en el código de las comprobaciones que necesitan el proyecto Firebase real, las credenciales de Apple y un dispositivo iOS. Una compilación correcta no demuestra por sí sola que APNs, Google Sign-In o la sincronización remota estén configurados.

## Cambios de esta rama

- La pantalla Inicio utiliza una cabecera más compacta, con acceso directo a Comunidad y Más/Ajustes, selector de mascota cuando hay varios perfiles y acceso a edición del perfil propio.
- El registro FCM se conserva asociado a la cuenta actual y se elimina al cerrar sesión o desactivar las notificaciones desde Ajustes.
- Al volver a activar las notificaciones se vuelve a registrar el dispositivo y se actualiza el token FCM conocido.
- Se mantiene la separación entre ramas Android e iOS; no se han fusionado los cambios Kotlin de Android sobre el cliente SwiftUI.

## Preparación obligatoria de Apple y Firebase

- [ ] Registrar el identificador com.pawly.ios en el proyecto Firebase correcto.
- [ ] Añadir el GoogleService-Info.plist correspondiente a iOS en ios/Pawly/; no usar el archivo JSON de Android.
- [ ] Configurar GOOGLE_CLIENT_ID y GOOGLE_REVERSED_CLIENT_ID en los ajustes del target.
- [ ] Configurar los proveedores Email/Password y Google en Firebase Authentication y revisar los URI OAuth.
- [ ] Activar Push Notifications y Background Modes/Remote notifications en el target de Xcode.
- [ ] Subir la clave APNs .p8 correcta a Firebase Cloud Messaging y comprobar Team ID, Key ID y Bundle ID.
- [ ] Configurar en XcodeGen/Xcode el entorno APNs adecuado para Debug y Release y la firma de distribución.

## Pruebas en dispositivo

- [ ] Instalar desde cero, registrar una cuenta y comprobar la creación de usuario, nombre reservado y perfil Firestore.
- [ ] Verificar correo, cerrar y volver a iniciar sesión, y recuperar contraseña.
- [ ] Probar Google Sign-In con una cuenta nueva y otra ya existente.
- [ ] Conceder permiso de notificaciones y comprobar que el token aparece en users/{uid}/fcmTokens.
- [ ] Desactivar el interruptor de notificaciones, verificar que se elimina el token de ese dispositivo y que no vuelve a registrarse mientras siga desactivado.
- [ ] Cerrar sesión y comprobar que se elimina el token FCM de la cuenta anterior; iniciar sesión con otra cuenta en el mismo dispositivo.
- [ ] Volver a activar las notificaciones y confirmar que se vuelve a guardar un único token vigente para ese dispositivo.
- [ ] Probar notificaciones en primer plano, segundo plano y app terminada, así como el destino al tocar cada aviso.
- [ ] Probar dos cuentas y dos dispositivos para perfiles compartidos, permisos editor/lector, diario, mensajes y confirmaciones de lectura.
- [ ] Probar bloqueo en ambos sentidos, denuncia, retirada de contenido y limpieza de listeners al salir de Comunidad.
- [ ] Probar restauración de una copia en una cuenta de prueba y confirmar qué datos locales y remotos se recuperan.
- [ ] Probar permisos denegados, pérdida de conexión, sesión caducada, reinstalación y cambio de cuenta.
- [ ] Revisar VoiceOver, tamaños de texto, orientación y tamaños de iPhone/iPad.

## Límites que siguen requiriendo trabajo externo o validación

- La eliminación de cuenta iOS registra una solicitud; no borra por sí sola Firebase Auth, todas las subcolecciones ni los medios de Cloudinary.
- Las notificaciones remotas dependen de APNs y de desplegar/configurar el backend Cloudflare Worker con sus secretos de Firebase.
- El código de iOS y del Worker ya incorpora push específicas de mensajes nuevos, validando la conversación y sin incluir el contenido privado del mensaje. Solo se entregan cuando el Worker está desplegado, se han configurado sus secretos y APNs/FCM está habilitado.
- Los recordatorios del sistema son locales al dispositivo; los registros de agenda compartida no equivalen a sincronizar todos los detalles y disparadores de notificación.
- Cloudinary unsigned no ofrece privacidad de archivos ni cifrado de extremo a extremo; para producción conviene firma desde backend y límites de subida.
- El idioma preferido está guardado, pero no todo el contenido de la app está traducido.
- CI de GitHub ha completado correctamente la compilación de iOS para simulador y la validación TypeScript/Vitest del Worker. Las 21 pruebas de reglas Firestore también pasan en el emulador. APNs real, OAuth, Cloudinary, sincronización y push deben seguir probándose en un dispositivo con las credenciales del propietario.

## Criterio de salida

No considerar iOS listo para publicar hasta que la compilación del simulador esté verde, las pruebas anteriores se hayan completado en dispositivos reales y se hayan cerrado los bloqueantes legales, de privacidad, moderación, borrado de cuenta y operación del backend.
