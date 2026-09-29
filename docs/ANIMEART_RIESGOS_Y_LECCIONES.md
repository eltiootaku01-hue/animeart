# AnimeArt — Riesgos y lecciones preventivas

**Repositorio:** `eltiootaku01-hue/animeart`  
**Branch investigada:** `main`  
**HEAD de referencia:** `d8b17344328cf4d80c3c721ca48253bebcaa65c0`  
**Fecha de investigación:** 2026-09-29  
**Alcance:** investigación preventiva. No se implementan funcionalidades del editor ni se modifican Gradle, configuración Android o dependencias.

> Este documento distingue entre hechos documentados por fuentes técnicas, recomendaciones derivadas de esos hechos y aspectos que deberán verificarse experimentalmente en AnimeArt. Las categorías de riesgo no son puntuaciones: son una clasificación cualitativa para ordenar la atención de ingeniería.

---

## 1. Resumen ejecutivo

Los riesgos que más condicionarán AnimeArt son:

1. **Memoria de Bitmap:** un bitmap decodificado puede ocupar mucha más memoria que el archivo comprimido que lo originó. Android documenta que los bitmaps pueden ser uno de los mayores consumidores de memoria y que una imagen de 4 bytes por píxel en ARGB_8888 crece directamente con ancho × alto. [DOCUMENTADO]
2. **Carga de imágenes grandes sin inspección previa:** Android recomienda leer dimensiones antes de asignar el bitmap y utilizar submuestreo cuando la resolución de trabajo lo permita. [DOCUMENTADO]
3. **Copias durante transformaciones:** rotar, recortar, escalar, generar máscaras o sombras puede crear buffers intermedios. El diseño debe evitar cadenas de copias completas innecesarias. [RECOMENDACIÓN TÉCNICA]
4. **Undo/redo mediante snapshots completos:** guardar una copia completa de cada estado puede multiplicar el consumo de memoria. Para AnimeArt conviene estudiar historial de operaciones/estado ligero y reservar snapshots grandes para casos controlados. [RECOMENDACIÓN TÉCNICA]
5. **Trabajo pesado en el hilo principal:** Android relaciona el trabajo prolongado del main thread con jank, falta de respuesta y ANR. La documentación cita un objetivo de aproximadamente 16 ms por frame para 60 FPS y un timeout de entrada de 5 s en dispositivos AOSP/Pixel, con variaciones por OEM. [DOCUMENTADO]
6. **Redraw y capas de composición:** Canvas acelerado por hardware utiliza GPU y también puede incrementar RAM. Las capas fuera de pantalla y ciertos efectos pueden introducir buffers y cambios de destino costosos. [DOCUMENTADO]
7. **Transformaciones destructivas repetidas:** modificar físicamente el bitmap fuente en cada gesto puede acumular pérdida de calidad y generar trabajo/copia innecesarios. Es preferible separar datos originales de transformaciones de presentación. [RECOMENDACIÓN TÉCNICA]
8. **Orientación y EXIF:** una fotografía puede almacenar su orientación en metadatos. Si AnimeArt ignora esa información, puede aparecer rotada aunque los píxeles estén correctamente codificados. [DOCUMENTADO]
9. **SAF y persistencia de acceso:** Android proporciona Storage Access Framework para que el usuario seleccione archivos y permite conservar permisos URI cuando el proveedor los ofrece. Esto reduce la necesidad de permisos de almacenamiento amplios. [DOCUMENTADO]
10. **Exportación no atómica / falta de espacio:** un fallo durante una exportación puede dejar un resultado incompleto o hacer perder trabajo si se reemplaza directamente el archivo anterior. La exportación debe tratarse como operación con resultado verificable. [RECOMENDACIÓN TÉCNICA]
11. **Background removal por color:** un umbral de blanco no puede distinguir de forma perfecta entre fondo blanco y regiones blancas pertenecientes al sujeto. JPEG añade variaciones de color y halos. La función debe considerarse una herramienta especializada, no un recorte universal. [RECOMENDACIÓN TÉCNICA]
12. **Sombras y blur:** sombras basadas en alpha pueden requerir máscaras, buffers intermedios y blur. Android documenta limitaciones de ciertas sombras Canvas y dispone de RenderEffect desde API 31, pero AnimeArt tiene minSdk 24, por lo que no puede depender de RenderEffect como única vía. [DOCUMENTADO + DECISIÓN FUTURA]
13. **GIF:** GIF usa una paleta de 256 colores o menos y múltiples frames con tiempos y métodos de disposición. El formato impone limitaciones visuales y el encoder no debe asumir que guardar todos los frames completos simultáneamente es barato. [DOCUMENTADO]
14. **Preview frente a exportación:** mantener una representación de trabajo razonable para la pantalla y conservar los datos necesarios para exportación evita exigir resolución máxima durante cada gesto. [RECOMENDACIÓN TÉCNICA]
15. **Lifecycle/process death:** ViewModel sobrevive cambios de configuración, pero no garantiza conservar datos tras una muerte del proceso iniciada por el sistema. El proyecto editable debe poder reconstruirse desde datos persistentes o un estado guardado ligero. [DOCUMENTADO]
16. **Compatibilidad de dispositivos:** Android debe funcionar en teléfonos, tablets, foldables y diferentes tamaños/ventanas; la documentación de calidad adaptable exige comprobar cambios de configuración y tamaños. [DOCUMENTADO]
17. **Dependencias innecesarias:** cada librería añade superficie de mantenimiento, compatibilidad, compilación y potenciales riesgos. En AnimeArt se debe justificar cada dependencia por necesidad concreta. [RECOMENDACIÓN TÉCNICA]
18. **Pruebas insuficientes:** una app de edición puede funcionar con imágenes pequeñas y fallar con una fotografía grande, muchas capas, undo repetido o exportaciones largas. Las pruebas de memoria, rendimiento y recuperación deben ser parte de las fases, no una comprobación final improvisada.

