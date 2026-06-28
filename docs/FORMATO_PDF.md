# Formato para importar rutinas (PDF o texto)

La app extrae el **texto** del PDF y lo analiza línea por línea. El formato es flexible, pero
funciona mejor si sigues estas reglas. También puedes **pegar el texto** directamente en la pantalla
de importación (útil para probar sin generar un PDF).

## Estructura

```
Rutina: <nombre de la rutina>          ← opcional (si falta, se usa el nombre del archivo)

Día 1: <nombre del día>                ← inicia un día / sección
- <ejercicio>: <series>x<reps>[-<reps>] [@ <peso>]
- <ejercicio> <series>x<reps> <peso>kg
...

Día 2: <nombre del día>
- ...
```

## Reglas que detecta el parser

| Elemento        | Cómo se escribe                              | Ejemplos                          |
|-----------------|----------------------------------------------|-----------------------------------|
| Nombre rutina   | Línea que empieza por `Rutina:` / `Plan:`    | `Rutina: Push Pull Legs`          |
| Día / sección   | `Día N:` / `Day N:` o una línea acabada en `:` | `Día 1: Empuje`, `Pierna:`        |
| Series × reps   | `NxM` o `NxM-K` (rango)                       | `4x8`, `3x8-12`, `4 x 12-15`      |
| Al fallo        | `NxAMRAP` / `Nxfallo` / `Nxmax`              | `4xAMRAP`                         |
| Peso            | `@ Nkg`, `Nkg`, `Nlb` (se convierte a kg)    | `@ 60kg`, `90 kg`, `135 lb`       |
| Descanso (opc.) | `descanso Ns` / `descanso N min`             | `descanso 90s`                    |
| Viñetas         | `-`, `*`, `•`, `1.`, `2)` se ignoran al inicio | `- Press de banca: 4x8`           |

- El **nombre del ejercicio** es todo lo que va antes de las series. Se empareja con el catálogo
  ignorando mayúsculas y acentos (p. ej. `press banca` → *Press de banca*).
- Si un ejercicio **no** está en el catálogo, se crea como **personalizado** y se intenta adivinar su
  grupo muscular por palabras clave (curl→bíceps, sentadilla→cuádriceps, etc.).
- Las líneas que no encajan (notas, semanas, separadores) se ignoran sin romper la importación.

## Ejemplo mínimo

```
Rutina: Full Body 3 días

Día 1
- Sentadilla: 4x6-8 @ 90kg
- Press de banca: 4x6-8 @ 60kg
- Remo con barra: 4x8-10 @ 65kg
- Press militar: 3x8-10
- Curl con barra: 3x10-12

Día 2
- Peso muerto: 4x5 @ 110kg
- Press inclinado mancuernas: 4x8-10 @ 24kg
- Jalón al pecho: 4x10-12
- Elevaciones laterales: 4x12-15
- Extensión de tríceps en polea: 3x12-15
```

Consulta `docs/rutina-ejemplo.txt` para un ejemplo más completo listo para pegar.
