# Guía editorial para el artículo de Remote Compose

Este documento acompaña a [`remote-compose-article.md`](remote-compose-article.md). Sirve para
actualizar el artículo sin confundir una decisión de arquitectura con una capacidad ya verificada
en una versión alpha. No sustituye la documentación oficial ni modifica el artículo.

## Decisión que debe reflejar el artículo

La arquitectura admitida para este ejemplo separa datos de UI y creación del documento:

```text
Backend: estado del partido + firma del envelope
                         │
                         ▼
Android host: valida datos + compone @RemoteComposable con APIs públicas
                         │
                         ▼
                  RemoteComposePlayer
```

El backend es dueño de los datos del partido y de la firma del envelope. El host Android conserva
la conversión de esos datos a UI remota mediante la ruta pública de captura. El documento no es
código ejecutable, y el host sigue siendo dueño de navegación, identidad, permisos y efectos de
negocio.

No presentar `RemoteComposeWriter` ni los creadores JVM de los fixtures como la arquitectura
recomendada del producto. Forman parte del historial y de las pruebas de compatibilidad del
repositorio, no de la dirección soportada para el ejemplo.

## Consecuencia para la tesis del artículo

La decisión anterior tiene un efecto que el artículo debe decir con claridad. En este ejemplo el
backend firma **datos**, y la estructura visual de cada estado (Programado, En vivo, Finalizado)
la define el código `@RemoteComposable` de la aplicación Android. Por eso:

- Los datos del partido cambian sin publicar la app.
- **La estructura de la tarjeta no cambia sin publicar la app.** Añadir un bloque, quitar un
  elemento o reordenar la tarjeta exige una nueva versión del host.

La documentación oficial describe el mismo modelo: "Your app runs your `@RemoteComposable`
functions to record a document" y "Your Kotlin code runs only when your app creates the
document". Guarda silencio sobre la creación en un servidor, la entrega por red y la
compatibilidad entre quien graba y el player. Que la estructura viaje sin publicar la app es una
propuesta arquitectónica del artículo, no una capacidad documentada.

Afirmaciones del artículo que dejan de coincidir con el repositorio (buscar por el texto, no por
el número de línea):

| Afirmación del artículo | Qué es cierto en el repositorio |
| --- | --- |
| "La estructura cambia sin publicar la app" | Cambian los datos; la estructura vive en el compilador del host. |
| El backend ejecuta `captureSingleRemoteDocument` y publica un "documento .rc firmado" | El backend firma un envelope con datos de partido; Android captura el documento. |
| "Creación en JVM" como sección y el diagrama del backend | La captura con la API pública de Compose ocurre en Android. |
| Nota de versión y artefactos de `1.0.0-alpha19` | El repositorio fija `1.0.0-alpha22` y `minSdk` 29. |
| Ante un payload incompatible "se conserva la interacción básica" | El fallback local es un texto sin acciones. |
| "Ver jugada" o "seguir equipo" se mapean a navegación, autenticación o deep links | Los destinos del host son demostraciones; no hay login ni navegación real. |
| Descripciones semánticas para marcador, estado y acciones, y orden de foco predecible | Los nodos tienen buenas descripciones en el árbol, pero con TalkBack real el foco nunca entra en ellos: queda en la vista del player. El host ofrece las acciones como acciones de accesibilidad. No se probó el orden de foco por teclado. |
| El benchmark propone tamaño, memoria, CPU y una comparación con Compose local, SDUI y WebView | Solo se miden validación, captura y primer contenido visible; el resto no se midió. |

Lo que sigue en pie y conviene defender: el documento es una representación reproducible y no
código remoto, el host conserva la autoridad sobre acciones y efectos, y el player resuelve en
local el reloj, el pulso, el estado simbólico y la adaptación al tamaño sin tráfico de red. La
ventaja frente a un SDUI queda en la **expresividad y el comportamiento local**, no en la
independencia respecto de las versiones de la app. La sección "Cuándo tiene sentido" del propio
artículo ya reconoce que un catálogo SDUI pequeño puede resolver el problema con menos
complejidad; el ejemplo debe decir que se acerca a ese caso en cuanto a distribución.

Formulación sugerida para sustituir la propiedad central: "los datos cambian sin publicar la app;
la estructura la define el host, y lo que gana el documento es que el player resuelve animación,
estado y acciones localmente".

## Alternativa evaluada y no adoptada: crear los documentos fuera de la app

Se comprobó si la estructura podría cambiar sin publicar la app creando los documentos fuera de
ella. Es viable con API pública, pero con condiciones que el artículo no menciona. Evidencia de
un emulador Pixel 9 con API 35 y `1.0.0-alpha22`:

- **No es un backend JVM puro.** `remote-creation-compose` solo se publica como AAR de Android.
  Un módulo JVM de Gradle no puede depender de él (`library elements 'aar'… the consumer needed
  class files`). `remote-core`, `remote-creation-core` y `remote-creation-jvm` sí son JARs y exponen la capa de
  bajo nivel; no se verificó el resto de artefactos.