---

## 2. Matriz de riesgos

### Criterio cualitativo

- **Probabilidad Alta:** escenario frecuente o muy fácil de provocar en un editor de imágenes.
- **Probabilidad Media:** escenario razonablemente esperable, pero dependiente del flujo o dispositivo.
- **Probabilidad Baja:** escenario menos frecuente o dependiente de condiciones específicas.
- **Impacto Crítico:** puede producir pérdida de trabajo, crash/OOM, ANR o impedir una función principal.
- **Impacto Alto:** degrada seriamente la experiencia o limita una función importante.
- **Impacto Medio:** problema visible pero acotado y recuperable.
- **Impacto Bajo:** efecto menor sin pérdida importante de trabajo.

Estas categorías son **ingeniería cualitativa**, no una puntuación matemática.

| Riesgo | Probabilidad | Impacto | Fase afectada | Prevención | Cómo probarlo |
|---|---|---|---|---|---|
| OOM por Bitmap grandes | Alta | Crítico | 1, 2, 5–8, 9 | Inspeccionar dimensiones, limitar resolución de trabajo, controlar buffers | Abrir imágenes progresivamente mayores y observar memoria/crash |
| Copias completas durante transformaciones | Alta | Alto | 1, 2, 6, 7 | Transformaciones no destructivas y buffers temporales limitados | Arrastrar/escalar/rotar durante tiempo prolongado |
| Historial de snapshots grandes | Alta | Crítico | 1, 2, 6–8 | Historial basado en operaciones/estado ligero; snapshots limitados | 100+ acciones sobre imágenes grandes y medir memoria |
| Redraw excesivo | Alta | Alto | 1–9 | Invalidar sólo cuando cambie el estado visible; separar modelo/render | Gestos rápidos con muchas capas |
| Trabajo pesado en UI thread | Alta | Crítico | 1–9 | Procesamiento e I/O fuera del main thread | Importación/exportación/procesamiento durante interacción |
| Buffers off-screen excesivos | Media | Alto | 5, 7 | Usar capas y efectos sólo donde aporten valor | Muchas sombras/transparencias/efectos simultáneos |
| Error de orientación EXIF | Media | Alto | 1–2 | Leer orientación y normalizar representación de forma controlada | Fotos tomadas en orientaciones distintas |
| Exportación incompleta | Media | Crítico | 1, 2, 8, 9 | Escribir temporalmente, cerrar/validar y reemplazar sólo al completar | Interrumpir exportación y simular poco espacio |
| Pérdida de acceso a URI | Media | Alto | 1, 2, 9 | Diseñar persistencia de permisos cuando proceda | Reiniciar dispositivo/app y reabrir proyecto |
| Background removal elimina blancos internos | Alta | Alto | 6 | Umbral configurable + región conectada + revisión manual | Personajes con ojos/ropa blancos y fondos blancos |
| Halos por eliminación de fondo | Alta | Medio/Alto | 6 | Suavizado y corrección de borde; evitar promesas de perfección | JPEG con compresión y bordes claros |
| Sombra costosa | Media | Medio/Alto | 7 | Trabajar con alpha/máscara y resolución adecuada; evitar duplicados | Muchas capas con sombra y blur |
| GIF consume demasiada RAM | Alta | Alto | 8 | Procesamiento incremental y límites de resolución/frames | GIF con muchos frames y alta resolución |
| GIF pierde color/calidad | Alta | Medio | 8 | Advertir limitaciones del formato y probar material fotográfico | Comparar PNG/frames contra GIF final |
| Gestos multitouch inestables | Media | Alto | 1–2, 5 | Máquina de estados clara para punteros y coordenadas | Alternar 1/2/3 dedos rápidamente |
| Estado perdido por recreation | Media | Alto | Todas | Separar estado de proyecto de estado de Activity | Rotación, resize, multi-window |
| Estado perdido por process death | Media | Crítico | Todas | Persistir proyecto/estado recuperable | Forzar cierre del proceso y restaurar |
| UI incorrecta en pantallas pequeñas/grandes | Media | Medio/Alto | 1–9 | Layout adaptable y pruebas por tamaños | Teléfono pequeño, grande, tablet, ventana dividida |
| Dependencia innecesaria | Media | Medio | Todas | Revisar necesidad, tamaño, licencia, compatibilidad y mantenimiento | Auditoría de dependencias antes de cada incorporación |
| Fuga de referencias | Media | Alto | Todas | Evitar referencias de larga vida a Activity/View/Bitmap | Rotaciones repetidas + perfil de memoria |

---

## 3. Memoria

### 3.1 Lo que está documentado

Android señala que los Bitmap suelen ser uno de los mayores componentes de la memoria de una aplicación. En ARGB_8888, la referencia actual de Android documenta 4 bytes por píxel. Por tanto, una imagen de 4000×3000 píxeles representa aproximadamente 48 MB de datos de píxeles antes de contar objetos, buffers adicionales, GPU, UI y otras estructuras. [DOCUMENTADO]  
Fuente: Android Developers — Bitmaps and memory  
https://developer.android.com/topic/performance/memory/guide/bitmaps

La documentación de carga eficiente recomienda consultar primero las dimensiones con `inJustDecodeBounds` y decidir después cuánto decodificar. `inSampleSize` permite reducir el número de píxeles cargados. [DOCUMENTADO]  
Fuentes:  
https://developer.android.com/topic/performance/graphics/load-bitmap  
https://developer.android.com/reference/android/graphics/BitmapFactory.Options

