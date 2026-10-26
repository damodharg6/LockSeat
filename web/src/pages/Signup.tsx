import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../lib/api'

export default function Signup() {
  const { signup } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await signup(email, password, fullName)
      navigate('/account', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.problem.errors) {
        setError(Object.entries(err.problem.errors).map(([f, m]) => `${f}: ${m}`).join('. '))
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not create account.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card auth-card">
      <h1>Create an account</h1>
      <form className="form" onSubmit={submit}>
        <label>Full name<input autoComplete="name" value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={120} /></label>
        <label>Email<input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required maxLength={254} /></label>
        <label>Password
          <input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} maxLength={72} />
          <span className="muted hint">8 to 72 characters.</span>
        </label>
        {error && <p className="error">{error}</p>}
        <div><button className="btn" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button></div>
      </form>
      <p className="muted">Already have an account? <Link to="/login">Sign in</Link></p>
    </section>
  )
}
