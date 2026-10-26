export const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

/** RFC 9457 problem returned by the API for every error. */
export interface Problem {
  type?: string
  title?: string
  status: number
  detail?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  readonly problem: Problem

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${problem.status})`)
    this.problem = problem
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  let res: Response
  try {
    res = await fetch(`${API_URL}${path}`, { ...init, headers })
  } catch {
    throw new ApiError({ status: 0, title: 'Network error', detail: 'Could not reach the SeatLock API.' })
  }

  if (!res.ok) {
    const problem: Problem = await res.json().catch(() => ({ status: res.status }))
    throw new ApiError({ ...problem, status: res.status })
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}
