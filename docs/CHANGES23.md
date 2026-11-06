# cambios23 · Perfiles compartidos

## Incluido
- Nueva sección Salud → Compartir.
- Creación de un perfil compartible ligado a la cuenta propietaria y al identificador estable de mascota.
- Invitaciones privadas por nombre de usuario, con prevención de auto-invitación, duplicados pendientes y usuarios que ya son miembros.
- Aceptación y rechazo transaccional de invitaciones.
- Listado de personas con acceso y posibilidad de que el propietario retire el acceso.
- Listado de perfiles compartidos aceptados e invitaciones recibidas.
- Reglas Firestore específicas para perfiles, miembros e invitaciones.
- Índices compuestos para las consultas de invitaciones.
- Workflow Android actualizado para ejecutar CI en cambios23.

## Límites conocidos
- Se comparte el acceso al perfil básico, no los datos sanitarios ni las rutinas. El pasaporte y los cuidados siguen bajo `users/{uid}/petCare/{petId}`.
- Los perfiles compartidos aceptados se muestran como referencia, pero todavía no se integran como mascota activa en Inicio/Salud.
- Las invitaciones no generan notificaciones push; requieren backend de confianza.
- No se ha desplegado Firestore desde esta rama ni se ha ejecutado una compilación local.
- Requiere cuenta con correo verificado.

## Pruebas manuales
1. Desplegar reglas e índices de Firestore.
2. Iniciar sesión con dos cuentas Pawly distintas y verificar ambos correos.
3. Desde la cuenta propietaria, abrir Salud → Compartir e invitar al nombre de usuario de la segunda cuenta.
4. En la segunda cuenta, abrir Salud → Compartir, aceptar la invitación y comprobar que aparece en «Perfiles compartidos contigo».
5. Volver a la cuenta propietaria, comprobar el miembro y retirar su acceso.
6. Confirmar que la segunda cuenta ya no puede leer el perfil compartido.

## Siguiente paso
Migrar pasaporte, cuidados y agenda a un repositorio compartido con roles owner/editor/reader, operaciones transaccionales, sincronización en tiempo real y migración no destructiva desde los documentos privados actuales.