Android también explica que liberar referencias permite que el recolector recupere memoria; `Bitmap.recycle()` existe para liberar inmediatamente el almacenamiento de píxeles de un bitmap que con certeza ya no se necesita, pero usarlo sobre un bitmap todavía referenciado puede ser inseguro. [DOCUMENTADO]  
Fuente: https://developer.android.com/reference/android/graphics/Bitmap

### 3.2 Implicaciones para AnimeArt

AnimeArt no debe tratar el tamaño del archivo JPEG/PNG como medida suficiente de memoria. El dato crítico durante edición es el tamaño decodificado.

**Reglas derivadas:**

- Inspeccionar dimensiones antes de cargar.
- No cargar resolución máxima automáticamente sólo porque el archivo la tenga.
- Diferenciar imagen fuente, representación de edición y preview cuando sea necesario.
- Evitar duplicar un bitmap completo para cada estado.
- Liberar referencias a buffers temporales tan pronto como sea seguro.
- No conservar cachés sin límite.
- Medir memoria en escenarios de varias capas.

La caché puede mejorar fluidez, pero Android advierte que una caché de memoria consume memoria valiosa; LruCache es un patrón adecuado para un límite explícito. [DOCUMENTADO]  
Fuente: https://developer.android.com/topic/performance/graphics/cache-bitmap

**DECISIÓN REQUERIDA POR IA-CHAN:** antes de FASE 1 deberá definirse el límite de resolución de trabajo y la política exacta de memoria para proyectos con varias imágenes grandes. No conviene inventar un límite universal sin medir en dispositivos objetivo.

---

## 4. Rendimiento

Android explica que el sistema intenta procesar actualizaciones de pantalla aproximadamente cada 16 ms para 60 FPS. Si el main thread acumula tareas demasiado largas o numerosas, aparecen hitching, lag y falta de respuesta. [DOCUMENTADO]  
Fuente: https://developer.android.com/topic/performance/threads

Para ANR de entrada, Android documenta un timeout por defecto de 5 segundos en AOSP/Pixel, indicando que puede variar según OEM. El trabajo bloqueante en el main thread es una causa principal. [DOCUMENTADO]  
Fuente: https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs

### Riesgos concretos

- Decodificar una fotografía grande mientras se procesa un gesto.
- Crear una copia completa en cada `ACTION_MOVE`.
- Recalcular sombras en cada movimiento.
- Recomponer todas las capas cuando sólo cambia una.
- Ejecutar exportación mientras el mismo hilo atiende interacción.
- Crear cientos de objetos temporales por segundo durante multitouch.

**RECOMENDACIÓN TÉCNICA:** los eventos táctiles deben actualizar un estado ligero y la vista debe invalidarse de manera controlada. El procesamiento de píxeles debe separarse del camino crítico del gesto.

---

## 5. Canvas y rendering

La aceleración de hardware está habilitada de forma predeterminada en los escenarios modernos y utiliza GPU para las operaciones Canvas, pero Android indica que también aumenta el uso de RAM y que no todas las operaciones tienen idéntico soporte o comportamiento. [DOCUMENTADO]  
Fuente: https://developer.android.com/topic/performance/hardware-accel

Las capas fuera de pantalla pueden ser útiles, pero tienen coste. La documentación de Canvas/graphics señala que `saveLayer` puede implicar buffers fuera de pantalla; la documentación de Compose describe los cambios de render target como relativamente costosos por el trabajo de copia y churn de memoria. [DOCUMENTADO]  
Fuente: https://developer.android.com/reference/android/graphics/Canvas  
https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/Canvas

**Regla:** no convertir cada capa en un buffer rasterizado permanente sólo para facilitar efectos.

Para blur, Android ofrece `RenderEffect` desde API 31, pero AnimeArt tiene actualmente minSdk 24. Por ello, cualquier estrategia basada exclusivamente en RenderEffect sería incompatible con parte del rango objetivo. [DOCUMENTADO]  
Fuente: https://developer.android.com/reference/android/graphics/RenderEffect

---

## 6. Capas

El sistema de capas debe ser principalmente un **modelo de datos**, no una colección de bitmaps ya fusionados.

Cada capa debería conceptualmente tener:

- identidad estable;
- tipo;
- visibilidad;
- bloqueo;
- orden Z;
- transformaciones;
- opacidad;
- referencia a contenido;
- metadatos necesarios para reconstruirla.

### Errores a evitar

- Usar la posición visual de la UI como única fuente de verdad.
- Confundir índice visual con identidad de capa.
- Fusionar capas sólo para facilitar una operación reversible.
- Duplicar el bitmap cada vez que se duplica una capa.
- Perder transformaciones al reordenar.
- Mantener una sombra como copia completa de la imagen si una máscara/alpha puede representarla.

**RECOMENDACIÓN TÉCNICA:** el modelo de proyecto debe poder reconstruir el render a partir de capas y parámetros. La UI debe ser una representación del modelo, no el modelo mismo.

---

## 7. Undo/Redo

Android no prescribe una arquitectura específica para el historial de un editor de imágenes. Por ello, esta sección contiene recomendaciones derivadas del problema de memoria documentado.

### Riesgo principal

Un snapshot completo de una composición de gran tamaño puede ser enorme. N snapshots completos multiplican ese coste.

### Enfoque a investigar para AnimeArt

Preferir acciones/estado ligero cuando sea posible:

- agregar capa;
- eliminar capa;
- mover;
- escalar;
- rotar;
- cambiar opacidad;
- cambiar visibilidad;
- editar texto;
- cambiar propiedades.

Para operaciones que cambian píxeles, puede ser necesario guardar un resultado intermedio o información reversible, pero debe existir una política de tamaño.

**REGLA:** no implementar un historial infinito de bitmaps completos.

**DECISIÓN REQUERIDA POR IA-CHAN:** definir, después de medir, cuándo una operación de píxeles merece snapshot, patch, archivo temporal o estrategia híbrida.

---

## 8. Transformaciones

