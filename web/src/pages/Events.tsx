import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { api, ApiError } from '../lib/api'
import { formatDateTime } from '../lib/format'
import type { EventSummary, PageResponse } from '../lib/types'

const PAGE_SIZE = 10

interface Filters {
  city: string
  from: string
  to: string
}

function toQuery(filters: Filters, page: number): string {
  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) })
  if (filters.city.trim()) params.set('city', filters.city.trim())
  if (filters.from) params.set('from', new Date(filters.from).toISOString())
  if (filters.to) params.set('to', new Date(filters.to).toISOString())
  return params.toString()
}

export default function Events() {
  const { canManageVenues } = useAuth()
  const [draft, setDraft] = useState<Filters>({ city: '', from: '', to: '' })
  const [filters, setFilters] = useState<Filters>(draft)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<EventSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<PageResponse<EventSummary>>(`/api/v1/events?${toQuery(filters, page)}`)
      .then((d) => { setData(d); setError(null) })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load events.'))
  }, [filters, page])

  function search(e: FormEvent) {
    e.preventDefault()
    setPage(0)
    setFilters(draft)
  }

  return (
    <div className="stack">
      <div className="page-head">
        <h1>Events</h1>
        {canManageVenues && <Link className="btn" to="/events/new">Create event</Link>}
      </div>
      <form className="card filters" onSubmit={search}>
        <label>City<input value={draft.city} onChange={(e) => setDraft({ ...draft, city: e.target.value })} placeholder="Any city" /></label>
        <label>From<input type="datetime-local" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} /></label>
        <label>To<input type="datetime-local" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} /></label>
        <div><button className="btn">Search</button></div>
      </form>
      <section className="card">
        {error && <p className="error">{error}</p>}
        {data && data.content.length === 0 && <p className="muted">No upcoming events match.</p>}
        {data && data.content.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Event</th><th>When</th><th>Venue</th></tr></thead>
              <tbody>
                {data.content.map((ev) => (
                  <tr key={ev.id}>
                    <td><Link to={`/events/${ev.id}`}>{ev.title}</Link></td>
                    <td>{formatDateTime(ev.startsAt)}</td>
                    <td>{ev.venue.name}, {ev.venue.city}</td>
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
    </div>
  )
}
