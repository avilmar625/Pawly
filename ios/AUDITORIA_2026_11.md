# Pawly iOS · auditoría y validación

Rama de entrega `codex/ios-auditoria-integral-2026-11`. La base iOS es `bcc39e10`. La referencia funcional Android revisada es `b324a4c0` de `codex/streamlined-navigation-2026-11`. La comparación incluye el cliente SwiftUI, los contratos Kotlin, las reglas Firestore y los servicios que usa cada flujo. [Cambios de la rama](https://github.com/avilmar625/Pawly/compare/bcc39e10483f0ad3405043caf7e7e2f76fb10e0e...codex/ios-auditoria-integral-2026-11)

**Decisión de publicación.** La rama sirve para pruebas internas en simulador y en un entorno de pruebas compartido con Android. Para distribución pública quedan la eliminación integral de cuenta, la decisión sobre acceso equivalente a Google, la validación de privacidad de los archivos y las pruebas de extremo a extremo con servicios configurados.

## Requisitos del apartado 3

«Implementado» describe el cambio en la rama. La última columna especifica hasta dónde llega la evidencia. «Parcial» identifica un requisito que conserva trabajo funcional o de verificación relevante.

| Nº | Estado | Resultado y alcance de la prueba |
|---|---|---|
| 3.1 Idiomas | Parcial | Los siete idiomas tienen catálogos equivalentes, nombres nativos y selección inmediata mediante el entorno SwiftUI. Las claves de pantalla se traducen por separado de los valores de Firestore. Se comprueban las claves de SwiftUI que extrae Xcode, los formatos y las opciones literales de menús. Se localizaron las categorías del muro, motivos de denuncia, especies, rutinas, profesionales y estados del pasaporte y de propuestas. Quedan mensajes construidos en tiempo de ejecución por servicios, revisión de documentos exportados y revisión lingüística por hablantes. |
| 3.2 Navegación | Parcial | Cabeceras compactas y acciones contextualizadas en Mi perro, Diario, Agenda y Más. Se reducen introducciones permanentes. Quedan comprobaciones visuales en pantalla pequeña, texto de accesibilidad e iPad. |
| 3.3 Selector global | Implementado | Un mismo selector con avatar, nombre y menú controla Hoy, Salud, Diario y Agenda. Conserva alta, edición y eliminación de mascota propia. La campana y Ajustes abren sus destinos directamente. |
| 3.4 Hoy | Implementado | Se retiraron las tres entradas redundantes. Próximamente muestra el detalle del registro pulsado y tiene en cuenta la siguiente ocurrencia de un aviso. El historial de cuidados sigue accesible desde Salud. Falta recorrido visual de cada variedad de registro. |
| 3.5 Paseos y cuidados | Implementado | El acceso Paseo fija el tipo de formulario. Admite distancia, descripción, notas, paseador, fotos y enlace HTTPS al mapa. El historial conserva el alta genérica. Las fotos de galería y cámara se pueden previsualizar y quitar por separado. La ruta se introduce por enlace. |
| 3.6 Peso | Implementado | El último peso válido inicializa el formulario. Hay entrada manual, deslizador, ajustes de 100 g, fecha y notas. Salud y Hoy usan el mismo lector de mediciones. Pruebas con decimales de coma y punto, unidades, datos históricos y valores inválidos. El gráfico enseña las seis mediciones recientes. |
| 3.7 Multimedia | Parcial crítico | La petición unsigned envía la carpeta y deja que Cloudinary asigne el identificador. Se comprueban tipo, ruta y propietario antes de asociar el recurso. Hay miniaturas, controles de retirada, permisos de captura y reutilización de una subida confirmada durante reintentos. Muro y chat conservan una foto o un vídeo por publicación según el contrato Android. Diario y cuidados admiten varias fotos. Faltan un dispositivo físico, presets de pruebas y una comprobación integral de cada destino. |
| 3.8 Salud | Parcial | Unificado el selector, retirado el salto redundante a Diario y reorganizadas las acciones. El PDF reutiliza el conjunto cargado cuando está completo, se genera en segundo plano, muestra progreso y utiliza un fichero exclusivo. Pendientes tiempos y revisión visual del PDF con un historial extenso. |
| 3.9 Diario | Parcial | Editor plegable, controles de categoría y galería independientes, miniaturas y eliminación individual. Conserva filtros y ordenación. Falta el recorrido de fotos reales en dos dispositivos. |
| 3.10 Agenda | Parcial | Calendario mensual directo con leyenda, varios tipos por día, salto a fecha, detalle y CRUD de avisos. La caché mantiene los avisos de cada mascota separados. El aviso se borra por su propio documento. Título, fecha y categoría se comparten. Notas, repetición, finalización y permiso de notificación tienen alcance local en el contrato actual. La planificación contempla avisos recurrentes antiguos e inicios diferidos. Faltan entrega física y edición simultánea entre plataformas. |
| 3.11 Comunidad | Parcial | Publicar está visible, hay filtros y ordenación, y los adjuntos tienen previsualización. Se redujo la duplicación de búsqueda y accesos. Pendiente revisar moderación y recorrido social con cuentas de prueba de ambos sistemas. |
| 3.12 Perfiles y relaciones | Parcial | Perfil público reutilizado desde muro, chat, búsqueda y asistentes. Listas paginadas de seguidores y seguidos, seguir, escribir, bloquear y denunciar. El selector de contactos permite elegir un número. El listado de asistentes sigue sujeto a permisos del Worker. Falta validación real con dos identidades y varias combinaciones de privacidad. |
| 3.13 Ubicación y mapa | Parcial | Servicio común Core Location con reintento acotado, cancelación y tratamiento de ubicación transitoriamente desconocida. Mapa general con capas de resultados cercanos, favoritos propios, eventos y lugares comunitarios aprobados. Lecturas acotadas al abrir el mapa. Pendientes precisión reducida, movimiento real y calidad de búsqueda según la zona. |
| 3.14 Explorar | Parcial | Ideas, Cerca, Guardados y Colecciones accesibles. Las mutaciones de iOS evitan reemplazar entradas de otras colecciones. Los favoritos de mapa tienen un documento privado propio. Se conservan datos heredados y se comprueban permisos en emulador. El guardado integral de colecciones de la versión Android de referencia aún puede sobrescribir una edición simultánea de una colección compartida. |
| 3.15 Eventos | Parcial | Búsqueda, filtros, ordenación, confirmación visible, perfil del asistente y foto opcional con previsualización. Las reglas admiten eventos anteriores y vinculan la foto a un recurso indexado por el organizador. Pendientes despliegue autorizado de reglas y recorrido con foto real y asistente. |
| 3.16 Aprender | Parcial | Guías plegables, búsqueda, filtros y estados de noticias. Las cinco guías y su búsqueda consultan el idioma activo. Las noticias usan ediciones por idioma con edición española identificada para catalán y euskera. Pendiente validar disponibilidad y pertinencia de cada edición externa. |
| 3.17 Mascotas perdidas | Parcial | El cartel tiene salida JPEG visible y compartible. La zona se invalida al cambiar la dirección, se geocodifica y se redondea. La retirada del consentimiento puede guardarse sin posición actual. Faltan guardado/recuperación y entrega de avisos por radio con el Worker y dos dispositivos. |
| 3.18 Compartir mascotas | Parcial | Selector desplegable inicializado con la mascota activa. La edición depende de la pertenencia y del rol. Pruebas de reglas con cuentas diferenciadas. Falta completar invitación, aceptación y revocación entre Android e iOS de pruebas. |
| 3.19 Más y cuenta | Implementado | Reorganizados Ajustes, privacidad, perfil, compartir, copias y cierre de sesión. Los accesos repetidos de Agenda, comunidad y mensajes se concentran en los destinos principales. |
| 3.20 Copias | Parcial | JSON v2 validado con límite de 25 MB e identidad de cuenta. Al restaurar se conservan datos actuales y se insertan los documentos ausentes. Se combinan favoritos y avisos por identificador. Los archivos multimedia son referencias externas. Los registros inmutables de actividad se exportan para consulta. Quedan pruebas con históricos grandes y compatibilidad explícita con otros formatos. |
| 3.21 Login | Parcial | El ojo usa el mismo valor de contraseña en acceso y registro. XCTest cubre el formulario real. El alta interrumpida puede retomarse acreditando las credenciales. La configuración local de Google se comprueba en el producto compilado. Faltan Google OAuth real, entrega del correo de recuperación y renovación de sesión en dispositivo. |
| 3.22 Otros módulos | Parcial | Los gastos conservan autor al editar y el chat muestra los últimos 200 mensajes y permite ampliar el historial desde la conversación. La prueba de integración confirma que un mensaje entrante sigue llegando con la ventana llena y que se recuperan los anteriores en orden. Se revisaron profesionales, Market, avisos y sus permisos. Quedan coste de consultas de contadores con tráfico real, accesibilidad y tema en todas las pantallas, apertura de enlaces externos y entrega física de recordatorios locales. APNs está pospuesto para esta edición gratuita. |

## Arquitectura, contratos y archivos

- **Navegación y estado.** `PawlyPetHeader`, `PawlyPetStore`, `PawlyCalendarView` y `PawlyHomeCareSection` comparten la selección. `PawlyPublicProfile` reúne acciones sociales y respeta el acceso concedido por Firebase y el Worker.
- **Agenda.** `PawlyAgendaCache` separa fusión y cálculo de fechas del listener y del planificador de `PawlyReminderStore`. Una instantánea local vacía no desencadena una migración destructiva. La planificación local usa el límite de peticiones de iOS y se renueva al volver al primer plano.
- **Medios.** `PawlyUploadContract` valida solicitudes y respuestas. `PawlyCloudinaryUploader` procesa y sube. `PawlyUploadCache` comparte trabajo en curso y recibos recientes. `PawlyAttachmentPicker` y `PawlyCameraButton` gestionan selección, captura y miniaturas. El recibo en memoria protege reintentos dentro del mismo proceso. Una interrupción entre la subida y el índice puede dejar un recurso huérfano.
- **Ubicación y colecciones.** `PawlyLocationProvider` controla permisos, plazo y reintentos. El mapa general consulta solo eventos publicados, lugares aprobados y los datos privados del usuario activo. `PawlySavedPlacesStore` lee el formato histórico y crea el nuevo documento privado en una transacción. Una lista privada vacía expresa una eliminación intencional.
- **Copias.** `PawlyBackupPolicy` valida estructura, identificadores, tamaño y versión. `PawlyBackupManager` aplica las reglas de restauración. Quedan excluidos credenciales, tokens, mensajería privada de terceros y archivos multimedia binarios. Una restauración interrumpida puede repetirse y conserva los documentos existentes.
- **Infraestructura.** Los cambios de reglas corresponden a fotos opcionales de eventos y favoritos privados. Los contratos de referencia siguen en Kotlin. Los cambios de CI y la configuración `firebase.ios-tests.json` apuntan a datos sintéticos en loopback. El modo del emulador queda condicionado a Debug y simulador.
- **Mantenibilidad.** Se extrajeron componentes de los archivos grandes a medida que cambiaba cada flujo. `PawlyFeatureViews.swift`, `PawlyParityViews.swift` y algunas consultas sociales siguen concentrando responsabilidades. La próxima extracción debe preservar el comportamiento con pruebas de formularios, consultas y permisos.

## Evidencia de validación

| Nivel | Resultado comprobado | Alcance |
|---|---|---|
| Compilación | XcodeGen genera el proyecto. Xcode 27.2 compila Debug y Release para simulador arm64. | Se validan el plist procesado, HTTPS completo, identificadores Google, parámetros Cloudinary y siete idiomas. |
| Firma y push | El target para iPhone conserva su identificador y omite el entitlement APNs en Debug y Release. | Binario Debug para iPhone firmado con el Personal Team seleccionado en el Mac. Se comprobó el identificador y la ausencia de APNs en la firma del ejecutable. Firebase Messaging no inicia el registro remoto; los avisos locales mantienen su permiso y presentación. Pendiente entrega física de un recordatorio. |
| Swift | Cinco ejecutables de prueba superados. | Petición/respuesta multimedia, reintentos concurrentes, aislamiento de agenda y recurrencias, copias/versionado y lectura de pesos. |
| Idiomas | 658 claves equivalentes por idioma. Cubiertas las 498 claves extraídas por Xcode 27.2, 392 literales directos y 68 opciones de listas. | El compilador permite comprobar también interpolaciones y etiquetas de accesibilidad. Las opciones persistidas mantienen el valor que espera Android. Los permisos del sistema tienen recursos en los siete idiomas. |
| XCTest, iPhone 17e | Cinco pruebas superadas con Auth y Firestore locales y cuentas ficticias. | Ojo y valor de contraseña en acceso/registro; inicio de sesión, persistencia tras reabrir la aplicación, cambio de mascota en Agenda, cambio a inglés y conservación del calendario; exportación/restauración repetida con colección eliminada, perfil más reciente y favoritos privados; preparación de foto y muestra de vídeo con respuesta HTTP controlada e índices Firestore de propietario; historial del chat y recepción de mensajes con la ventana llena. |
| Firestore | 23 pruebas de reglas superadas en el emulador. | Incluyen documentos antiguos, foto de evento vinculada al organizador, roles y aislamiento de favoritos. |
| Cloudflare Worker | TypeScript sin errores y 50 pruebas en nueve archivos superadas. | Contratos de backend con datos de prueba. |
| GitHub Actions | El checkpoint público `dc0f2e38` pasó ambos flujos. El estado del último commit se puede consultar por rama. | [Compilación y Worker](https://github.com/avilmar625/Pawly/actions/runs/45693056331) · [Reglas](https://github.com/avilmar625/Pawly/actions/runs/45693056239) · [Ejecuciones de la rama](https://github.com/avilmar625/Pawly/actions?query=branch%3Acodex%2Fios-auditoria-integral-2026-11) |
| Dispositivo y servicios | Pendientes los recorridos físicos y en staging de la matriz manual. | Siguen abiertos la entrega física de un aviso local, la captura, la disponibilidad del archivo remoto, el flujo OAuth externo y la sincronización Android–iOS con dos dispositivos. |

## Historial de implementación

| Commit | Cambio principal |
|---|---|
| `d05c9f8b` | Contrato unsigned de imágenes y vídeo e índice de propiedad. |
| `dbda3808` | Cabecera de mascota, navegación de Mi perro y acciones de Hoy. |
| `ff068a4e` | Agenda por mascota y selector de adjuntos en muro y chat. |
| `d4bf37af` | Ubicación, consentimiento de avisos y creación de eventos. |
| `d768ebca` | Catálogos iniciales en siete idiomas. |
| `21f850f1` | Restauración transaccional e idempotente de registros. |
| `a95cc066` | Perfil público reutilizable, Diario y exportación sanitaria. |
| `b3657ac9` | Calendario mensual y reintentos de subida. |
| `c51fe19b` | Foto opcional de eventos con reglas de propiedad. |
| `41d0c0bc` | Favoritos privados y creación concurrente de colecciones. |
| `f802b38b` | Fechas de inicio de avisos y lector de pesos común. |
| `2437818a` | Formulario de acceso probado en simulador y recuperación de altas interrumpidas. |
| `dc0f2e38` | Mapa por capas y miniaturas de Diario y cuidados. |
| `ad4b08d7` | Catálogos, permisos, guías, selección por cuenta, búsquedas cancelables, autoría de gastos y mensajes recientes. |
| `9a78853f` | Pruebas integradas de navegación y copias con los emuladores locales. |
| `b5191079` | Entitlement de APNs y comprobaciones de Debug/Release. |
| `e83eaa57` | Gestión de errores al recargar noticias. |

## Icono de instalación

El target compila el catálogo `Pawly/Assets.xcassets/AppIcon.appiconset` con `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon`. Se reutilizó la silueta del vector oficial `ic_pawly.xml` de la rama Android. La variante principal conserva la huella crema sobre verde bosque. La oscura tiene el fondo transparente que compone iOS, y la tintada usa escala de grises. Las tres son PNG cuadrados de 1024 × 1024 sin máscara de esquinas incorporada. [Especificación de Apple](https://developer.apple.com/documentation/xcode/configuring-your-app-icon)

El plist instalado identifica AppIcon en iPhone y iPad. El catálogo compilado contiene las tres representaciones. En la pantalla de inicio del iPhone 17e simulado se comprobó visualmente el icono de la aplicación. La comprobación física pendiente incluye Biblioteca de apps, Spotlight y la personalización de apariencia del dispositivo.

## Actualizar y ejecutar en el Mac

Desde tu copia habitual de Pawly, estas instrucciones crean un directorio hermano independiente. Puedes mantener tus cambios actuales en su rama y abrir esta auditoría en Xcode. El destino debe ser nuevo. Los dos archivos locales de configuración se copian con protección contra sobrescritura.

```bash
cd ~/Documents/Pawly
git fetch origin codex/ios-auditoria-integral-2026-11
git worktree add --detach ../Pawly-iOS-auditoria origin/codex/ios-auditoria-integral-2026-11
cp -n ios/Config.local.xcconfig ../Pawly-iOS-auditoria/ios/Config.local.xcconfig
cp -n ios/Pawly/GoogleService-Info.plist ../Pawly-iOS-auditoria/ios/Pawly/GoogleService-Info.plist
cd ../Pawly-iOS-auditoria/ios
xcodegen generate
open Pawly.xcodeproj
```

Selecciona el esquema **Pawly**, elige un iPhone simulador y pulsa **Run**. En un iPhone físico selecciona **Automatically manage signing** y tu **Personal Team**. Conserva el identificador existente. Para verificar la app procesada puedes ejecutar lo siguiente desde `ios`.

```bash
xcodebuild -project Pawly.xcodeproj -scheme Pawly -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/PawlyAuditBuild CODE_SIGNING_ALLOWED=NO build
python3 Tests/check-built-config.py /tmp/PawlyAuditBuild/Build/Products/Debug-iphonesimulator/Pawly.app
bash Tests/run.sh
```

Para ejecutar XCTest con cuentas efímeras, utiliza el esquema de la rama, Firebase CLI, Java y un runtime de iPhone instalado. Cierra otros emuladores que estén ocupando los puertos 8080 o 9099. Desde la raíz de la copia de pruebas:

```bash
export PAWLY_SIMULATOR_ID="$(python3 ios/Tests/simulator-id.py)"
firebase emulators:exec --config firebase.ios-tests.json --only auth,firestore --project demo-pawly 'xcodebuild -project ios/Pawly.xcodeproj -scheme Pawly -destination "platform=iOS Simulator,id=$PAWLY_SIMULATOR_ID" -parallel-testing-enabled NO test'
npm --prefix tests/firestore ci
firebase emulators:exec --only firestore --project demo-pawly 'npm --prefix tests/firestore test'
npm --prefix backend/cloudflare-worker ci
npm --prefix backend/cloudflare-worker run check
npm --prefix backend/cloudflare-worker test
```

## Configuración externa pendiente

1. En Firebase Console comprueba que la aplicación iOS corresponda al bundle `com.alexvilarrubla.pawly`, y que Google y correo/contraseña estén habilitados en el proyecto de pruebas. Coloca el plist de ese entorno en `ios/Pawly/`. Comprueba el client ID y su esquema inverso. Los sufijos de entornos de pruebas deben coincidir en todos los servicios. [Configuración iOS del repositorio](../docs/IOS_MAC_FIREBASE_CLOUDFLARE.md)
2. En `Config.local.xcconfig` introduce los valores que ya correspondan a tu proyecto. Para conservar `https://` en xcconfig usa la forma `https:/$()/host`. El script de verificación lee el plist procesado sin imprimir las credenciales. Configura los presets unsigned separados para imagen y vídeo, sus formatos y límites, y la convención de carpetas que usa Android. Conserva la restricción de identificadores públicos elegidos por el cliente. [Configuración del proyecto](../docs/FIREBASE_SETUP.md) · [Referencia de subidas de Cloudinary](https://cloudinary.com/documentation/image_upload_api_reference)
3. Valida las reglas de esta rama en un proyecto de pruebas antes de decidir su despliegue. La versión desplegada puede rechazar las fotos de evento o el nuevo documento de favoritos. Verifica con una cuenta propietaria, una editora y una lectora. Las URLs de entrega de medios unsigned pueden estar accesibles para quien tenga el enlace.
4. Revisa URL, rutas y secretos del Worker en el entorno de pruebas. Verifica autenticación de solicitudes, publicación de propuestas, perfiles autorizados de asistentes y notificaciones. Conserva los secretos en el servidor. Los límites por consulta de mapas, eventos y chats están acotados. Quedan por medir subpeticiones, lecturas de relaciones por elemento y consumo de medios con tráfico representativo. [Configuración del Worker](../docs/CLOUDFLARE_SETUP.md)
5. En el iPhone concede el permiso de notificaciones al crear un aviso local. Programa un aviso a unos minutos, deja la app en segundo plano y comprueba su entrega. La instalación con Personal Team utiliza la firma gratuita; APNs queda pospuesto. [Capacidades por tipo de cuenta](https://developer.apple.com/help/account/reference/supported-capabilities-ios/)

## Pruebas manuales priorizadas y salida a producción

| Prioridad | Ensayo pendiente | Criterio observable |
|---|---|---|
| P0 | Cámara, micrófono y galería en un iPhone de pruebas. Probar imagen, vídeo y pérdida de red tras la subida. | Miniatura correcta, un recurso indexado por propietario, URL recuperable al reabrir y un error accionable al fallar. |
| P0 | Dos cuentas de prueba, una mascota compartida y Android de referencia. Editar o borrar un aviso, un gasto, una foto y una colección desde dispositivos alternos. | Coinciden los documentos, se conserva la autoría, el lector carece de edición y el resto de registros permanece íntegro. |
| P0 | Pérdida de mascota con consentimiento, sin consentimiento, denegación de GPS y posición aproximada. | Cartel compartible, zona redondeada, radio respetado, retirada efectiva y notificación solo al destinatario que la autorizó. |
| P0 | Google, recuperación de contraseña, cuenta eliminada y contenido denunciado en un entorno autorizado. | Acceso y revocación verificables, tiempos y alcance de borrado documentados, moderación operativa. |
| P1 | Copia con registros antiguos y nuevos, interrupción, segunda restauración y pérdida del acceso a un archivo externo. | Perfil y documentos vigentes se conservan; los medios accesibles por enlace se distinguen del contenido recuperable. |
| P1 | Alternar idioma y apariencia con un borrador abierto. Recorrer cada pantalla con VoiceOver, Dynamic Type grande e iPhone pequeño. | El foco, el texto escrito y la mascota se conservan. Los controles siguen cabiendo y tienen nombre accesible. |
| P1 | Medir PDF extenso y desplazamiento en muro, chat, mapas y calendario con Instruments. | Anotar latencia, memoria máxima, lecturas y cancelación antes de fijar presupuestos de rendimiento. |

Para App Review quedan por resolver el mecanismo efectivo de eliminación de cuenta y datos asociados, la equivalencia de las alternativas de autenticación cuando procede la regla 4.8, y la operación real de moderación para contenido generado por usuarios. Verificar también metadatos, enlaces de privacidad y condiciones definitivos, declaraciones de privacidad de dependencias, firma y archivo para dispositivo. Las directrices relevantes incluyen 1.2, 4.8 y 5.1.1(v). [Directrices de Apple](https://developer.apple.com/app-store/review/guidelines/) · [Operación de borrado de cuenta](../docs/ACCOUNT_DELETION_OPERATIONS.md)
