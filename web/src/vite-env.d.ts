/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 'true' 时走真 graph API；默认 mock fixture */
  readonly VITE_USE_GRAPH_API?: string
  /** 真 API 基址；设置非空也会切换到真 API；空 = 同源 */
  readonly VITE_GRAPH_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
