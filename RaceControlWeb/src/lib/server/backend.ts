import "server-only";

const BASE_URL = (process.env.RACECONTROL_API_BASE_URL || "http://localhost:8000").replace(/\/+$/, "");
const API_TOKEN = process.env.RACECONTROL_API_TOKEN || "";

export class BackendError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

/**
 * Server-only fetch against the RaceControl backend, with the shared
 * API_TOKEN injected. Never import this from a Client Component.
 */
export async function callBackend<T>(
  path: string,
  searchParams?: Record<string, string | number | undefined>,
  revalidateSeconds = 3600,
): Promise<T> {
  const url = new URL(BASE_URL + path);
  if (searchParams) {
    for (const [key, value] of Object.entries(searchParams)) {
      if (value !== undefined) url.searchParams.set(key, String(value));
    }
  }

  const headers: Record<string, string> = {};
  if (API_TOKEN) headers.Authorization = `Bearer ${API_TOKEN}`;

  const res = await fetch(url, {
    headers,
    next: { revalidate: revalidateSeconds },
  });

  if (!res.ok) {
    const body = await res.text().catch(() => "");
    const contentType = res.headers.get("content-type") || "";
    // A non-JSON (e.g. HTML) error body almost always means BASE_URL isn't
    // pointing at the backend at all (misconfigured RACECONTROL_API_BASE_URL,
    // wrong host, hitting this web app's own 404 page, etc.) rather than a
    // real backend error; don't dump the whole page into the server logs.
    const summary = contentType.includes("json") || contentType.includes("text/plain")
      ? body.slice(0, 500)
      : `non-JSON response (content-type: ${contentType || "unknown"}), ${body.length} bytes; ` +
        `check RACECONTROL_API_BASE_URL points at the backend, not this app`;
    throw new BackendError(res.status, summary || res.statusText);
  }
  return (await res.json()) as T;
}
