# Servidor de premium con verificación por servidor (SSV) de AdMob

El premium **vive en este servidor**, no en el móvil (*server-authoritative*). Ver un anuncio
recompensado da **1 día de premium** (quitar anuncios + acceso al contenido premium). El servidor:

1. Recibe el callback SSV de AdMob y **verifica su firma** con las claves públicas de Google.
2. Acredita **24 h** de premium al dispositivo (`user_id` = `deviceId`), con tope de 7 días.
3. Entrega a la app un *entitlement* `{ deviceId, premiumUntil }` **firmado**, que la app verifica
   con la clave pública incrustada (así nadie le puede inyectar un premium falso).
4. Sirve el **contenido premium** solo si ve premium activo para ese dispositivo.

## Endpoints

| Método y ruta                | Quién lo llama      | Qué hace                                                              |
|------------------------------|---------------------|----------------------------------------------------------------------|
| `GET  /admob/ssv`            | Servidores de AdMob | Verifica la firma y **acredita 24 h** de premium al `deviceId`.      |
| `GET  /reward/status?nonce=…`| La app              | `{ "verified": true/false }` para ese anuncio (confirmar que contó). |
| `POST /premium/claim`        | La app (autenticada)| Entitlement `{ deviceId, premiumUntil, issuedAt, signature }`.       |
| `POST /premium/content`      | La app (auth + premium)| Contenido premium; `402` si no hay premium activo.               |
| `GET  /health`               | Railway / tú        | `{ "ok": true }`.                                                    |

Autenticación de dispositivo (para `/premium/*`): el móvil genera un par EC en el Android Keystore
y envía en el cuerpo JSON `{ publicKey, timestamp, signature }`, firmando el reto `deviceId.timestamp`.
El servidor comprueba que posee la clave privada; nadie puede reclamar el premium de otro dispositivo.

La **URL de SSV** que registrarás en AdMob es `https://TU-DOMINIO/admob/ssv`.

## Claves de firma del premium (obligatorio)

El servidor firma los entitlements con una clave que **tú** generas una vez:

```bash
cd server
node gen-keys.js
```

- La **1ª salida** → variable de entorno `ENTITLEMENT_PRIVATE_KEY` en Railway (guárdala en secreto).
- La **2ª salida** (clave pública) → constante `SERVER_ENTITLEMENT_PUBLIC_KEY` en la app
  (`app/.../premium/Premium.kt`).

Si no defines `ENTITLEMENT_PRIVATE_KEY`, el servidor usa una clave **efímera** (cambia en cada
reinicio e invalida el premium cacheado) — solo válido para probar en local.

## 1. Desplegar en Railway

