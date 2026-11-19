export const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

export const todayStr = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export const monthStr = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`

export const addDays = (ymd: string, days: number) => {
  const [y, m, d] = ymd.split('-').map(Number)
  const dt = new Date(y, m - 1, d + days)
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`
}

export const shortDate = (ymd: string) => {
  const [, m, d] = ymd.split('-')
  return `${Number(m)}월 ${Number(d)}일`
}

export const genderLabel = (g: string | null) => (g === 'MALE' ? '남성' : g === 'FEMALE' ? '여성' : g === 'NONE' ? '선택 안 함' : '-')
