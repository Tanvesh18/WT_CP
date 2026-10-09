export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>
  constructor(message: string, status = 0, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

const TOKEN_KEY = 'tripshield-token'
export const storedToken = () => sessionStorage.getItem(TOKEN_KEY)
export const saveToken = (token: string) => sessionStorage.setItem(TOKEN_KEY, token)
export const clearToken = () => {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem('tripshield-session')
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = storedToken()
  let response: Response
  try {
    response = await fetch('/api' + path, {
      ...init,
      headers: {
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...init.headers,
      },
    })
  } catch {
    throw new ApiError('Cannot reach TripShield. Check that the backend is running.')
  }
  if (!response.ok) {
    if (response.status === 401 && !path.startsWith('/auth/')) {
      clearToken()
      window.dispatchEvent(new Event('tripshield-session-expired'))
      throw new ApiError('Your session ended. Please sign in again.', 401)
    }
    const body = await response.json().catch(() => ({})) as { detail?: string; message?: string; fieldErrors?: Record<string, string> }
    throw new ApiError(body.detail || body.message || 'Request failed. Please try again.', response.status, body.fieldErrors || {})
  }
  if (response.status === 204 || !response.headers.get('content-type')?.includes('json')) return undefined as T
  return response.json() as Promise<T>
}
