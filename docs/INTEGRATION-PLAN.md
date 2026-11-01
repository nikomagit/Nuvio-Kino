# Plan de integración (antes de implementar)

Fecha: 2026-11-01. Proyecto: Nuvio-Kino.

## Bases verificadas

La API de releases de GitHub, no `/releases/latest` (que omite prereleases), confirma:

| Plataforma | Publicación | Commit |
|---|---|---|
| Desktop | 0.1.33-alpha, 2026-10-27 | 5f99fb4dba1ee43258977abacde7703a27e941fa |
| Android TV | 1.1.0-beta.9, 2026-10-31 | fa194b008e331780d2391491879e4a0a343641ef |
| Android móvil | 0.6.6-beta, 2026-10-31 | 2d52c8b71b598eb90e13fa9ec9ccff97ab601126 |

Desktop no tiene una beta publicada. Se usa su última alpha, sin cambiar de base silenciosamente.

## Arquitectura de Nuvio

Desktop y Mobile son proyectos Kotlin Multiplatform con Compose Multiplatform, Ktor, Kotlinx
Serialization y coroutines. Comparten muchas convenciones, pero son releases distintos y se
conservan como proyectos independientes. El código de scrapers está en `fullCommonMain`, el host
QuickJS Android en `androidFull` y el de Desktop en `desktopMain`. La navegación usa rutas de
Navigation3, una raíz de pestañas y destinos de ajustes. Desktop reproduce mediante libmpv y
puentes nativos; Android móvil mediante los reproductores existentes. Los addons Stremio son
servicios HTTP (`manifest.json`); los scrapers Nuvio exponen `getStreams(tmdbId, type, season, episode)`.

TV es una app Kotlin/JVM Android con Compose for TV, Hilt, Navigation Compose, OkHttp/Retrofit y
Media3. Su runtime de scrapers usa también QuickJS, pero su navegación, repositorios y reproductor
son propios. El sabor `full` habilita plugins; el de Play Store los excluye.

## Arquitectura de Kino

Se inventariaron los 39 repositorios públicos de `kinotvapp`. `kino-light` es explícitamente una
demo Apache-2.0: Plugins y En vivo son pantallas de muestra. `kino-tv` publica licencia GPL y una
oferta de código por correo, no el motor real. No se puede afirmar que se haya estudiado ni copiado
su implementación privada. Se utilizarán las pantallas públicas como referencia y el contrato
público para una implementación independiente.

Un plugin es `kino-plugin.json` más un módulo ES `plugin.js`, ejecutado en QuickJS. No es un
scraper Nuvio ni un addon Stremio. El host suministra `kino.fetch`, configuración, almacenamiento,
cookies, criptografía y otras funciones. Las capacidades indican qué exports invocar. Hay plugins
que no reproducen nada (subtítulos, tracking, pantallas, tiendas, sincronización o temas).

El SDK y guía públicos anuncian API 15, pero los manifiestos actuales de Social y temas requieren
16 y 17. Es necesario consultar cada manifiesto, no asumir que todos los repositorios son API 3.
Las firmas Ed25519 cubren `kino-signed-entry:v1\nrepo\nid\nversion\nsha256(entry)` y se conserva
la clave de autor durante actualizaciones. El catálogo oficial mezcla plugins Kino, addons Stremio
y entradas integradas; el identificador sugerido por la tienda puede diferir del id del manifiesto
(por ejemplo Internet Archive). El id del manifiesto es la autoridad de instalación.

TV admite canales `ref` resueltos al reproducir, streams inline y listas M3U con XMLTV. Las
categorías, paginación, búsqueda, favoritos y guía deben conservar procedencia por plugin. Un
canal se reproduce directamente, no tiene episodios ni progreso VOD. Cabeceras deben llegar a
manifiestos, segmentos y claves. Reconexión necesita volver a resolver URLs caducadas.

## Decisiones de implementación

1. Monorepo con las tres bases fijadas y un núcleo compartido por rutas de fuentes Gradle. Se
   conservan sus sistemas originales de addons/scrapers, navegación y reproducción.
2. Runtime Kino independiente: módulos ES auténticos, puente JSON asíncrono a funciones del host,
   aislamiento por llamada/plugin, límites de ejecución/memoria y persistencia por plugin.
3. Tienda en Configuración: catálogo oficial y comunidad, instalación desde GitHub/subcarpeta,
   manifiesto y código de un mismo commit, validación, firmas, actualización, desinstalación,
   configuración y detalle visible de capacidades. Errores de una entrada no vacían la tienda.
4. Sección TV: fuentes/categorías, búsqueda, paginación, favoritos y guía; soporte de canales
   inline/ref y M3U/XMLTV. UI táctil/ratón y foco visible, D-pad y Volver en TV.
5. Reutilizar reproductores Nuvio pasando URL, cabeceras y metadatos. No convertir canales en
   películas para insertarlos en la biblioteca o en Continuar viendo.
6. No anunciar compatibilidad completa solo por aceptar un manifiesto. Funciones del host aún
   ausentes deben dar errores explícitos y quedar registradas en la matriz de compatibilidad.

## Riesgos y validación prevista

- Una instalación descargada correctamente no demuestra que funcione un login, DRM, un servidor
  privado, Telegram o un servicio de pago. No se inventarán credenciales ni resultados.
- APIs 13–17 abarcan sockets, byte streams, catálogo, identidad, salas, IA, monedas y temas: su
  semántica no equivale a las integraciones de Nuvio. Se documentarán brechas por capacidad.
- Validar hosts y cada redirección, mantener TLS, limitar tamaños y no exponer el sistema de
  archivos a JavaScript. Los servidores locales solo se permiten por URL configurada por el usuario.
- Se necesitan JDK y Android SDK; al inicio no estaban instalados. Preparar herramientas locales,
  ejecutar compilaciones y pruebas reales donde las dependencias publicadas permitan resolverlas.
- Pruebas del contrato con plugins oficiales, servidor de referencia local para canales/auth,
  M3U/XMLTV, firmas alteradas, reinstalación/actualización y aislamiento de almacenamiento.
- La reproducción audiovisual y D-pad se verifican en app/dispositivo cuando haya entorno capaz;
  una respuesta HTTP o un manifiesto HLS no equivalen a reproducción observada.
- Publicación solicitada en `nikomagit/Nuvio-Kino`. El conector permite leer/escribir contenido
  pero no crear repositorios. Se preparó GitHub CLI; requiere una sesión autenticada antes del push.
