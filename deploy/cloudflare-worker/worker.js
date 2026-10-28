// Резервные входы за пределами основного сервера, запросы пересылаются как есть:
// /api/* — API Mini App для пользователей, у которых мобильная сеть или VPN не пропускают основной адрес;
// /bot*  — Telegram Bot API для самого бота, когда с сервера Telegram недоступен (BOT_BASE_URL или BOT_FALLBACK_BASE_URL).
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const headers = new Headers(request.headers);
    headers.delete('host');
    const body = request.method === 'GET' || request.method === 'HEAD' ? undefined : request.body;

    if (url.pathname.startsWith('/bot') || url.pathname.startsWith('/file/bot')) {
      return fetch('https://api.telegram.org' + url.pathname + url.search, { method: request.method, headers, body });
    }

    if (url.pathname.startsWith('/api/')) {
      const target = new URL(url.pathname + url.search, env.BACKEND_URL || 'https://konyaevo-api.ixlax.space');
      try {
        // Origin и подпись Telegram уходят без изменений, поэтому CORS и авторизацию решает сам бэкенд.
        return await fetch(target, { method: request.method, headers, body, redirect: 'manual' });
      } catch {
        return new Response(JSON.stringify({ error: 'Backend unavailable' }), {
          status: 502,
          headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
        });
      }
    }

    return new Response('Not found', { status: 404 });
  },
};
