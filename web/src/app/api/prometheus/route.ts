import { NextRequest, NextResponse } from "next/server"
import { isPrometheusConfigComplete, prometheus_base_url } from "@/lib/config/server"

export const dynamic = "force-dynamic"

type PrometheusPath = "query" | "query_range"

function normalizePrometheusBaseUrl(value: string) {
  const trimmed = value.trim().replace(/\/+$/, "")
  if (/^https?:\/\//.test(trimmed)) {
    return trimmed
  }

  return `http://${trimmed}`
}

function isPrometheusPath(value: string | null): value is PrometheusPath {
  return value === "query" || value === "query_range"
}

export async function GET(request: NextRequest) {
  if (!isPrometheusConfigComplete()) {
    return NextResponse.json(
      { error: "未配置 Prometheus URL，请设置 PROMETHEUS_BASE_URL 或 NEXT_PUBLIC_PROMETHEUS_BASE_URL" },
      { status: 503 }
    )
  }

  const searchParams = new URL(request.url).searchParams
  const path = searchParams.get("path")

  if (!isPrometheusPath(path)) {
    return NextResponse.json(
      { error: "Invalid Prometheus query path" },
      { status: 400 }
    )
  }

  const upstreamUrl = new URL(`${normalizePrometheusBaseUrl(prometheus_base_url || "")}/api/v1/${path}`)
  searchParams.forEach((value, key) => {
    if (key !== "path") {
      upstreamUrl.searchParams.set(key, value)
    }
  })

  try {
    const response = await fetch(upstreamUrl.toString(), {
      cache: "no-store",
      signal: request.signal,
    })
    const body = await response.text()

    return new NextResponse(body, {
      status: response.status,
      headers: {
        "Content-Type": response.headers.get("content-type") || "application/json",
      },
    })
  } catch (error) {
    console.error("Error querying Prometheus:", error)
    return NextResponse.json(
      { error: error instanceof Error ? error.message : "Prometheus query failed" },
      { status: 500 }
    )
  }
}
