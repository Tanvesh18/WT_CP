export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>
  constructor(message: string, status = 0, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

const csrfCookie = () => document.cookie.split('; ').find(value => value.startsWith('TRIPSHIELD_CSRF='))?.slice('TRIPSHIELD_CSRF='.length)
export const clearLegacyToken = () => { sessionStorage.removeItem('tripshield-token'); sessionStorage.removeItem('tripshield-session') }

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch('/api' + path, {
      ...init,
      headers: {
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...(path === '/auth/login' || path === '/auth/register' ? { 'X-TripShield-Browser': '1' } : {}),
        ...(init.method && !['GET', 'HEAD', 'OPTIONS'].includes(init.method.toUpperCase()) && csrfCookie() ? { 'X-TripShield-CSRF': csrfCookie()! } : {}),
        ...init.headers,
      },
    })
  } catch {
    throw new ApiError('Cannot reach TripShield. Check that the backend is running.')
  }
  if (!response.ok) {
    if (response.status === 401 && !['/auth/login', '/auth/register', '/auth/me'].includes(path)) {
      window.dispatchEvent(new Event('tripshield-session-expired'))
      throw new ApiError('Your session ended. Please sign in again.', 401)
    }
    const body = await response.json().catch(() => ({})) as { detail?: string; message?: string; fieldErrors?: Record<string, string> }
    throw new ApiError(body.detail || body.message || 'Request failed. Please try again.', response.status, body.fieldErrors || {})
  }
  if (response.status === 204 || !response.headers.get('content-type')?.includes('json')) return undefined as T
  return response.json() as Promise<T>
}
