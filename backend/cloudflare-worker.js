export default {
  async fetch(request, env) {
    if (request.method !== 'POST') {
      return new Response(JSON.stringify({ error: 'POST only' }), { status: 405, headers: { 'content-type': 'application/json' } });
    }

    if (!env.OPENAI_API_KEY) {
      return new Response(JSON.stringify({ error: 'OPENAI_API_KEY missing' }), { status: 500, headers: { 'content-type': 'application/json' } });
    }

    const body = await request.json().catch(() => ({}));
    const screenText = String(body.screen_text || '').slice(0, 12000);
    if (!screenText.trim()) {
      return new Response(JSON.stringify({ recommendation: 'No se recibió texto de pantalla.' }), { headers: { 'content-type': 'application/json' } });
    }

    const prompt = `Sos el analizador de Betano Companion. A partir del texto OCR de una pantalla de casino online, identifica solo nombres plausibles de juegos/slots. Usa búsqueda web para verificar, cuando sea posible, RTP publicado, volatilidad y proveedor en fuentes oficiales del proveedor, operador o documentación pública confiable. No afirmes que un juego está caliente, que va a pagar, ni que una sesión será ganadora. Si hay varios juegos verificables, indica cuál tiene el RTP publicado más alto y explica en una frase por qué es estadísticamente menos desfavorable a largo plazo. Si no hay datos verificables, dilo. Responde en español, máximo 280 caracteres, comenzando con "Mejor opción detectada:" o "No pude verificar:".\n\nOCR:\n${screenText}`;

    const apiResponse = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${env.OPENAI_API_KEY}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        model: env.OPENAI_MODEL || 'gpt-5-mini',
        tools: [{ type: 'web_search' }],
        input: prompt
      })
    });

    const data = await apiResponse.json();
    if (!apiResponse.ok) {
      return new Response(JSON.stringify({ error: data }), { status: 502, headers: { 'content-type': 'application/json' } });
    }

    let text = '';
    for (const item of data.output || []) {
      for (const part of item.content || []) {
        if (part.type === 'output_text' && part.text) text += part.text;
      }
    }
    text = text.trim() || 'No pude verificar juegos con los datos visibles.';
    return new Response(JSON.stringify({ recommendation: text }), { headers: { 'content-type': 'application/json' } });
  }
};