- **Existe un camino sin `Context`.** `captureSingleRemoteDocument` tiene un overload que no pide un
  `Context`. Se alcanza solo con API pública: la fábrica `RemoteCreationDisplayInfo(width, height,
  dpi, fontScale, …)` es pública, `PlatformImageProvider` es una interfaz pública y
  `createProfile()` también. El constructor de `RemoteCreationDisplayInfo` y
  `NoOpPlatformImageProvider` son `internal`.
- **Funciona en un test JVM, con matices.** Con `unitTests.isReturnDefaultValues = true` se crearon
  los estados Programado y Finalizado. El estado En vivo falló porque la librería convierte
  `toRemoteString(DecimalFormat)` a `android.icu.text.DecimalFormatSymbols`, que en un stub
  devuelve `null`. Con Robolectric (Android real) se creó. Robolectric descarga el runtime de
  Android al ejecutarse, así que el CI necesita red o artefactos en caché.
- **Los documentos se reproducen igual.** Los tres, creados sin `Context`, se cargaron en el
  player sin cambios: mismos textos, reloj avanzando, pulso animado y las acciones llegaron al
  despachador del host.
- **El reloj hereda el desfase entre relojes.** El documento ancla el reloj al instante de
  creación. Creado en un equipo y reproducido en otro, mostró 66:56 donde debía marcar al menos
  67:12, porque ambos relojes no coincidían. Habría que anclarlo a una marca de tiempo del
  servidor y tolerar el desfase; no se resolvió.
- **No se probó** un servicio real, el peso de Robolectric en CI, otras densidades o tamaños de
  creación, ni que la app pudiera volver a `minSdk` 26. Los manifiestos indican que los
  artefactos del player (`remote-player-view`, `remote-player-core`) requieren 26 y que solo
  `remote-creation-compose` requiere 29, pero eso es una lectura de manifiestos, no una prueba.

Se descartó por ser un uso no documentado que depende de un runtime de pruebas y de una librería
alpha. Si el objetivo editorial fuera demostrar que la estructura viaja sin publicar la app, esta
es la vía a retomar, y el artículo debe presentarla como una propuesta que exige esas condiciones.

## Qué conservar

Antes de editar, preservar las ideas que siguen siendo válidas:

- Remote Compose distribuye un documento reproducible; no una jerarquía de componentes de negocio
  ni lógica arbitraria.
- El documento remoto es entrada no confiable. La validación de tamaño, esquema, expiración,
  compatibilidad, hash y firma ocurre antes de cargarlo en el player.
- Una acción es una solicitud declarativa. El host aplica una allowlist y decide toda operación
  sensible.
- La caché del último documento válido y un fallback local son responsabilidades del host.
- Estado simbólico, tiempo y animación que el player pueda resolver localmente no requieren una
  actualización de red por frame.
- La accesibilidad, el tamaño del host, las pruebas de dispositivo y las mediciones siguen siendo
  requisitos del experimento, no promesas automáticas de la biblioteca.

## Cambios editoriales requeridos

### 1. Ajustar la creación fuera del host

Revisar los pasajes que afirman o sugieren que un backend JVM captura directamente un documento
Remote Compose. Explicar en cambio que el backend firma y entrega datos de partido dentro de un
contrato verificable, y que Android transforma los datos aceptados en una función
`@RemoteComposable` con APIs públicas.

El diagrama de arquitectura debe mostrar la frontera con claridad:

| Lado | Responsabilidad editorialmente correcta |
| --- | --- |
| Backend | Estado del partido, revisión, expiración, hash y firma del envelope. |
| Transporte | Entrega los datos firmados; HTTPS no reemplaza la verificación del artefacto. |
| Android host | Verifica el envelope, convierte los datos aceptados en UI remota y aloja el player. |
| Player | Reproduce el documento y resuelve únicamente las capacidades locales que exponga. |
| Dispatcher del host | Autoriza IDs de acción y ejecuta efectos conocidos. |

Evitar frases como “el servidor ejecuta Compose” o “el backend produce directamente los bytes del
documento” para describir esta arquitectura. La formulación preferida es: “el backend produce
**datos firmados**; el host Android crea el documento Remote Compose después de validarlos”.

### 2. Distinguir el ejemplo del flujo oficial de widgets

Mantener la aclaración de que los ejemplos oficiales se orientan a superficies con un player
compatible. Añadir que este repositorio usa el player dentro de un host Android controlado para
probar una arquitectura de producto; no debe describirse como una integración oficial de widget
ni como una capacidad garantizada para cualquier host.

Cuando el artículo mencione una API, separar tres niveles:

