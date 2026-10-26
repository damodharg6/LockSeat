import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { api, ApiError } from '../lib/api'
import type { PageResponse, Venue } from '../lib/types'

const PAGE_SIZE = 10

function CreateVenueForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('')
  const [city, setCity] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api<Venue>('/api/v1/venues', { method: 'POST', body: JSON.stringify({ name, city }) })
      setName('')
      setCity('')
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create venue.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <h2>Add a venue</h2>
      <label>Name<input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} /></label>
      <label>City<input value={city} onChange={(e) => setCity(e.target.value)} required maxLength={80} /></label>
      {error && <p className="error">{error}</p>}
      <div><button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Create venue'}</button></div>
    </form>
  )
}

export default function Venues() {
  const { canManageVenues } = useAuth()
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<Venue> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    api<PageResponse<Venue>>(`/api/v1/venues?page=${page}&size=${PAGE_SIZE}&sort=name`)
      .then((d) => { setData(d); setError(null) })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load venues.'))
  }, [page])

  useEffect(load, [load])

  return (
    <div className="stack">
      <h1>Venues</h1>
      <section className="card">
        {error && <p className="error">{error}</p>}
        {data && data.content.length === 0 && <p className="muted">No venues yet.</p>}
        {data && data.content.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>City</th></tr></thead>
              <tbody>
                {data.content.map((v) => (
                  <tr key={v.id}>
                    <td><Link to={`/venues/${v.id}`}>{v.name}</Link></td>
                    <td>{v.city}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && data.totalPages > 1 && (
          <div className="pager">
            <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
            <span className="muted">Page {page + 1} of {data.totalPages}</span>
            <button className="btn btn-ghost" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
          </div>
        )}
      </section>
      {canManageVenues ? (
        <section className="card">
          <CreateVenueForm onCreated={() => (page === 0 ? load() : setPage(0))} />
        </section>
      ) : (
        <p className="muted">Organisers can add venues. <Link to="/login">Sign in</Link> with an organiser account.</p>
      )}
    </div>
  )
}