### Riesgos

- Aplicar una rotación físicamente al bitmap cada vez que el usuario gira con el dedo.
- Redimensionar el bitmap repetidamente.
- Acumular redondeos.
- Confundir coordenadas de pantalla con coordenadas del documento.
- Mezclar zoom de cámara con escala de objeto.
- Ignorar densidad o tamaño real del canvas.
- Perder la orientación original después de varias transformaciones.

### Regla de arquitectura

La transformación visual debe almacenarse como parámetros/matriz mientras sea posible:

- translation X/Y;
- scale;
- rotation;
- flip;
- skew/perspective si finalmente se implementan.

La fuente debería conservarse intacta mientras la operación sea sólo geométrica.

El render calcula cómo dibujar la fuente transformada; la exportación rasteriza al resultado final.

**RECOMENDACIÓN TÉCNICA:** una única matriz de transformación por capa es conceptualmente más segura que modificar repetidamente los píxeles fuente.

---

## 9. Importación y exportación

### SAF

Android Storage Access Framework permite que el usuario seleccione documentos mediante el selector del sistema. Para abrir se utiliza `ACTION_OPEN_DOCUMENT`; para crear un archivo, `ACTION_CREATE_DOCUMENT`. El acceso se entrega mediante URI y puede conservarse cuando el proveedor ofrece permisos persistentes. [DOCUMENTADO]  
Fuente: https://developer.android.com/training/data-storage/shared/documents-files

Esto encaja bien con el principio de AnimeArt de evitar permisos innecesarios.

### Importación

Antes de decodificar:

1. Obtener URI y acceso.
2. Identificar MIME cuando esté disponible.
3. Inspeccionar dimensiones.
4. Determinar orientación.
5. Estimar memoria de la representación elegida.
6. Decidir resolución de trabajo.
7. Decodificar fuera del main thread.
8. Validar resultado.

### EXIF

La orientación es un dato explícito del formato. Android ExifInterface documenta valores como 90°, 180°, 270° y diferentes inversiones. [DOCUMENTADO]  
Fuente: https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface

**REGLA:** la orientación de cámara no debe resolverse suponiendo que ancho > alto significa paisaje.

### Exportación

La exportación debe:

- comprobar espacio disponible cuando sea razonable;
- escribir fuera del camino de interacción;
- comprobar errores de I/O;
- cerrar correctamente los streams;
- validar que el resultado se completó;
- evitar destruir el archivo anterior antes de tener un resultado válido.

**RECOMENDACIÓN TÉCNICA:** utilizar archivo temporal + finalización + reemplazo controlado cuando el flujo de almacenamiento lo permita.

---

## 10. Background removal

AnimeArt no utilizará IA para esta función.

### Umbral de color

Un método basado sólo en `RGB > umbral` puede confundir:

- fondo blanco;
- ojos blancos;
- ropa blanca;
- reflejos;
- brillos;
- texto blanco.

### Flood fill / región conectada

Un método conectado al borde mejora la situación porque puede distinguir blanco conectado al borde de blanco aislado dentro del sujeto. Pero tampoco resuelve:

- huecos abiertos;
- fondo no uniforme;
- sombras;
- degradados;
- compresión JPEG;
- objetos blancos conectados al fondo.

### JPEG

JPEG puede introducir pequeñas variaciones de color alrededor de bordes. Por eso un umbral exacto de blanco será demasiado estricto y uno demasiado amplio puede eliminar detalles.

### Alpha y halos

Una máscara binaria dura puede producir bordes artificiales. Una transición suave puede producir halos blancos si el fondo original contaminó los píxeles del borde.

**RECOMENDACIÓN TÉCNICA:** estudiar una cadena:

1. análisis de color;
2. tolerancia configurable;
3. detección de región conectada al borde;
4. máscara alpha;
5. suavizado limitado;
6. corrección manual borrar/restaurar;
7. preview ampliada;
8. exportación sólo cuando el usuario confirme.

**REGLA:** la documentación y UI deben presentar esta función como eliminación especializada de fondos blancos/casi blancos, no como eliminación universal de fondo.

**PRUEBA FUTURA:** usar personajes con ojos, ropa, cabello y brillos blancos sobre fondo blanco.

---

## 11. Sombras

Una sombra desde alpha requiere conceptualmente:

1. obtener la cobertura alpha;
2. generar/usar una máscara;
3. desplazarla;
4. difuminarla;
5. teñirla;
6. componerla bajo el objeto.

El coste aumenta con el área procesada y con los buffers intermedios.

Android documenta `Paint.setShadowLayer()`, pero señala restricciones de soporte para operaciones distintas de texto en el pipeline acelerado. [DOCUMENTADO]  
Fuente: https://developer.android.com/reference/android/graphics/Paint

Android 12/API 31 añade `RenderEffect` con blur, pero AnimeArt tiene minSdk 24, por lo que no puede ser la única estrategia. [DOCUMENTADO]  
Fuente: https://developer.android.com/reference/android/graphics/RenderEffect

**RECOMENDACIÓN TÉCNICA:** mantener la sombra como propiedad derivada de la capa o como representación asociada, no como una segunda copia permanente de la imagen completa. Generarla sólo cuando cambien los parámetros relevantes y reutilizar el resultado hasta que sea necesario invalidarlo.

**DECISIÓN REQUERIDA POR IA-CHAN:** elegir la estrategia compatible con API 24+ después de una prueba de rendimiento real.

---

## 12. Animación/GIF

GIF89a define animación mediante múltiples imágenes y datos de control, incluidos tiempo y método de disposición. La propia especificación señala que el formato no fue concebido como plataforma de animación, aunque permite animaciones limitadas. [DOCUMENTADO]  
Fuente: GIF89a Specification  
https://giflib.sourceforge.net/gifstandard/GIF89a.html