Desde la carpeta `server/` (necesitas la [CLI de Railway](https://docs.railway.app/guides/cli)):

```bash
cd server
railway login          # crea la cuenta al vuelo si no tienes
railway init           # crea un proyecto nuevo
railway up             # sube y despliega esta carpeta
```

> Si en vez de la CLI conectas el repo de GitHub desde el panel de Railway, pon el **Root
> Directory** del servicio en `server` para que despliegue solo esta carpeta.

Railway detecta Node por el `package.json` y arranca con `npm start`. Inyecta `PORT` solo.

Cuando termine, genera un dominio público:

```bash
railway domain         # p. ej. https://gym-ssv-production.up.railway.app
```

Comprueba que responde: abre `https://TU-DOMINIO/health` → debe dar `{"ok":true}`.

## 2. Añadir Postgres (recomendado para producción)

Sin base de datos el servidor guarda las verificaciones **en memoria**: se pierden en cada
redeploy y no valen si Railway escala a más de una instancia. Para persistir:

```bash
railway add            # elige "PostgreSQL"
```

Railway inyecta `DATABASE_URL` automáticamente en el servicio; el código la detecta y crea las
tablas `rewards` y `premium` al arrancar. **Con premium, Postgres es especialmente recomendable**:
en memoria, un redeploy borraría el premium activo de todos. (La conexión interna de Railway no usa
SSL; si algún día apuntas a un Postgres externo que lo exija, añade la variable `PGSSL=require`.)

## 3. Configurar SSV en AdMob

En [AdMob](https://apps.admob.com) → tu app → **Bloques de anuncios** → tu unidad
**Intersticial recompensado** → **Editar** → sección **Verificación del lado del servidor** →
pega la URL del callback:

```
https://TU-DOMINIO/admob/ssv
```

Guarda. (Solo puedes hacer esto en **tu** unidad real, no en la de prueba de Google.)

**Blinda el crediting con `ADMOB_AD_UNIT`.** Las claves con las que Google firma los callbacks son
las mismas para todos los publishers, así que sin este filtro cualquiera podría apuntar la URL SSV
de *su* propia unidad a tu servidor y acreditarse premium. Añade en Railway la variable
`ADMOB_AD_UNIT` con el valor que AdMob envía en el parámetro `ad_unit` (míralo en los logs de
Railway en el primer callback real; suele ser la parte **numérica** de tu ID de unidad). Con eso, el
servidor rechaza cualquier callback que no sea de tu unidad. (Si no la defines, arranca con un aviso
y no filtra — funciona, pero menos blindado.)

## 4. Conectar la app

Rellena estos tres valores en la app:

- `RewardedAds.LIVE_UNIT` → el ID real de tu unidad de intersticial recompensado.
- `RewardedAds.VERIFY_BASE_URL` → `https://TU-DOMINIO` (sin barra final ni ruta).
- `Premium.SERVER_ENTITLEMENT_PUBLIC_KEY` → la clave pública que imprimió `node gen-keys.js`
  (debe ser la pareja de la `ENTITLEMENT_PRIVATE_KEY` de Railway, o el premium nunca se activará).

## 5. Probar SSV de extremo a extremo

La unidad de **prueba** de Google no permite SSV (no es tuya). Para probarlo real y sin tráfico
inválido, registra tu móvil como **dispositivo de prueba** y usa tu unidad real:

1. AdMob → **Configuración** → **Dispositivos de prueba** → añade el ID de tu móvil (aparece en
   el logcat cuando cargas un anuncio: *"Use RequestConfiguration ... setTestDeviceIds"*).
2. Compila la app apuntando a `LIVE_UNIT`.
3. Al ver un anuncio de prueba completo, AdMob llamará a `/admob/ssv` con `reward_item=reward` y
   `reward_amount=1`. En los logs de Railway verás el callback; en la app, la recompensa se
   concede solo tras el `verified:true`.

## Desarrollo local

```bash
cd server
npm install
npm start          # http://localhost:3000  (store en memoria)
```

Para exponerlo a AdMob durante pruebas locales usa un túnel (p. ej. `cloudflared tunnel --url
http://localhost:3000`) y registra esa URL temporal como callback.

## Seguridad — qué garantiza y qué no

Blindado (el servidor manda):
- **No hay premium sin anuncio real:** el premium solo se acredita tras una firma SSV válida de
  Google; falsificar el callback a mano se rechaza con 403.
- **No se puede farmear ni robar:** un anuncio = un crédito (dedup por `nonce`), con tope de 7 días,
  y solo el dispositivo que posee la clave privada del Keystore puede reclamar *su* premium.
- **La app no acepta premium falso:** el entitlement va firmado por el servidor; la app lo verifica
  contra la clave pública incrustada, así que ni un MITM ni unas preferencias manipuladas cuelan.

Límite honesto (inevitable en una app local):
- **Quitar anuncios** se aplica en el móvil, así que un APK **recompilado** que borre la
  comprobación podría saltárselo. Es un límite físico de toda app local.
- **El contenido premium del servidor SÍ es inasaltable:** solo se entrega si el servidor ve
  premium activo, y el payload no existe en el móvil, así que recompilar el APK no lo consigue.
