import { useEffect, useState } from 'react'
import { api } from '../lib/api'

type Status = 'done' | 'doing' | 'todo'

const BUILD_PROGRESS: { tag: string; title: string; status: Status }[] = [
  { tag: 'M0', title: 'Project foundation: API skeleton, errors, CI, hosting', status: 'done' },
  { tag: 'M1', title: 'Venues, sections and seats', status: 'done' },
  { tag: 'M2', title: 'Users, signup/login, JWT, roles', status: 'done' },
  { tag: 'M3', title: 'Events and seat inventory', status: 'doing' },
  { tag: 'M4', title: 'Seat holds under concurrency', status: 'todo' },
  { tag: 'M5', title: 'Checkout, fake payments and tickets', status: 'todo' },
  { tag: 'M6', title: 'Seat-map cache and rate limiting', status: 'todo' },
  { tag: 'M7', title: 'Outbox and Kafka events', status: 'todo' },
  { tag: 'M8', title: 'Load test, observability, hardening', status: 'todo' },
]

const STATUS_LABEL: Record<Status, string> = { done: 'Done', doing: 'In progress', todo: 'Planned' }

function ApiStatus() {
  const [state, setState] = useState<'checking' | 'up' | 'down'>('checking')

  useEffect(() => {
    api<{ status: string }>('/actuator/health')
      .then((h) => setState(h.status === 'UP' ? 'up' : 'down'))
      .catch(() => setState('down'))
  }, [])

  if (state === 'checking') return <span className="badge">Checking API…</span>
  return state === 'up'
    ? <span className="badge badge-ok">● API online</span>
    : <span className="badge badge-bad">● API offline</span>
}

export default function Home() {
  return (
    <div className="stack">
      <section>
        <ApiStatus />
        <h1>Tickets where a seat is never sold twice.</h1>
        <p className="lead">
          SeatLock is an event ticketing backend built to stay correct when thousands of people
          click the same seat at the same moment.
        </p>
      </section>

      <section className="card">
        <h2>Build progress</h2>
        <ul className="progress">
          {BUILD_PROGRESS.map((m) => (
            <li key={m.tag}>
              <span className="tag">{m.tag}</span>
              <span>{m.title}</span>
              <span className={m.status}>{STATUS_LABEL[m.status]}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