La Library of Congress describe GIF como formato basado en paleta, con 256 colores o menos y compresión LZW. [DOCUMENTADO]  
Fuente: https://www.loc.gov/preservation/digital/formats/fdd/fdd000133.shtml

Android ofrece `AnimatedImageDrawable` para GIF y documenta que el framework decodifica frames posteriores en otro hilo y sólo anima mientras se muestra. [DOCUMENTADO]  
Fuente: https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable

### Riesgos para nuestro editor

- guardar todos los frames a resolución máxima;
- mantener previews y originales simultáneamente;
- exportar todo de golpe en RAM;
- reproducir continuamente cuando el usuario no lo solicita;
- generar GIF sin límites de duración/resolución;
- asumir que GIF conserva color fotográfico como PNG/JPEG.

### Estrategia recomendada

- Frame strip con previews pequeñas.
- Datos completos sólo cuando sean necesarios.
- Procesamiento incremental.
- Liberación de buffers por frame.
- Cancelación de exportación.
- Límite explícito de frames/resolución/duración o advertencia antes de exportar.
- Preview bajo demanda.
- Ninguna animación permanente en background.

**DECISIÓN REQUERIDA POR IA-CHAN:** establecer límites iniciales de frames, resolución y duración después de pruebas de memoria en el dispositivo objetivo.

---

## 13. Touch / multitouch

Android documenta la secuencia básica de eventos y, para multitouch, diferencia `ACTION_DOWN`, `ACTION_POINTER_DOWN` y `ACTION_MOVE`, entre otros. [DOCUMENTADO]  
Fuente: https://developer.android.com/develop/ui/views/touch-and-input/gestures/multi

### Riesgos

- usar sólo el índice del puntero en lugar de su ID;
- asumir que siempre hay dos dedos;
- no gestionar correctamente `POINTER_UP`;
- perder el dedo principal;
- cambiar de gesto en mitad de una operación;
- aplicar escala y rotación a coordenadas ya transformadas;
- confundir zoom del canvas con escala del objeto;
- seleccionar un objeto pequeño cuando otro está encima.

### Arquitectura recomendada

Separar:

- selección;
- pan;
- transformación de una capa;
- zoom/pan del canvas;
- estado de punteros;
- conversión pantalla → documento.

**REGLA:** las coordenadas táctiles deben transformarse al espacio del documento antes de modificar la capa.

**PRUEBAS:** uno, dos y tres dedos; levantar uno de los dedos durante un gesto; comenzar un segundo dedo durante un movimiento; objetos pequeños; objetos superpuestos.

---

## 14. Android lifecycle

Android documenta que ViewModel puede sobrevivir cambios de configuración, pero se destruye cuando el proceso muere por una decisión del sistema. Para recuperar estado tras process death se deben usar mecanismos como SavedStateHandle o persistencia en disco según la naturaleza del dato. [DOCUMENTADO]  
Fuente: https://developer.android.com/topic/libraries/architecture/views/saving-states-views

### Para AnimeArt

Separar tres niveles:

1. **Estado visual temporal:** selección, herramienta abierta, zoom, etc.
2. **Estado de proyecto:** capas, propiedades, transformaciones, texto, orden.
3. **Datos pesados:** imágenes y resultados de procesamiento.

El estado pesado no debería depender exclusivamente de una Activity o ViewModel en memoria.

**REGLA:** el proyecto debe poder reconstruirse sin depender de que la Activity anterior siga viva.

---

## 15. Compatibilidad

Android documenta que existen teléfonos, tablets, foldables y diferentes modos de ventana. Sus guías de calidad adaptable exigen conservar estado durante cambios de configuración y probar diferentes tamaños. [DOCUMENTADO]  
Fuentes:  
https://developer.android.com/guide/topics/large-screens  
https://developer.android.com/docs/quality-guidelines/adaptive-app-quality

AnimeArt no debe diseñarse usando dimensiones fijas del dispositivo principal.

El Xiaomi/Redmi **24116RACCG** será un dispositivo de prueba importante, pero no una especificación arquitectónica.

### Pruebas mínimas posteriores

- teléfono pequeño;
- teléfono grande;
- orientación vertical/horizontal;
- tablet;
- ventana redimensionada;
- multi-window;
- diferentes densidades;
- GPU de capacidades diferentes.

---

## 16. Ciclo de vida y recuperación del trabajo

La recuperación debe contemplar:

- Activity recreation;
- volver desde otra app;
- presión de memoria;
- proceso eliminado;
- orientación;
- cambio de ventana;
- interrupción de exportación.

El proyecto debería guardar un estado recuperable sin convertir cada gesto en una escritura pesada.

**RECOMENDACIÓN TÉCNICA:** guardar metadatos y estructura del proyecto de forma ligera y controlada, y utilizar archivos temporales/activos separados para datos grandes.

---

## 17. Guardado y pérdida de trabajo

### Riesgos

- exportación cortada;
- falta de almacenamiento;
- excepción durante compresión;
- URI que deja de estar disponible;
- archivo temporal abandonado;
- reemplazo prematuro del resultado anterior;
- proceso eliminado durante exportación.

### Regla

Una operación de guardado debe tener estados distinguibles:

- iniciada;
- procesando;
- completada;
- fallida;
- cancelada.

El archivo anterior no debería considerarse sustituido hasta disponer de un resultado válido.

---

## 18. Dependencias

Android recomienda bibliotecas específicas para ciertos casos, por ejemplo Glide para carga de imágenes, pero AnimeArt no debe adoptar una dependencia automáticamente sólo porque exista. [DOCUMENTADO]  
Fuente: https://developer.android.com/topic/performance/graphics/load-bitmap

Actualmente el proyecto base no tiene dependencias externas de edición de imágenes.

Cada dependencia futura debería responder a:

- problema concreto que resuelve;
- coste de APK;
- memoria/runtime;
- compatibilidad con minSdk 24;
- mantenimiento;
- licencia;
- seguridad;
- posibilidad de reemplazarla con Android nativo sin aumentar complejidad.

**REGLA:** no introducir una librería porque “facilita” una función si la misma función puede resolverse de forma razonablemente simple con APIs nativas y la dependencia añade más riesgo que valor.

---

# 19. Plan de pruebas futuras

Estas pruebas son **PLANIFICADAS, NO REALIZADAS**.

## Memoria

1. Abrir una imagen pequeña.
2. Abrir una fotografía grande.
3. Abrir varias imágenes grandes.
4. Crear muchas capas.
5. Duplicar capas repetidamente.
6. Aplicar transformaciones durante varios minutos.
7. Repetir undo/redo muchas veces.
8. Volver atrás y adelante en el historial.
9. Cerrar y volver a abrir el proyecto.
10. Observar memoria antes, durante y después de liberar capas.

## Canvas/rendering

11. Mover rápidamente una capa.
12. Escalar y rotar simultáneamente.
13. Hacer zoom/pan durante varios minutos.
14. Utilizar muchas capas visibles.
15. Utilizar transparencias y sombras.
16. Buscar frames con jank y caídas de fluidez.
17. Comprobar que no se produzcan ANR.

## Transformaciones

18. Escala mínima y máxima.
19. Rotación 0–360° y repetida.
20. Flip horizontal/vertical.
21. Crop en varias relaciones.
22. Encadenar transformaciones.
23. Comparar calidad frente al original.
24. Verificar que zoom del canvas no modifique el documento.

## Importación/exportación

25. JPEG normal.
26. PNG transparente.
27. JPEG con orientación EXIF.
28. Imagen muy grande.
29. Archivo corrupto.
30. URI procedente del selector del sistema.
31. Exportar PNG.
32. Exportar JPG con diferentes calidades.
33. Interrumpir una exportación.
34. Simular almacenamiento insuficiente.
35. Comprobar que el archivo final sea legible.

## Background removal

36. Fondo blanco puro.
37. Fondo casi blanco.
38. Fondo con sombra.
39. Fondo JPEG comprimido.
40. Sujeto con ropa blanca.
41. Sujeto con ojos/blancos internos.
42. Cabello claro.
43. Bordes finos.
44. Regiones blancas desconectadas.
45. Regiones blancas conectadas al fondo.
46. Borrar/restaurar manualmente.

## Sombras

47. Una capa con sombra.
48. Muchas capas con sombra.
49. Blur pequeño.
50. Blur grande.
51. Cambiar desplazamiento rápidamente.
52. Cambiar opacidad.
53. Exportar con sombra.
54. Comprobar memoria durante generación.

## Animación/GIF

55. 4 frames pequeños.
56. 20 frames.
57. Muchos frames.
58. Alta resolución.
59. Duraciones 0.1/0.2/0.5/1 s.
60. Duplicar/reordenar/eliminar frames.
61. Preview repetida.
62. Cancelar exportación.
63. Exportar GIF grande.
64. Comprobar que la RAM no crezca indefinidamente.

## Touch

65. Un dedo: mover.
66. Dos dedos: escalar.
67. Dos dedos: rotar.
68. Pinch + rotación simultáneos.
69. Levantar un dedo en mitad del gesto.
70. Añadir un segundo dedo durante un pan.
71. Objetos pequeños.
72. Objetos superpuestos.
73. Cambiar selección durante interacción.

## Lifecycle

74. Rotar dispositivo.
75. Cambiar entre apps.
76. Volver desde otra app.
77. Cambiar tamaño de ventana.
78. Multi-window.
79. Matar proceso y restaurar.
80. Recuperar un proyecto parcialmente editado.

## Compatibilidad

81. Xiaomi/Redmi 24116RACCG.
82. Teléfono de pantalla pequeña.
83. Teléfono de pantalla grande.
84. Tablet.
85. Android antiguo dentro del minSdk.
86. Android moderno.
87. GPU diferente.
88. Densidades diferentes.

---

# 20. Reglas preventivas de AnimeArt

1. **No conservar múltiples copias completas innecesarias de imágenes de alta resolución en memoria.**
2. **Inspeccionar dimensiones y formato antes de decodificar una imagen grande.**
3. **Toda funcionalidad que procese imágenes grandes debe tener una estrategia explícita de memoria.**
4. **Las previews pueden utilizar una resolución inferior; la exportación debe utilizar los datos necesarios para la calidad final.**
5. **Las transformaciones geométricas no deben destruir innecesariamente la fuente original.**
6. **No generar un Bitmap completo nuevo en cada movimiento táctil.**
7. **No utilizar snapshots completos ilimitados para Undo/Redo.**
8. **El modelo de proyecto debe ser independiente de la UI.**
9. **Las capas deben tener identidad estable y propiedades separadas del contenido rasterizado.**
10. **El main thread no debe ejecutar I/O ni procesamiento de imagen pesado.**
11. **Toda operación larga debe tener un mecanismo de cancelación cuando sea apropiado.**
12. **Toda operación pesada debe comunicar progreso/resultado a la UI sin bloquearla.**
13. **No usar buffers off-screen o efectos costosos sin una justificación de rendimiento.**
14. **No recalcular una sombra mientras sus parámetros y contenido no hayan cambiado.**
15. **El sistema de background removal debe considerarse especializado y tolerante, no universal.**
16. **La eliminación de fondo debe conservar una vía de corrección manual.**
17. **El sistema de GIF debe procesar frames de forma incremental siempre que sea posible.**
18. **La reproducción de preview sólo debe ejecutarse cuando el usuario la solicita.**
19. **La orientación EXIF debe formar parte del proceso de importación.**
20. **El almacenamiento debe preferir APIs modernas controladas por el usuario como SAF cuando corresponda.**
21. **Las exportaciones deben comprobar errores y no sustituir prematuramente resultados válidos.**
22. **El estado del proyecto debe poder reconstruirse después de recreaciones de Activity.**
23. **El proyecto editable no debe depender exclusivamente de memoria para sobrevivir a process death.**
24. **No asumir dimensiones, densidad o proporción de pantalla del dispositivo principal.**
25. **Cada dependencia nueva debe tener una justificación técnica explícita.**
26. **Cada nueva función de procesamiento de imágenes debe tener al menos una prueba de memoria y una prueba de rendimiento cuando corresponda.**
27. **Las pruebas con imágenes pequeñas no son suficientes para validar un editor de imágenes.**
28. **No mantener procesos pesados permanentemente activos.**
29. **No realizar procesamiento continuo cuando el usuario no lo necesita.**
30. **No declarar una función estable hasta probarla en condiciones de carga representativas.**

