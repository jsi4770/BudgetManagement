export type Category = { id: number; name: string; type: 'EXPENSE' | 'INCOME' }
export type CategoryRef = { id: number; name: string }

export type Me = {
  id: number
  username: string
  email: string
  role: string
  regionCode: string | null
  regionName: string | null
  age: number | null
  gender: 'MALE' | 'FEMALE' | 'NONE' | null
  onboardingCompleted: boolean
}

export type Region = { code: string; name: string; selectable: boolean }

export type Page<T> = { items: T[]; page: number; size: number; totalCount: number; hasNext: boolean }

export type Merchant = {
  id: number
  name: string
  address: string | null
  emdName: string | null
  phone: string | null
  group: string
  groupLabel: string
  baseDate: string | null
}

export type YouthPolicy = {
  id: number
  plcyNo: string
  name: string
  summary: string | null
  category: string | null
  keywords: string[]
  support: string | null
  ageText: string
  applyPeriod: string
  applyEnd: string | null
  dDay: number | null
  applyUrl: string | null
  refUrl: string | null
  institution: string | null
  regionSpecific: boolean
}

export type MonthlySummary = {
  yearMonth: string
  totalIncome: number
  totalExpense: number
  balance: number
  budget: number | null
  remainingBudget: number | null
  budgetUsageRate: number | null
}

export type Transaction = {
  id: number
  category: Category
  amount: number
  transactionDate: string
  memo: string | null
}

export type Parsed = {
  amount: number | null
  transactionDate: string | null
  category: Category | null
  memo: string | null
  missingFields: string[]
}

export type Nudge = {
  id: number
  category: CategoryRef
  title: string
  spendingAmount: number
  avgAmount: number
  growthRate: number
  status: string
}

export type Challenge = {
  id: number
  title: string
  category: CategoryRef
  startDate: string
  endDate: string
  goalType: 'AMOUNT' | 'COUNT'
  goalAmount: number | null
  goalCount: number | null
  spendingAmount: number
  spendingCount: number
  achievementRate: number
  status: 'IN_PROGRESS' | 'SUCCESS' | 'FAILED'
  daysLeft: number
}

export type Proposal = {
  category: CategoryRef | null
  goalType: 'AMOUNT' | 'COUNT'
  goalAmount: number | null
  goalCount: number | null
  periodDays: number
  title: string | null
}

export type ChatMessage = { id: number; role: 'AI' | 'USER'; content: string; createdAt: string; isFallback?: boolean }

export type CoachingSession = {
  coachingId: number
  status: 'IN_PROGRESS' | 'COMPLETED' | 'CANCELED'
  proposal: Proposal | null
  messages: ChatMessage[]
}

export type SendMessageResult = {
  userMessage: ChatMessage
  aiMessage: ChatMessage
  proposal: Proposal | null
  recommendedMerchants: Merchant[]
}
