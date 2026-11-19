import { useCallback, useEffect, useState } from 'react'
import { ApiError } from './api'

/** 데이터를 불러오는 가장 단순한 훅. 실패하면 error 메시지를 들고 있고, reload 로 다시 받는다. */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fn, deps)
  const reload = useCallback(() => {
    setLoading(true)
    run()
      .then((d) => { setData(d); setError(null) })
      .catch((e) => setError(e instanceof ApiError ? e.message : '불러오지 못했어요.'))
      .finally(() => setLoading(false))
  }, [run])

  useEffect(reload, [reload])
  return { data, error, loading, reload, setData }
}

export const errMsg = (e: unknown) => (e instanceof ApiError ? e.message : '문제가 생겼어요. 잠시 후 다시 시도해 주세요.')
