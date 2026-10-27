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
- [ ] Los ejemplos y nombres de API se revisan contra la documentación de la versión fijada.
- [ ] Los enlaces oficiales y los diagramas siguen describiendo el alcance de la afirmación que
      los acompaña.
- [ ] La nota de versión coincide con el catálogo de dependencias y las pruebas de compatibilidad.

## Límite de este documento

Esta guía indica qué debe comprobar o cambiar una edición posterior; no es una corrección implícita
del artículo. Cualquier revisión del artículo debe conservar sus argumentos sobre seguridad,
medición, accesibilidad y límites de Remote Compose, y debe validarse de nuevo contra el código y
la versión de dependencias entonces vigentes.
