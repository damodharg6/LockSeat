import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { parseRows } from '../lib/rows'
import type { AddSeatsResponse, SectionSummary, VenueDetail as Venue } from '../lib/types'

function SeatGrid({ section }: { section: SectionSummary }) {
  return (
    <div className="seat-section">
      <h3>{section.name} <span className="muted">· {section.seatCount} seats</span></h3>
      <div className="seat-grid">
        {section.rows.map((row) => (
          <div className="seat-row" key={row.label}>
            <span className="seat-row-label">{row.label}</span>
            {Array.from({ length: row.seats }, (_, i) => (
              <span className="seat" key={i} title={`${section.name} ${row.label}${i + 1}`} />
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}

function AddSeatsForm({ venueId, onAdded }: { venueId: string; onAdded: () => void }) {
  const [section, setSection] = useState('')
  const [rows, setRows] = useState('A-J')
  const [seatsPerRow, setSeatsPerRow] = useState(20)
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setMessage(null)
    try {
      const res = await api<AddSeatsResponse>(`/api/v1/venues/${venueId}/seats`, {
        method: 'POST',
        body: JSON.stringify({ section, rowLabels: parseRows(rows), seatsPerRow }),
      })
      setMessage({ ok: true, text: `Added ${res.seatsCreated} seats to ${res.section}.` })
      onAdded()
    } catch (err) {
      setMessage({ ok: false, text: err instanceof ApiError ? err.message : 'Could not add seats.' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <h2>Add seats</h2>
      <label>Section<input value={section} onChange={(e) => setSection(e.target.value)} required maxLength={60} placeholder="Stalls" /></label>
      <label>Rows<input value={rows} onChange={(e) => setRows(e.target.value)} required placeholder="A-J or A, B, AA" /></label>
      <label>Seats per row
        <input type="number" min={1} max={200} value={seatsPerRow} onChange={(e) => setSeatsPerRow(Number(e.target.value))} required />
      </label>
      {message && <p className={message.ok ? 'muted' : 'error'}>{message.text}</p>}
      <div><button className="btn" disabled={busy}>{busy ? 'Adding…' : 'Add seats'}</button></div>
    </form>
  )
}

export default function VenueDetail() {
  const { venueId = '' } = useParams()
  const [venue, setVenue] = useState<Venue | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    api<Venue>(`/api/v1/venues/${venueId}`)
      .then((v) => { setVenue(v); setError(null) })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load venue.'))
  }, [venueId])

  useEffect(load, [load])

  if (error) return <p className="error">{error} <Link to="/venues">Back to venues</Link></p>
  if (!venue) return <p className="muted">Loading…</p>

  return (
    <div className="stack">
      <div>
        <Link to="/venues" className="muted">← Venues</Link>
        <h1>{venue.name}</h1>
        <p className="muted">{venue.city} · {venue.totalSeats} seats</p>
      </div>
      <section className="card">
        <h2>Seat map</h2>
        {venue.sections.length === 0
          ? <p className="muted">No seats yet.</p>
          : venue.sections.map((s) => <SeatGrid key={s.id} section={s} />)}
      </section>
      <section className="card">
        <AddSeatsForm venueId={venueId} onAdded={load} />
      </section>
    </div>
  )
}
