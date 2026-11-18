const BASE: string = (import.meta.env.VITE_API_URL as string | undefined) || 'http://localhost:8080/api/v1'

let accessToken: string | null = null

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message)
  }
}

type Opts = { method?: string; body?: unknown; query?: Record<string, string | number | undefined | null>; noAuth?: boolean }

function url(path: string, query?: Opts['query']) {
  const u = new URL(BASE + path)
  Object.entries(query ?? {}).forEach(([k, v]) => v !== undefined && v !== null && v !== '' && u.searchParams.set(k, String(v)))
  return u.toString()
}

let refreshing: Promise<boolean> | null = null

/** 쿠키의 refresh_token 으로 액세스 토큰을 다시 받는다. 동시에 여러 번 불리면 한 번만 보낸다. */
export function refreshSession(): Promise<boolean> {
  refreshing ??= fetch(url('/auth/refresh'), { method: 'POST', credentials: 'include' })
    .then(async (r) => {
      if (!r.ok) return false
      const json = await r.json()
      accessToken = json.data?.accessToken ?? null
      return accessToken !== null
    })
    .catch(() => false)
    .finally(() => { refreshing = null })
  return refreshing
}

export function setAccessToken(t: string | null) {
  accessToken = t
}

export async function api<T>(path: string, opts: Opts = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = {}
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  if (accessToken && !opts.noAuth) headers.Authorization = `Bearer ${accessToken}`

  let res: Response
  try {
    res = await fetch(url(path, opts.query), {
      method: opts.method ?? 'GET',
      headers,
      credentials: 'include',
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK', '서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.')
  }

  if (res.status === 401 && !retried && !opts.noAuth && (await refreshSession())) {
    return api<T>(path, opts, true)
  }
  if (res.status === 204) return undefined as T

  const json = await res.json().catch(() => null)
  if (!res.ok || json?.success === false) {
    throw new ApiError(res.status, json?.error?.code ?? 'UNKNOWN', json?.error?.message ?? '요청을 처리하지 못했어요.')
  }
  return json.data as T
}