1. **API pública documentada:** puede describirse como la base admitida de la implementación.
2. **Decisión del ejemplo:** envelope firmado, allowlist, caché y fallback pertenecen a la
   aplicación, no a Remote Compose.
3. **Hipótesis que exige evidencia:** compatibilidad de host, semántica, movimiento reducido,
   rendimiento o adaptación deben presentarse como elementos a comprobar en la versión concreta.

### 3. Corregir el pipeline y sus etiquetas

Actualizar los diagramas para que el tramo de creación diga “datos firmados” antes de Android y
“captura pública de `@RemoteComposable`” dentro de Android. El flujo de revisión recomendado es:

1. El backend construye el estado del partido y el envelope firmado.
2. Android limita y verifica el envelope antes de usar sus datos.
3. Android mapea datos validados a la UI `@RemoteComposable`.
4. La captura pública produce el documento para `RemoteComposePlayer`.
5. El host registra y autoriza las acciones después de cargar el documento.
6. Ante rechazo o falta de datos, el host mantiene el último documento válido o muestra su
   fallback local seguro.

No convertir los datos remotos en callbacks arbitrarios ni atribuir al player decisiones de
navegación o autenticación.

### 4. Tratar versión y APIs alpha con precisión

El artículo actual se basa en `1.0.0-alpha19`, mientras que el repositorio fija todos los
artefactos Remote Compose en `1.0.0-alpha22` para usar las APIs públicas necesarias para captura,
configuración, tiempo y animación. La edición del artículo debe reflejar alpha22 sólo junto con la
comprobación de catálogo, fixtures y pruebas de host de esa misma versión.

Al hacer la edición final del artículo:

- sustituir la nota de versión por la versión que esté realmente fijada y verificada en el
  repositorio;
- comprobar los nombres, firmas y disponibilidad de las APIs contra la documentación de esa misma
  versión;
- conservar el aviso de que es una alpha y que las APIs pueden cambiar;
- no mezclar versiones de `remote-creation-*` y `remote-player-*` en texto, diagramas o ejemplos;
- describir las capacidades de tiempo, configuración, animación y movimiento reducido sólo con el
  alcance que las pruebas del host hayan demostrado.

La versión es un dato de compatibilidad, no decoración editorial. Si la prueba de compatibilidad
sigue en curso, la redacción honesta es “el repositorio fija alpha22; la evidencia de fixtures y
host para esa versión debe comprobarse antes de publicar”.

### 5. Revisar ejemplos de código y afirmaciones técnicas

Los fragmentos de código son ilustrativos cuando representan una política de aplicación. Deben
marcarse como pseudocódigo si no compilan contra la versión fijada. En particular:

- El dispatcher por `actionId` representa una política de autorización del host, no una API que
  convierta una acción remota en lambda Kotlin.
- Las transiciones de marcador proceden de datos nuevos aceptados; un reloj o pulso local sólo
  avanza si la capacidad del player y la versión usada lo permiten.
- Un layout adaptativo se resuelve con información del host durante la reproducción; no debe
  derivarse de píxeles o `CompositionLocal` capturados en el productor.
- No prometer menor latencia, CPU, batería o memoria sin una medición comparable y reproducible.

## Lista de edición antes de publicar

- [ ] El artículo sigue presentando Remote Compose como documento reproducible, no como código
      remoto general.
- [ ] Backend, transporte, host y player tienen responsabilidades distintas y coherentes.
- [ ] Ningún diagrama afirma que el backend JVM usa una ruta restringida para crear la UI.
- [ ] La creación Android mediante `@RemoteComposable` y captura pública aparece en el pipeline.
- [ ] Firma, límites, validación, allowlist, caché y fallback se atribuyen a la aplicación.
- [ ] Las acciones sensibles permanecen bajo autorización del host.
- [ ] Las capacidades alpha se condicionan a la versión y evidencia verificadas.
- [ ] La propiedad "la estructura cambia sin publicar la app" se reformula: cambian los datos, y la
      estructura la define el host.
- [ ] El benchmark prometido coincide con lo que realmente se midió.
- [ ] El fallback y los destinos de acción se describen como demostraciones, no como producto.
- [ ] La accesibilidad se describe con lo comprobado con TalkBack real, no con lo que muestra el árbol de accesibilidad.
- [ ] Los ejemplos y nombres de API se revisan contra la documentación de la versión fijada.
- [ ] Los enlaces oficiales y los diagramas siguen describiendo el alcance de la afirmación que
      los acompaña.
- [ ] La nota de versión coincide con el catálogo de dependencias y las pruebas de compatibilidad.

## Límite de este documento

Esta guía indica qué debe comprobar o cambiar una edición posterior; no es una corrección implícita
del artículo. Cualquier revisión del artículo debe conservar sus argumentos sobre seguridad,
medición, accesibilidad y límites de Remote Compose, y debe validarse de nuevo contra el código y
la versión de dependencias entonces vigentes.
