# Symmetry (clon) — App de gimnasio para Android

App nativa de Android inspirada en [symmetry.club](https://symmetry.club/): registro de
entrenamientos con **sobrecarga progresiva automática**, **rangos de fuerza por músculo con mapa
corporal**, **seguimiento de progreso (fotos y gráficas)** e **importación de rutinas desde PDF**
con detección automática de ejercicios.

Todo funciona **offline**: los datos se guardan en el dispositivo con una base de datos local (Room).

---

## ✨ Funciones

- **Importar rutina desde PDF** (o pegando texto): el parser detecta días, ejercicios, series,
  repeticiones y peso, y los empareja automáticamente con un catálogo de +50 ejercicios. Lo que no
  reconoce lo crea como ejercicio personalizado adivinando el grupo muscular.
- **Registro de entrenamiento** con interfaz limpia: series, repeticiones y peso con un toque.
- **Sobrecarga progresiva automática**: doble progresión — cuando completas el rango de reps en
  todas las series, te sugiere subir el peso en el próximo entreno.
- **Rangos + mapa corporal**: cada grupo muscular recibe una puntuación 0–100 y un rango
  (Principiante → Maestro) según tu 1RM estimado relativo a tu peso corporal. Se pinta sobre un
  cuerpo interactivo (frente y espalda).
- **Progreso**: gráfica de peso corporal, gráfica de 1RM estimado por ejercicio y fotos de progreso.
- **Perfil**: peso, altura, sexo y unidad (kg/lb) — el peso y el sexo alimentan el cálculo de rangos.

---

## 🚀 Cómo compilar y ejecutar

Necesitas **Android Studio** (ya lo tienes instalado) y **JDK 17** (incluido).

### Opción A — Android Studio (recomendado)
1. Abre Android Studio → **File ▸ Open…** y selecciona esta carpeta (`gym`).
2. Espera al *Gradle Sync* (descarga dependencias la primera vez).
3. Conecta un móvil con depuración USB o crea un emulador (**Device Manager**).
4. Pulsa ▶ **Run 'app'**.

### Opción B — Línea de comandos
```bash
# desde la carpeta del proyecto
./gradlew assembleDebug          # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # instala en un dispositivo/emulador conectado
```
En Windows usa `gradlew.bat` en lugar de `./gradlew`.

> El `gradle-wrapper.jar` ya está incluido. Si Android Studio pide regenerar el wrapper, deja que lo
> haga; no afecta al código.

---

## 🧱 Stack y arquitectura

- **Kotlin + Jetpack Compose** (Material 3, tema oscuro).
- **Room** para persistencia local + **KSP**.
- **PDFBox-Android** (`com.tom-roush:pdfbox-android`) para extraer texto de PDFs.
- **Coil** para fotos de progreso.
- **Navigation Compose** con barra inferior de 5 secciones.
- DI manual sencilla (`AppContainer`) — sin frameworks.

```
app/src/main/java/com/aesthetic/gym/
├─ data/
│  ├─ db/            Entidades Room, DAOs, relaciones, conversores, AppDatabase
│  ├─ repo/          GymRepository (único punto de acceso a datos)
│  └─ seed/          ExerciseCatalog (catálogo de ejercicios con alias)
├─ domain/
│  ├─ model/         MuscleGroup, Rank, enums
│  ├─ overload/      ProgressiveOverload (doble progresión)
│  └─ rank/          RankCalculator (puntuación y tier por músculo)
├─ pdf/              RoutineParser, ExerciseMatcher, MuscleGuesser, PdfTextExtractor, RoutineImporter
├─ ui/
│  ├─ theme/  components/  nav/
│  ├─ home/ routines/ workout/ body/ progress/ profile/
│  └─ ...
├─ di/AppContainer.kt
├─ SymmetryApp.kt    MainActivity.kt
```

---

## 📄 Importar rutinas desde PDF

Mira **[docs/FORMATO_PDF.md](docs/FORMATO_PDF.md)** para el formato esperado y
**[docs/rutina-ejemplo.txt](docs/rutina-ejemplo.txt)** para un ejemplo que puedes pegar directamente
en la pantalla de importación (botón *Rutinas ▸ Importar rutina ▸ pegar texto*) para probarlo sin un
PDF. Para probar con PDF, copia ese texto a un documento y expórtalo como PDF.

---

## ⚠️ Notas

- Los rangos usan estándares de fuerza aproximados (1RM estimado con la fórmula de Epley relativo al
  peso corporal). Son una guía motivacional, no una medida clínica.
- El AI Body Scan y la red social de Symmetry **no** están en esta versión MVP (requieren backend y
  visión por computador). La arquitectura está lista para añadirlos más adelante.
- `applicationId` es `com.aesthetic.gym` (distinto del original) para evitar conflictos.
