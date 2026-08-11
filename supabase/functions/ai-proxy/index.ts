import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const GEMINI_MODEL = "gemini-flash-latest";
const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;

const TONE_LABELS: Record<number, string> = {
  1: "very bad",
  2: "bad",
  3: "neutral",
  4: "good",
  5: "very good",
};

interface ProxyRequest {
  tone: number;
  thoughts?: string;
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function parseRequest(value: unknown): ProxyRequest | null {
  if (typeof value !== "object" || value === null) return null;
  const { tone, thoughts } = value as Record<string, unknown>;
  if (typeof tone !== "number" || !Number.isInteger(tone) || tone < 1 || tone > 5) return null;
  if (thoughts !== undefined && typeof thoughts !== "string") return null;
  return { tone, thoughts };
}

function buildPrompt({ tone, thoughts }: ProxyRequest): string {
  const label = TONE_LABELS[tone];
  const thoughtsLine = thoughts?.trim()
    ? ` The person also shared this about their day: "${thoughts.trim()}".`
    : "";
  return (
    `Write a short, warm journal-entry starter prompt (1-2 sentences) for someone whose mood ` +
    `today is "${label}".${thoughtsLine} The prompt should invite them to keep writing, not ask ` +
    `a question that requires information you don't have. Respond in the same language the shared ` +
    `thoughts are written in, if any were shared; otherwise respond in English. Return only the ` +
    `prompt text, no preamble or quotation marks.`
  );
}

async function callGemini(prompt: string, apiKey: string): Promise<string> {
  const response = await fetch(GEMINI_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-goog-api-key": apiKey,
    },
    body: JSON.stringify({
      contents: [{ parts: [{ text: prompt }] }],
    }),
  });

  if (!response.ok) {
    console.error(`Gemini call failed: ${response.status} ${await response.text()}`);
    throw new Error("gemini_call_failed");
  }

  const data = await response.json();
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (typeof text !== "string" || text.length === 0) {
    console.error(`Gemini response missing text: ${JSON.stringify(data)}`);
    throw new Error("gemini_response_empty");
  }
  return text;
}

Deno.serve(async (req: Request) => {
  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return jsonResponse(400, { error: "invalid_request" });
  }

  const parsed = parseRequest(body);
  if (!parsed) {
    return jsonResponse(400, { error: "invalid_request" });
  }

  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) {
    console.error("GEMINI_API_KEY secret is not set");
    return jsonResponse(502, { error: "upstream_failed" });
  }

  try {
    const text = await callGemini(buildPrompt(parsed), apiKey);
    return jsonResponse(200, { text });
  } catch (e) {
    console.error(`ai-proxy upstream error: ${e}`);
    return jsonResponse(502, { error: "upstream_failed" });
  }
});
