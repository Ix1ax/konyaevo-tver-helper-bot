// Резервный вход в API за пределами основного сервера: пересылает запросы как есть.
// Нужен пользователям, у которых мобильная сеть или VPN не пропускают запросы к основному адресу.
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!url.pathname.startsWith('/api/')) return new Response('Not found', { status: 404 });
    const target = new URL(url.pathname + url.search, env.BACKEND_URL);
    const headers = new Headers(request.headers);
    headers.delete('host');
    try {
      // Origin и подпись Telegram уходят без изменений, поэтому CORS и авторизацию решает сам бэкенд.
      return await fetch(target, {
        method: request.method,
        headers,
        body: request.method === 'GET' || request.method === 'HEAD' ? undefined : request.body,
        redirect: 'manual',
      });
    } catch {
      return new Response(JSON.stringify({ error: 'Backend unavailable' }), {
        status: 502,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
      });
    }
  },
};
