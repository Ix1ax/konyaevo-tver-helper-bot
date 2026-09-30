interface Env {
  BACKEND_URL?: string;
}

export const onRequest: PagesFunction<Env> = async (context) => {
  const { request, env } = context;
  const url = new URL(request.url);

  // Target backend server URL.
  // Configure in Cloudflare Pages Dashboard -> Settings -> Environment variables -> BACKEND_URL
  // e.g. "http://SERVER_IP:8080"
  const backendBase = env.BACKEND_URL || 'http://127.0.0.1:8080';
  
  // Construct destination URL preserving the /api path and query parameters
  const targetUrl = new URL(url.pathname + url.search, backendBase);

  // Forward the request to Spring Boot backend
  const newRequest = new Request(targetUrl.toString(), {
    method: request.method,
    headers: request.headers,
    body: request.method !== 'GET' && request.method !== 'HEAD' ? request.body : undefined,
    redirect: 'follow',
  });

  try {
    const response = await fetch(newRequest);
    
    // Clone response with CORS headers
    const newHeaders = new Headers(response.headers);
    newHeaders.set('Access-Control-Allow-Origin', '*');
    newHeaders.set('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    newHeaders.set('Access-Control-Allow-Headers', '*');

    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: newHeaders,
    });
  } catch (err: any) {
    return new Response(JSON.stringify({ 
      error: 'Cannot reach backend server', 
      details: err?.message || String(err) 
    }), {
      status: 502,
      headers: { 
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*' 
      },
    });
  }
};
