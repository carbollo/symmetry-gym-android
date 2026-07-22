import { Hono } from 'hono'
import { and, eq, isNull } from 'drizzle-orm'
import { db } from '../db/client.js'
import { sharedRoutines } from '../db/schema.js'

// App identity used for Android App Links verification. The fingerprint is the
// SHA-256 of the signing cert (currently the debug keystore, shared by debug and
// the localRelease build). If the app is re-signed, update this.
const PACKAGE = 'com.aesthetic.gym'
const SHA256 = '1C:10:04:AF:38:8D:B4:BA:32:72:62:9C:66:28:5C:27:A5:A6:6C:45:EC:2F:DD:19:A0:76:3E:61:DA:25:34:C2'

export const linkRoutes = new Hono()

// Digital Asset Links: lets Android auto-open Zenit for https://<this-host>/r/* URLs
// instead of the browser (verified App Links).
linkRoutes.get('/.well-known/assetlinks.json', (c) =>
  c.json([
    {
      relation: ['delegate_permission/common.handle_all_urls'],
      target: {
        namespace: 'android_app',
        package_name: PACKAGE,
        sha256_cert_fingerprints: [SHA256],
      },
    },
  ])
)

// Public landing for a shared routine link. If Zenit is installed and the link is
// verified, Android opens the app directly and this page never renders. Otherwise
// (desktop, app not installed) it offers to open the app or explains what to do.
linkRoutes.get('/r/:code', async (c) => {
  const code = (c.req.param('code') ?? '').trim().toUpperCase()
  const [row] = await db
    .select({ name: sharedRoutines.name })
    .from(sharedRoutines)
    .where(and(eq(sharedRoutines.code, code), isNull(sharedRoutines.deletedAt)))
    .limit(1)

  const host = new URL(c.req.url).host
  const intentUrl = `intent://${host}/r/${encodeURIComponent(code)}#Intent;scheme=https;package=${PACKAGE};end`
  return c.html(landingHtml(row?.name ?? null, intentUrl))
})

function esc(s: string): string {
  return s.replace(/[&<>"']/g, (ch) =>
    ch === '&' ? '&amp;' : ch === '<' ? '&lt;' : ch === '>' ? '&gt;' : ch === '"' ? '&quot;' : '&#39;'
  )
}

function landingHtml(name: string | null, intentUrl: string): string {
  const title = name ? `Rutina "${esc(name)}"` : 'Rutina de Zenit'
  const body = name
    ? `Alguien quiere compartir contigo la rutina <b>${esc(name)}</b>.`
    : 'Este enlace no encontró la rutina (puede que se haya eliminado).'
  return `<!doctype html>
<html lang="es"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title} · Zenit</title>
<style>
  :root{color-scheme:dark}
  body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0B0B10;color:#F5F6F2;
       font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
  .card{max-width:420px;padding:32px 24px;text-align:center}
  h1{font-size:22px;margin:0 0 6px}
  p{color:#9A9AA8;line-height:1.5}
  a.btn{display:inline-block;margin-top:20px;padding:14px 22px;border-radius:14px;background:#7C5CFF;
        color:#fff;font-weight:700;text-decoration:none}
  .muted{font-size:12px;color:#63636F;margin-top:22px}
</style>
</head><body>
  <div class="card">
    <h1>Zenit</h1>
    <p>${body}</p>
    ${name ? `<a class="btn" href="${intentUrl}">Abrir en Zenit</a>` : ''}
    <p class="muted">Necesitas la app Zenit instalada. Al abrirla, la rutina se añadirá a tu galería.</p>
  </div>
</body></html>`
}