---

# 21. Cosas que NO debemos hacer

Estas prohibiciones derivan de los riesgos documentados y de las recomendaciones técnicas anteriores:

- Cargar todas las imágenes a resolución máxima sin inspección.
- Confundir tamaño comprimido del archivo con consumo real del bitmap.
- Mantener copias completas para cada paso de Undo.
- Crear un bitmap completo nuevo por cada `ACTION_MOVE`.
- Rasterizar permanentemente cada transformación.
- Fusionar capas sólo para evitar modelar su estado.
- Hacer I/O en el main thread.
- Ejecutar decodificación o exportación pesada dentro del gesto.
- Crear buffers off-screen innecesarios.
- Aplicar blur a imágenes completas si sólo se necesita un área pequeña.
- Generar todas las imágenes de un GIF simultáneamente sin límite de memoria.
- Reproducir animación continuamente en background.
- Ignorar EXIF.
- Suponer que un umbral de blanco funciona perfectamente con cualquier imagen.
- Eliminar todo píxel blanco sin analizar conectividad.
- Prometer eliminación de fondo perfecta sin IA.
- Sustituir un archivo válido antes de terminar la nueva exportación.
- Guardar el estado completo del editor únicamente dentro de una Activity.
- Diseñar la UI con tamaños fijos del Xiaomi de prueba.
- Introducir librerías pesadas para resolver problemas que Android nativo ya cubre razonablemente.
- Copiar arquitectura de Picsart, Snapseed, ibisPaint u otras aplicaciones sin entender sus restricciones.
- Considerar una compilación exitosa como prueba de rendimiento.
- Considerar que una prueba con una sola imagen pequeña valida el sistema de memoria.
- Declarar “sin lag” sin medir.
- Mantener trabajo pesado permanente para funciones que el usuario utiliza ocasionalmente.

---

# 22. Relación con las fases futuras

| Fase | Riesgos que deben incorporarse |
|---|---|
| FASE 1 — Core Editor | Modelo de capas, memoria, transforms no destructivas, undo/redo, render, importación, exportación, lifecycle |
| FASE 2 — Crop/Transform | Matrices, coordenadas, zoom, densidad, calidad, EXIF, no destrucción |
| FASE 3 — Text | Rendering, fuentes, transformaciones, sombras, historial |
| FASE 4 — Bubbles | Vector/shape rendering, transformaciones, texto, historial |
| FASE 5 — Stickers | Memoria, assets, caché, transformaciones, capas |
| FASE 6 — Background Removal | Threshold, conectividad, alpha, halos, JPEG, memoria, cancelación |
| FASE 7 — Shadows | Alpha, máscaras, blur, buffers, API 24+, rendimiento |
| FASE 8 — Animation/GIF | Frames, memoria, procesamiento incremental, límites, preview, exportación |
| FASE 9 — Optimization/Compatibility | Profiling, memoria, ANR, jank, dispositivos, lifecycle, tamaños de pantalla |

---

# 23. DECISIONES REQUERIDAS POR IA-CHAN

La investigación deja varias decisiones que no deben inventarse durante una implementación:

### 23.1 Resolución de trabajo

Definir cuándo AnimeArt conserva resolución original y cuándo utiliza una representación reducida.

### 23.2 Límite de memoria del proyecto

Definir una política de seguridad para evitar que una composición con muchas imágenes provoque OOM.

### 23.3 Arquitectura de Undo/Redo

Elegir entre historial de comandos, estado estructural, snapshots selectivos o enfoque híbrido.

### 23.4 Estrategia de sombras API 24+

Elegir una implementación que no dependa exclusivamente de APIs posteriores a minSdk 24.

### 23.5 Límites de GIF

Definir límites iniciales de frames, resolución y duración después de medir memoria y tiempo.

### 23.6 Persistencia de proyectos

Definir el formato de proyecto y qué datos deben persistirse para reconstruir el trabajo después de process death.

### 23.7 Dependencias futuras

Cada dependencia deberá aprobarse individualmente si la API nativa no resulta suficiente.

---

# 24. Fuentes consultadas

## Fuentes oficiales — Android / Google

1. **Android Developers — Bitmaps and memory**  
   https://developer.android.com/topic/performance/memory/guide/bitmaps  
   Hallazgo: coste de memoria de Bitmap y bytes por píxel.

2. **Android Developers — Loading Large Bitmaps Efficiently**  
   https://developer.android.com/topic/performance/graphics/load-bitmap  
   Hallazgo: inspección de dimensiones, `inSampleSize`, reducción de memoria.

3. **Android Developers — BitmapFactory.Options**  
   https://developer.android.com/reference/android/graphics/BitmapFactory.Options  
   Hallazgo: `inSampleSize`, reutilización de bitmaps y opciones de decodificación.

