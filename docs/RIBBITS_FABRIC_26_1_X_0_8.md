# Ribbits Fabric 26.1.x Port - 0.8

## English

### What This Build Is

This is the first working checkpoint for the unofficial Fabric 26.1.x port of Ribbits.
The goal of this version is to keep the original mod experience intact: Ribbit villages generate, Ribbits render and animate correctly, trading works, music packets are stable, and the mod no longer depends on an external YUNG's API build that is unavailable for this Minecraft target.

### Main Changes

#### 🧱 Fabric 26.1.x Build Port

The project was moved onto a Fabric 26.1.x-compatible Gradle/Loom setup and the Fabric module metadata was updated for the new target.
This was required because the original 1.21.1 build could not load on the 26.1.x runtime without updating loader-facing metadata, mappings, Gradle configuration, and dependency coordinates.

#### 🌿 Embedded YUNG's API Logic

Ribbits relies on YUNG's API for the village generation pipeline. Removing it entirely would make the core feature of the mod disappear, so the needed YUNG API systems were embedded into the port instead of replacing the feature with custom one-off logic.

This keeps the Ribbit village generation behavior close to the original implementation while avoiding a hard dependency on a YUNG's API release that does not exist for 26.1.x. The embedded code keeps the relevant structure, jigsaw, terrain adaptation, registration, mixin, service-loader, and data hooks available locally.

#### 🏡 Ribbit Village Generation

The worldgen data and processors were updated so Ribbit villages register and load in 26.1.x. The structure can be found with `/locate ribbits:ribbit_village`, which means Minecraft's worldgen pipeline can resolve the structure naturally for the current seed.

The implementation keeps the existing YUNG-style generation path instead of recreating village placement manually.

#### 🦎 GeckoLib 5 Client Update

Client rendering and animation code was updated for the GeckoLib 5 API used by 26.1.x. Models, animations, renderer classes, animation controllers, and data tickets were moved to the newer API style.

This preserves the original Ribbit animations and supporter hat rendering while matching the current GeckoLib package and runtime expectations.

#### 🎒 Item Models, Spawn Eggs, and Creative Tab

Item model definitions were updated for the newer Minecraft item model format. Ribbit spawn egg textures and item definitions were restored so the creative inventory entries render correctly.

The creative tab registration was also moved into the port's current registration layer so Ribbits items remain discoverable in-game.

#### 🎵 Music and Client Packet Stability

The music network handling was restored so client-side sound actions can wait for Ribbit entities to load. In the previous port state, packets could arrive before the entity was present on the client, causing errors like `StartMusicSingle: ribbit ... not found`.

The new handler queues entity-dependent actions until Fabric reports the entity as loaded, then plays or stops the relevant Ribbit music. Pending actions are cleared on disconnect.

#### 🤝 Vanilla-Friendly Trading Behavior

Ribbit merchants now stay still and look at the player while the trading UI is open.

This mirrors vanilla's merchant behavior: Minecraft uses a movement-blocking trade goal and a separate look-at-trading-player goal for entities such as the Wandering Trader. Ribbits cannot reuse those exact vanilla classes because they are typed to `AbstractVillager`, so the port adds small Ribbit-specific equivalents that follow the same goal pattern.

#### 🧹 Runtime Log Cleanup

Several noisy runtime warnings were addressed:

- Missing Ribbits refmap warnings were removed from mixin configs.
- A duplicate Beardifier mixin path was removed so YUNG's embedded version owns that hook.
- GeoIP failures are now treated as non-fatal warnings instead of hard errors.
- Music packets no longer log entity-not-found errors during normal chunk/entity loading.

Warnings from unrelated mods, such as Camerapture texture warnings or Iris/Sodium shader messages, are not caused by Ribbits.

### Validation

- ✅ `sh ./gradlew build` completes successfully.
- ✅ The latest Fabric jar was copied to the TESTFABRIC instance.
- ✅ The mod loads in-game.
- ✅ `/locate ribbits:ribbit_village` finds Ribbit villages.
- ✅ Ribbit merchant trading opens and now behaves closer to vanilla merchants.

---

## Español

### Qué Es Esta Build

Este es el primer punto estable de trabajo para el port no oficial de Ribbits a Fabric 26.1.x.
El objetivo de esta versión es mantener la experiencia original del mod: las aldeas Ribbit generan, los Ribbits renderizan y animan correctamente, el comercio funciona, los paquetes de música son estables, y el mod ya no depende de una build externa de YUNG's API que no existe para este objetivo de Minecraft.

### Cambios Principales

