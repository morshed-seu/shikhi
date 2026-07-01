export interface HealthStatus {
  status: string
  service?: string
}

/**
 * Calls the backend liveness endpoint (see docs/43-api-contract.openapi.yaml, GET /health).
 * In dev, Vite proxies /v1 to the Spring Boot backend; in prod it is same-origin.
 */
export async function fetchHealth(): Promise<HealthStatus> {
  const res = await fetch('/v1/health')
  if (!res.ok) {
    throw new Error(`Health check failed: ${res.status}`)
  }
  return (await res.json()) as HealthStatus
}
