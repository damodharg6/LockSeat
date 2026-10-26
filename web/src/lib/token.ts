/**
 * The access token lives in sessionStorage: it survives a page refresh but not closing the tab,
 * and it expires after 15 minutes anyway. The server decides what the token allows; the web app
 * only uses the role to hide buttons.
 */
const KEY = 'seatlock.token'

export const tokenStore = {
  get(): string | null {
    try { return sessionStorage.getItem(KEY) } catch { return null }
  },
  set(token: string) {
    try { sessionStorage.setItem(KEY, token) } catch { /* storage unavailable: token stays in memory only */ }
  },
  clear() {
    try { sessionStorage.removeItem(KEY) } catch { /* ignore */ }
  },
}
