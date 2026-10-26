import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

const ROLE_LABEL = { BUYER: 'Buyer', ORGANISER: 'Organiser', ADMIN: 'Admin' } as const

export default function Account() {
  const { user, loading } = useAuth()

  if (loading) return <p className="muted">Loading…</p>
  if (!user) return <Navigate to="/login" replace state={{ from: '/account' }} />

  return (
    <section className="card auth-card">
      <h1>{user.fullName}</h1>
      <dl className="details">
        <dt>Email</dt><dd>{user.email}</dd>
        <dt>Role</dt><dd><span className="badge badge-accent">{ROLE_LABEL[user.role]}</span></dd>
        <dt>Member since</dt><dd>{new Date(user.createdAt).toLocaleDateString()}</dd>
      </dl>
      {user.role === 'BUYER' && (
        <p className="muted">Buyers can browse venues. Creating venues and seats needs an organiser account.</p>
      )}
    </section>
  )
}
