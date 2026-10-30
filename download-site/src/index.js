const APK = "https://github.com/TestJuz/Alsama-Digital-ID/releases/latest/download/Alsama-Digital-ID.apk";
const HTML = `<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Alsama Digital ID | Descargar</title>
<style>
body{margin:0;background:#f0f7f1;color:#123d30;font:16px system-ui,sans-serif}
header{background:#123d30;color:white;padding:23px;font-size:22px;font-weight:bold;letter-spacing:3px}
main{max-width:700px;margin:40px auto;padding:20px}
article{padding:40px 24px;background:white;border-radius:26px;text-align:center;box-shadow:0 8px 40px #123d3015}
h1{font-size:clamp(28px,5vw,45px)}p{line-height:1.6;color:#476556}
a.download{display:inline-block;background:#123d30;color:white;text-decoration:none;font-weight:bold;padding:19px 28px;border-radius:14px}
a.download:hover{background:#2c7257}
section{margin-top:25px;background:white;border-radius:20px;padding:24px}
li{padding:7px 0}
small{color:#627b6a}
</style></head><body><header>ALSAMA <small>DIGITAL ID</small></header>
<main><article><h1>Alsama Digital ID</h1>
<p>Descarga la versión más reciente de la aplicación Android de Alsama Tours Costa Rica.</p>
<a class="download" href="/apk">↓ Descargar última APK</a>
<p><small>Descarga pública desde GitHub Releases. No necesitas iniciar sesión en GitHub.</small></p>
</article><section><h2>Instalación</h2><ol>
<li>Descarga el APK desde tu teléfono Android.</li>
<li>Abre el archivo descargado y autoriza la instalación si se solicita.</li>
<li>Abre la aplicación e inicia sesión como empleado o administrador.</li>
</ol><small>Versión de prueba: una firma distinta entre compilaciones puede requerir reinstalar la aplicación.</small>
</section></main></body></html>`;
export default {
  async fetch(request) {
    const p = new URL(request.url).pathname;
    if (request.method !== "GET" && request.method !== "HEAD") return new Response("Method not allowed", {status:405});
    if (p === "/apk") return Response.redirect(APK, 302);
    if (p === "/" || p === "/descargar") return new Response(request.method === "HEAD" ? null : HTML, {
      headers: {"Content-Type":"text/html; charset=utf-8","Cache-Control":"public, max-age=300",
      "Content-Security-Policy":"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'","X-Content-Type-Options":"nosniff"}
    });
    return new Response("Not found", {status:404});
  }
};