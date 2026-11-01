# Nuvio-Kino

Fork experimental de Nuvio para Desktop, Android TV y Android móvil, con una tienda de complementos
Kino y una sección de TV. Conserva los clientes y reproductores de Nuvio y añade un núcleo Kotlin
compartido. **La compatibilidad con Kino es parcial**: instalar y cargar un plugin no implica que
estén disponibles todas las integraciones de su servicio.

## Bases elegidas

Verificadas el **1 de noviembre de 2026** contra las publicaciones oficiales:

| Cliente | Base | Publicación |
|---|---|---|
| Desktop | **0.1.33-alpha** | [27 oct 2026](https://github.com/NuvioMedia/NuvioDesktop/releases/tag/0.1.33-alpha) |
| Android TV | **1.1.0-beta.9** | [31 oct 2026](https://github.com/NuvioMedia/NuvioTV/releases/tag/1.1.0-beta.9) |
| Android móvil | **0.6.6-beta** | [31 oct 2026](https://github.com/NuvioMedia/NuvioMobile/releases/tag/0.6.6-beta) |

Desktop no publica una beta: se usa su última alpha. Los commits exactos están en
[upstreams.lock.json](upstreams.lock.json). Cada base se mantiene en `platforms/` como proyecto
Gradle independiente; no hay que ejecutar los scripts de integración para compilar.

## Qué cambia

- Tienda de addons Kino en Configuración; búsqueda, categorías, información del manifiesto,
  instalación, actualización, configuración y desinstalación.
- Catálogo oficial y comunitario de Kino, inventario de los plugins del autor y addons Stremio.
  Los Stremio se instalan a través del repositorio original de addons de Nuvio.
- Runtime QuickJS con módulos ES reales; configuración y almacenamiento por plugin, firmas
  Ed25519 y clave de autor conservada al actualizar, límites de recursos y comprobación de hosts.
- TV con categorías por fuente, búsqueda local o en todas las fuentes, favoritos, paginación y
  programación. Canales inline, por `resolve(ref)` y M3U; guías de plugins y XMLTV, incluido gzip.
- Transporte local de reproducción que conserva cabeceras y valida los destinos y redirecciones.
  Reescribe listas HLS —variantes, segmentos, claves y rendiciones— y referencias DASH. Admite
  Range en archivos progresivos y la función `sign` para cabeceras por solicitud.
- Navegación con botones enfocables y foco visible para D-pad, además de ratón/táctil. La pantalla
  TV usa una columna de categorías, otra de canales y, con espacio suficiente, programación.
- Identificadores Android independientes para que móvil y TV se instalen juntos y junto a Nuvio.
  Desktop usa su propia carpeta de datos. Los sistemas Stremio y scrapers originales siguen presentes.
- El actualizador de Nuvio se desactiva en las distribuciones del fork hasta disponer de un canal
  de releases propio, para conservar la instalación de Nuvio-Kino.

## Compilar

Necesitas **JDK 21**, Node 18+ para los scripts, Git LFS y Android SDK. Después de clonar ejecuta
`git lfs pull` para descargar las bibliotecas nativas heredadas de Nuvio. Las versiones Kotlin, Compose,
Gradle y AGP son las de las bases fijadas; no se rebajan para ocultar problemas de compilación.
Android móvil usa plataforma **37.1**; TV usa su configuración fijada (plataforma 36, Build Tools
y NDK cuando lo solicita Gradle). Acepta las licencias del SDK con las herramientas oficiales.

En cada proyecto crea un `local.properties` local, incluso si no usas servicios de cuenta:

```properties
sdk.dir=C:/ruta/al/Android/Sdk
```

La configuración pública de cuentas Nuvio se incluye en `config/official-accounts.properties`,
obtenida de [su documento de descubrimiento](https://api.nuvio.tv/.well-known/nuvio).
Contiene únicamente la URL y la clave de cliente anónimo publicada por ese servidor. Puede
actualizarse con `node scripts/configure-accounts.mjs`; el script rechaza claves administrativas
y valida el acceso a los ajustes públicos de autenticación. `local.properties` y las variables
de entorno permiten sustituir estos valores. El acceso con cuenta y con enlace siguen dependiendo
del servicio Nuvio y de sus condiciones de acceso.

Las claves propias de TMDB, Trakt y otros servicios son opcionales para compilar, pero las funciones
que dependen de ellos necesitan configuración. No se incluyen cuentas, claves de firma de releases
ni credenciales administrativas del proyecto original. Consulta también los README de cada base.

Antes de compilar Desktop descarga el único asset upstream que supera el límite de archivo de GitHub:

```sh
node scripts/bootstrap-assets.mjs
```

El script fija commit, tamaño y SHA-256; el resto de fuentes y assets están en este repositorio.
Los submódulos con URL publicada se conservan en `.gitmodules`. Los punteros heredados que no
tenían URL se registran en `docs/upstream-orphaned-gitlinks.json`; los builds habituales usan los
precompilados de la base y no necesitan esos directorios para compilar.

**Desktop:**

```sh
cd platforms/desktop
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:run
# Instalador del sistema anfitrión (requiere las herramientas nativas de la base):
./gradlew :composeApp:packageDistributionForCurrentOS
```

En Windows usa `gradlew.bat`. El reproductor Desktop conserva los puentes nativos de Nuvio:
Windows necesita su toolchain C++/CMake para reconstruirlos; macOS y Linux necesitan las
dependencias nativas descritas en la base. Compilar Kotlin no equivale a generar un instalador.

**Android TV:**

```sh
cd platforms/android-tv
./gradlew :app:assembleFullDebug
```

APK en `app/build/outputs/apk/full/debug/` (la base puede separar por arquitectura). Los builds debug
usan la clave debug estándar. Para publicar releases configura una clave propia; nunca la de Nuvio.

**Android móvil:**

```sh
cd platforms/android-mobile
./gradlew :androidApp:assembleFullDebug
```

APK en `androidApp/build/outputs/apk/full/debug/`. Usa la distribución **full** para plugins; los
clientes de tienda mantienen las restricciones upstream y no incluyen el runtime Kino.

## Cómo funciona la tienda

Abre Configuración → Tienda de addons Kino (en TV: Configuración → Integraciones). También puedes
entrar a TV desde su destino de navegación o desde Configuración.

La tienda consulta `kinotvapp/kino-plugin-store` (`catalog.json` e `index.json`) y el inventario
público del autor. Conserva una copia local y una copia incluida para poder mostrar el catálogo
sin conexión. Un fallo de una fuente no elimina las demás. Las tiendas `pluginStore` instaladas
también pueden aportar entradas; ninguna instala otros plugins por su cuenta.

Para instalar pulsa **Instalar**, revisa versión, servidores, capacidades y limitaciones, y confirma
esa versión. Se descarga manifiesto y entry desde **el mismo commit**, se verifica la firma si
existe y se comprueban sus exports en QuickJS. El id real es el del manifiesto, aunque el catálogo
use otro. La configuración y los datos se guardan en un espacio propio de ese plugin.

Puedes escribir `autor/repositorio` o `autor/repositorio/carpeta` en la búsqueda para instalar una
fuente por dirección. Una actualización conserva la configuración y exige la misma clave si el
plugin ya estaba firmado. La versión anterior queda activa hasta guardar el marcador de la nueva.
Desinstalar borra los datos locales de ese complemento. Los servicios privados requieren que
escribas sus URL y credenciales en su configuración.

## Compatibilidad y límites

Consulta la [matriz de compatibilidad](docs/COMPATIBILITY.md), el
[análisis previo](docs/INTEGRATION-PLAN.md) y la [revisión de repositorios](docs/REPOSITORY-REVIEW.md).

El motor admite instalar manifiestos de API 1–17 y ejecutar sus exports, pero todavía faltan
puentes de sincronización Kino, sockets/byteSource, resolvedores IPC, salas, identidad, IA,
tareas en segundo plano y otras integraciones. Las pantallas declarativas tienen un renderer
básico; los temas no sustituyen el sistema de temas de Nuvio. La interfaz muestra estas limitaciones.

**Kino Social exige tokens y atestaciones emitidos por Kino**. Su instalación puede validarse, pero
su servidor no puede usarse desde un cliente independiente sin una vía oficial de autenticación.
No se suplantan firmas ni se omiten esas comprobaciones. Servicios externos caídos o que requieren
cuenta no se vuelven funcionales por instalar su manifiesto.

La app completa de Kino no está en `kino-light`: ese repositorio es una demo. `kino-tv` ofrece
el código por solicitud. La TV de este fork es una implementación del contrato público,
inspirada en la UI pública, y no una réplica verificada de su motor privado.

## Verificación

Desde la raíz:

```sh
./gradlew test
```

Para incluir los plugins y el servidor oficiales en las pruebas:

```sh
node scripts/fetch-references.mjs
# PowerShell:
$env:KINO_RESEARCH_DIR=(Resolve-Path .research).Path
./gradlew.bat test
# bash:
KINO_RESEARCH_DIR="$PWD/.research" ./gradlew test
```

Los resultados comprobados y su alcance están en [verification](verification/README.md). Se
distinguen instalación, ejecución del módulo, transporte de medios y reproducción audiovisual.
El workbench de interfaz (`./gradlew run`) usa las pantallas compartidas para QA; no sustituye un
cliente Nuvio ni implementa la instalación Stremio o el reproductor final.

## Licencia y atribución

Nuvio-Kino se distribuye bajo **GNU GPL v3**, conservando las licencias y atribuciones de las tres
bases Nuvio. No es una publicación oficial de Nuvio ni de Kino. Los recursos de SDK/tienda Kino
incluidos mantienen sus avisos Apache-2.0 en `shared/resources/kino/`. Los plugins descargados
conservan sus autores y licencias. No se redistribuyen el logo ni los assets de marca de Kino.
