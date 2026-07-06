/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Absolute origin of the Shikhi backend API (no trailing slash), e.g.
   * https://shikhi-backend.onrender.com. Leave unset for same-origin `/v1`
   * (dev Vite proxy, or a CDN/nginx fronting both SPA and API). See api/client.ts.
   */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
