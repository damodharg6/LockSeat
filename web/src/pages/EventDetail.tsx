import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { api, ApiError } from '../lib/api'
import { formatDateTime, formatPaise } from '../lib/format'
import type { EventDetail as Event, EventSeat, EventSeatStatus, PageResponse } from '../lib/types'

const SEAT_PAGE_SIZE = 25
const STATUS_LABEL: Record<EventSeatStatus, string> = { AVAILABLE: 'Available', HELD: 'Held', SOLD: 'Sold' }

function SeatTable({ event }: { event: Event }) {
  const [status, setStatus] = useState<EventSeatStatus | ''>('')
  const [section, setSection] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<EventSeat> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const params = new URLSearchParams({ page: String(page), size: String(SEAT_PAGE_SIZE) })
    if (status) params.set('status', status)
    if (section) params.set('section', section)
    api<PageResponse<EventSeat>>(`/api/v1/events/${event.id}/seats?${params}`)
      .then((d) => { setData(d); setError(null) })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load seats.'))
  }, [event.id, event.status, status, section, page])

  return (
    <section className="card">
      <h2>Seats</h2>
      <div className="filters">
        <label>Status
          <select value={status} onChange={(e) => { setStatus(e.target.value as EventSeatStatus | ''); setPage(0) }}>
            <option value="">All</option>
            {(Object.keys(STATUS_LABEL) as EventSeatStatus[]).map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        </label>
        <label>Section
          <select value={section} onChange={(e) => { setSection(e.target.value); setPage(0) }}>
            <option value="">All</option>
            {event.sectionPrices.map((p) => <option key={p.sectionId} value={p.section}>{p.section}</option>)}
          </select>
        </label>
      </div>
      {error && <p className="error">{error}</p>}
      {data && data.totalElements === 0 && (
        <p className="muted">{event.status === 'DRAFT' ? 'Seats are created when the event is published.' : 'No seats match.'}</p>
      )}
      {data && data.totalElements > 0 && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Section</th><th>Row</th><th>Seat</th><th>Price</th><th>Status</th></tr></thead>
            <tbody>
              {data.content.map((s) => (
                <tr key={s.id}>
                  <td>{s.section}</td><td>{s.row}</td><td>{s.number}</td>
                  <td>{formatPaise(s.pricePaise)}</td>
                  <td><span className={`seat-status seat-${s.status.toLowerCase()}`}>{STATUS_LABEL[s.status]}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && data.totalPages > 1 && (
        <div className="pager">
          <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span className="muted">Page {page + 1} of {data.totalPages} · {data.totalElements} seats</span>
          <button className="btn btn-ghost" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </section>
  )
}

export default function EventDetail() {
  const { eventId = '' } = useParams()
  const { user } = useAuth()
  const [event, setEvent] = useState<Event | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    api<Event>(`/api/v1/events/${eventId}`)
      .then((e) => { setEvent(e); setError(null) })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load event.'))
  }, [eventId])

  useEffect(load, [load])

  async function act(action: 'publish' | 'cancel') {
    if (action === 'cancel' && !window.confirm('Cancel this event? This cannot be undone.')) return
    setBusy(true)
    setActionError(null)
    try {
      setEvent(await api<Event>(`/api/v1/events/${eventId}/${action}`, { method: 'POST' }))
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : `Could not ${action} the event.`)
    } finally {
      setBusy(false)
    }
  }

  if (error) return <p className="error">{error} <Link to="/events">Back to events</Link></p>
  if (!event) return <p className="muted">Loading…</p>

  const canManage = !!user && (user.role === 'ADMIN' || user.id === event.organiserId)

  return (
    <div className="stack">
      <div>
        <Link to="/events" className="muted">← Events</Link>
        <div className="page-head">
          <h1>{event.title}</h1>
          <span className={`badge event-${event.status.toLowerCase()}`}>{event.status}</span>
        </div>
        <p className="muted">
          {formatDateTime(event.startsAt)} · <Link to={`/venues/${event.venue.id}`}>{event.venue.name}</Link>, {event.venue.city}
        </p>
      </div>

      {canManage && event.status !== 'CANCELLED' && (
        <section className="card actions">
          {event.status === 'DRAFT' && (
            <button className="btn" disabled={busy} onClick={() => act('publish')}>Publish and generate seats</button>
          )}
          <button className="btn btn-ghost" disabled={busy} onClick={() => act('cancel')}>Cancel event</button>
          {actionError && <p className="error">{actionError}</p>}
        </section>
      )}

      <div className="grid">
        <section className="card">
          <h2>Prices</h2>
          <ul className="plain-list">
            {event.sectionPrices.map((p) => (
              <li key={p.sectionId}><span>{p.section}</span><strong>{formatPaise(p.pricePaise)}</strong></li>
            ))}
          </ul>
        </section>
        <section className="card">
          <h2>Availability</h2>
          {event.totalSeats === 0
            ? <p className="muted">No seats yet.</p>
            : (
              <ul className="plain-list">
                {(Object.keys(STATUS_LABEL) as EventSeatStatus[]).map((s) => (
                  <li key={s}><span>{STATUS_LABEL[s]}</span><strong>{event.seatCounts[s] ?? 0}</strong></li>
                ))}
                <li><span>Total</span><strong>{event.totalSeats}</strong></li>
              </ul>
            )}
        </section>
      </div>

      <SeatTable event={event} />
    </div>
  )
}
