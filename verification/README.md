# Evidencia de verificación

- `official-modules.json`: versiones, API, exports y resultado de carga/instalación de 29 plugins
  oficiales, con límites de host indicados por plugin. Las firmas se comprueban sobre bytes Git
  originales, evitando la conversión CRLF de Windows.
- `reference-server.json`: prueba con el plugin y servidor oficiales Tu servidor: autenticación,
  Home, categorías, canales, programación, resolución y transporte HLS/segmentos MPEG-TS.
- `android-engine.json`: instalación y ejecución de un módulo ES en un APK de prueba con el
  mismo núcleo y el mismo AAR QuickJS de los clientes Android. Se detectó y corrigió la carga
  de módulos en la versión 1.0.5 de Android; no se sustituyó el runtime original de scrapers.
- `builds.json`: compilación Kotlin de Desktop y generación de las APK full debug de Android TV
  y móvil, todas con código de salida 0. No se generó un instalador Desktop ni releases firmadas.
- `android-ui.json`: prueba manual en emulador Android 36 del cliente TV completo. Instalación
  de Tu servidor desde la tienda, configuración por formulario, categorías y programación,
  reproducción HLS visible en Media3 y regreso al listado. Las capturas `tv-channels.png` y
  `tv-playback.png` muestran ese recorrido. El emulador no tiene salida de audio habilitada.
  En móvil se comprobó el inicio como invitado, creación de perfil local y acceso a TV.
- `device-install.json`: la APK móvil se instaló correctamente y se abrió en un Samsung S21 Ultra
  físico (arm64). Las pruebas funcionales en ese teléfono quedan a cargo del usuario; la instalación
  por sí sola no certifica reproducción ni compatibilidad de sus servicios.
- `account-server.json`: configuración pública de Nuvio validada y creación de una sesión por
  enlace comprobada contra el servidor. La primera APK tenía esos valores vacíos y dirigía ambos
  métodos de acceso a localhost. No se han usado las credenciales del usuario en estas pruebas.
- Las pruebas de `shared/test` cubren aislamiento, persistencia, configuración de servidores,
  rechazo de redirecciones a otros hosts, M3U, XMLTV/XXE y reescritura HLS/DASH.
- La prueba con `KINO_MPV_LIBRARY` decodificó video HLS real con la libmpv fijada de Desktop
  (320 píxeles de ancho y posición de reproducción avanzada). También pasó la descarga de lista y
  segmento HLS con firma HMAC por solicitud. Se ejecutó sin salida visible de audio/video.

Las pruebas de referencias son opt-in: sin `KINO_RESEARCH_DIR` se omiten explícitamente, no se
cuentan como aprobadas. Las fuentes fijadas están en `upstreams.lock.json`.

Un segmento descargado ni una decodificación sin salida demuestran salida audiovisual en una pantalla. La prueba visual de TV
sí mostró video del servidor de referencia; no certifica audio audible. Estos reportes no
certifican servicios de producción, todas las capacidades de API 17 ni compatibilidad idéntica
con el motor privado de Kino.
