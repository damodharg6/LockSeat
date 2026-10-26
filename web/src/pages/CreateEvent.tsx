import { useEffect, useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { api, ApiError } from '../lib/api'
import { rupeesToPaise } from '../lib/format'
import type { EventDetail, PageResponse, Venue, VenueDetail } from '../lib/types'

export default function CreateEvent() {
  const { canManageVenues, loading } = useAuth()
  const navigate = useNavigate()
  const [venues, setVenues] = useState<Venue[]>([])
  const [venueId, setVenueId] = useState('')
  const [venue, setVenue] = useState<VenueDetail | null>(null)
  const [title, setTitle] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [prices, setPrices] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<PageResponse<Venue>>('/api/v1/venues?size=100&sort=name')
      .then((d) => setVenues(d.content))
      .catch(() => setError('Could not load venues.'))
  }, [])

  useEffect(() => {
    if (!venueId) return
    api<VenueDetail>(`/api/v1/venues/${venueId}`)
      .then((v) => { setVenue(v); setPrices(Object.fromEntries(v.sections.map((s) => [s.name, '']))) })
      .catch(() => setError('Could not load the venue.'))
  }, [venueId])

  if (loading) return <p className="muted">Loading…</p>
  if (!canManageVenues) return <Navigate to="/login" replace state={{ from: '/events/new' }} />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const sectionPrices: Record<string, number> = {}
    for (const [section, value] of Object.entries(prices)) {
      const paise = rupeesToPaise(value)
      if (paise === null) {
        setError(`Enter a price for ${section}, e.g. 499 or 499.50.`)
        return
      }
      sectionPrices[section] = paise
    }
    setBusy(true)
    try {
      const event = await api<EventDetail>('/api/v1/events', {
        method: 'POST',
        body: JSON.stringify({
          venueId: Number(venueId),
          title,
          startsAt: startsAt ? new Date(startsAt).toISOString() : null,
          sectionPrices,
        }),
      })
      navigate(`/events/${event.id}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the event.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h1>Create event</h1>
      <form className="form" onSubmit={submit}>
        <label>Venue
          <select value={venueId} onChange={(e) => { setVenue(null); setVenueId(e.target.value) }} required>
            <option value="">Choose a venue</option>
            {venues.map((v) => <option key={v.id} value={v.id}>{v.name}, {v.city}</option>)}
          </select>
        </label>
        <label>Title<input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} /></label>
        <label>Starts at<input type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} required /></label>
        {venue && venue.sections.length === 0 && <p className="error">This venue has no seats yet. Add seats first.</p>}
        {venue && venue.sections.length > 0 && (
          <fieldset className="prices">
            <legend>Price per section (₹)</legend>
            {venue.sections.map((s) => (
              <label key={s.id}>{s.name} <span className="muted hint">{s.seatCount} seats</span>
                <input inputMode="decimal" value={prices[s.name] ?? ''} placeholder="499"
                  onChange={(e) => setPrices({ ...prices, [s.name]: e.target.value })} required />
              </label>
            ))}
          </fieldset>
        )}
        {error && <p className="error">{error}</p>}
        <div><button className="btn" disabled={busy || !venue || venue.sections.length === 0}>{busy ? 'Creating…' : 'Create draft'}</button></div>
      </form>
    </section>
  )
}
