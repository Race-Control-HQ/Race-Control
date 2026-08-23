import { NextRequest, NextResponse } from "next/server";
import "server-only";

const BASE_URL = (process.env.RACECONTROL_API_BASE_URL || "http://localhost:8000").replace(/\/+$/, "");
const API_TOKEN = process.env.RACECONTROL_API_TOKEN || "";

// Only the read-only /api/* data surface is reachable through this proxy.
// /attest/* and /playintegrity/* are mobile-only device-attestation bootstrap
// endpoints and are intentionally not exposed to the browser.
function isAllowedPath(segments: string[]): boolean {
  return segments.length > 0 && segments[0] === "api";
}

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  const { path } = await params;
  if (!isAllowedPath(path)) {
    return NextResponse.json({ detail: "Not found" }, { status: 404 });
  }

  const url = new URL(BASE_URL + "/" + path.join("/"));
  request.nextUrl.searchParams.forEach((value, key) => {
    url.searchParams.set(key, value);
  });

  const headers: Record<string, string> = {};
  if (API_TOKEN) headers.Authorization = `Bearer ${API_TOKEN}`;

  const upstream = await fetch(url, { headers });
  const body = await upstream.text();
  return new NextResponse(body, {
    status: upstream.status,
    headers: { "Content-Type": upstream.headers.get("Content-Type") || "application/json" },
  });
}