#### 🧱 Port de Build a Fabric 26.1.x

El proyecto fue actualizado a una configuración Gradle/Loom compatible con Fabric 26.1.x y se ajustó la metadata del módulo Fabric para el nuevo objetivo.
Esto era necesario porque la build original de 1.21.1 no podía cargar en runtime 26.1.x sin actualizar metadata del loader, mappings, configuración de Gradle y coordenadas de dependencias.

#### 🌿 Lógica de YUNG's API Integrada

Ribbits depende de YUNG's API para el flujo de generación de aldeas. Quitar esa API por completo habría eliminado la característica principal del mod, así que se integraron en el port los sistemas necesarios de YUNG's API en lugar de reemplazar la generación con lógica manual.

Esto mantiene la generación de aldeas Ribbit cerca de la implementación original y evita depender de una versión externa de YUNG's API que no existe para 26.1.x. El código integrado conserva los hooks relevantes de estructuras, jigsaw, adaptación de terreno, registro, mixins, service loaders y datos.

#### 🏡 Generación de Aldeas Ribbit

Los datos de worldgen y los processors fueron actualizados para que las aldeas Ribbit registren y carguen en 26.1.x. La estructura se puede encontrar con `/locate ribbits:ribbit_village`, lo que significa que el pipeline natural de worldgen de Minecraft puede resolver la estructura para la seed actual.

La implementación conserva el camino de generación estilo YUNG en lugar de recrear manualmente la colocación de aldeas.

#### 🦎 Actualización del Cliente a GeckoLib 5

El renderizado y las animaciones del cliente fueron actualizados para la API de GeckoLib 5 usada en 26.1.x. Modelos, animaciones, renderers, controllers de animación y data tickets fueron movidos al estilo nuevo de la API.

Esto preserva las animaciones originales de los Ribbits y el render del sombrero de supporter, pero usando los paquetes y expectativas actuales de GeckoLib.

#### 🎒 Modelos de Items, Huevos de Spawn y Creative Tab

Las definiciones de modelos de items fueron actualizadas para el formato nuevo de Minecraft. También se restauraron texturas y definiciones de los spawn eggs de Ribbits para que se vean correctamente en el inventario creativo.

La creative tab también fue movida a la capa de registro actual del port para que los items de Ribbits sigan siendo fáciles de encontrar dentro del juego.

#### 🎵 Estabilidad de Música y Paquetes de Cliente

Se restauró el manejo de red de la música para que las acciones de sonido del cliente puedan esperar a que las entidades Ribbit estén cargadas. En el estado anterior del port, los paquetes podían llegar antes de que la entidad existiera en el cliente, generando errores como `StartMusicSingle: ribbit ... not found`.

El handler nuevo deja acciones pendientes hasta que Fabric informa que la entidad ya cargó, y entonces reproduce o detiene la música correspondiente. Las acciones pendientes se limpian al desconectarse.

#### 🤝 Comportamiento de Comercio Amigable con Vanilla

Los Ribbits comerciantes ahora se quedan quietos y miran al jugador mientras la UI de comercio está abierta.

Esto replica el comportamiento de comerciantes vanilla: Minecraft usa un goal para bloquear movimiento durante el comercio y otro goal separado para mirar al jugador, como ocurre con el Wandering Trader. Ribbits no puede reutilizar directamente esas clases vanilla porque están tipadas a `AbstractVillager`, así que el port agrega equivalentes pequeños específicos para Ribbits siguiendo el mismo patrón de goals.

#### 🧹 Limpieza de Logs en Runtime

Se corrigieron varios avisos ruidosos de runtime:

- Se quitaron referencias a refmaps faltantes de los mixin configs de Ribbits.
- Se eliminó una ruta duplicada del mixin Beardifier para que la versión integrada de YUNG controle ese hook.
- Los fallos de GeoIP ahora son warnings no fatales en lugar de errores duros.
- Los paquetes de música ya no generan errores de entidad no encontrada durante la carga normal de chunks/entidades.

Los warnings de otros mods, como texturas de Camerapture o mensajes de shaders Iris/Sodium, no son causados por Ribbits.

### Validación

- ✅ `sh ./gradlew build` termina correctamente.
- ✅ El jar Fabric más reciente fue copiado a la instancia TESTFABRIC.
- ✅ El mod carga dentro del juego.
- ✅ `/locate ribbits:ribbit_village` encuentra aldeas Ribbit.
- ✅ El comercio del Ribbit comerciante abre y ahora se comporta más parecido a comerciantes vanilla.