4. **Android Developers — Managing Bitmap Memory**  
   https://developer.android.com/topic/performance/graphics/manage-memory  
   Hallazgo: estrategias de memoria y liberación de bitmaps.

5. **Android Developers — Caching Bitmaps**  
   https://developer.android.com/topic/performance/graphics/cache-bitmap  
   Hallazgo: LruCache y coste de las cachés en memoria.

6. **Android Developers — Bitmap API reference**  
   https://developer.android.com/reference/android/graphics/Bitmap  
   Hallazgo: `recycle()` y advertencias sobre referencias activas.

7. **Android Developers — App performance guide**  
   https://developer.android.com/topic/performance/overview  
   Hallazgo: memoria, rendimiento, ANR y herramientas de inspección.

8. **Android Developers — Better performance through threading**  
   https://developer.android.com/topic/performance/threads  
   Hallazgo: objetivo aproximado de 16 ms por frame y necesidad de mover trabajo largo fuera del main thread.

9. **Android Developers — Diagnose and fix ANRs**  
   https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs  
   Hallazgo: causas de ANR y timeout de entrada documentado.

10. **Android Developers — StrictMode**  
    https://developer.android.com/reference/android/os/StrictMode  
    Hallazgo: detección de I/O accidental en main thread.

11. **Android Developers — Hardware acceleration**  
    https://developer.android.com/topic/performance/hardware-accel  
    Hallazgo: GPU, RAM y compatibilidad de operaciones Canvas.

12. **Android Developers — Canvas**  
    https://developer.android.com/reference/android/graphics/Canvas  
    Hallazgo: `saveLayer` y buffers de render.

13. **Android Developers — RenderEffect**  
    https://developer.android.com/reference/android/graphics/RenderEffect  
    Hallazgo: blur y efectos desde API 31.

14. **Android Developers — Paint**  
    https://developer.android.com/reference/android/graphics/Paint  
    Hallazgo: `setShadowLayer` y restricciones de rendering.

15. **Android Developers — Storage Access Framework**  
    https://developer.android.com/training/data-storage/shared/documents-files  
    Hallazgo: `ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT` y permisos URI.

16. **Android Developers — ExifInterface**  
    https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface  
    Hallazgo: orientación EXIF y metadatos de imagen.

17. **Android Developers — Save UI states**  
    https://developer.android.com/topic/libraries/architecture/views/saving-states-views  
    Hallazgo: límites de ViewModel y recuperación tras process death.

18. **Android Developers — Multitouch gestures**  
    https://developer.android.com/develop/ui/views/touch-and-input/gestures/multi  
    Hallazgo: eventos y punteros multitouch.

19. **Android Developers — Large screens / adaptive quality**  
    https://developer.android.com/guide/topics/large-screens  
    https://developer.android.com/docs/quality-guidelines/adaptive-app-quality  
    Hallazgo: variedad de tamaños, configuración y pruebas adaptativas.

20. **Android Developers — AnimatedImageDrawable**  
    https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable  
    Hallazgo: reproducción de GIF y decodificación de frames en otro hilo.

21. **Android Developers — WorkManager long-running workers**  
    https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running  
    Hallazgo: opción oficial para trabajo persistente/largo cuando realmente corresponde; no implica que AnimeArt deba mantener trabajo permanente.

## Fuente técnica del formato GIF

22. **GIF89a Specification**  
    https://giflib.sourceforge.net/gifstandard/GIF89a.html  
    Hallazgo: estructura de frames, tiempos, disposal y limitaciones del formato.

23. **Library of Congress — GIF 89a**  
    https://www.loc.gov/preservation/digital/formats/fdd/fdd000133.shtml  
    Hallazgo: GIF basado en paleta de 256 colores o menos y LZW.

## Fuentes de desarrolladores / experiencias comunitarias

No se utilizaron experiencias comunitarias como fundamento principal de las reglas de esta fase. Se priorizaron fuentes técnicas primarias para evitar convertir opiniones o casos aislados en requisitos arquitectónicos.

---

# 25. Clasificación de evidencia

### DOCUMENTADO

Se utiliza cuando la afirmación está explícitamente respaldada por documentación técnica primaria, por ejemplo:

- consumo de memoria de Bitmap;
- `inSampleSize`;
- comportamiento de main thread/ANR;
- hardware acceleration;
- SAF;
- EXIF;
- lifecycle/ViewModel;
- multitouch;
- GIF/AnimatedImageDrawable;
- requisitos adaptativos.

### RECOMENDACIÓN TÉCNICA

Se utiliza cuando la conclusión es una regla de diseño derivada de los hechos anteriores, pero no es una API o requisito oficial de Android.

Ejemplos:

- no crear snapshots completos ilimitados;
- separar transformaciones de la fuente;
- usar procesamiento incremental;
- limitar buffers.

### EXPERIENCIA DE DESARROLLADORES

No se utilizó como fundamento obligatorio en esta investigación.

### HIPÓTESIS / POR VERIFICAR

Debe utilizarse para comportamientos que sólo pueden confirmarse mediante medición en AnimeArt y sus dispositivos objetivo.

Ejemplos:

- cantidad máxima práctica de capas;
- resolución máxima de trabajo;
- límites óptimos de GIF;
- coste real de una estrategia de sombras;
- memoria disponible efectiva bajo diferentes OEM.

---

# 26. Estado de esta investigación

**Investigación:** realizada.

**Implementación:** ninguna.

**Conclusión principal:** AnimeArt debe diseñarse alrededor de un modelo de proyecto ligero, transformaciones no destructivas, control explícito de memoria, procesamiento fuera del main thread y pruebas reales de carga.

Este documento es preventivo. No demuestra que AnimeArt ya cumpla ninguna de estas reglas.

Las reglas deberán convertirse en requisitos y pruebas concretas cuando se implementen las fases correspondientes.
