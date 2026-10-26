import { Link, NavLink, Outlet } from 'react-router-dom'

export default function Layout() {
  return (
    <>
      <header className="nav">
        <div className="container">
          <Link to="/" className="brand">
            <img src="/favicon.svg" alt="" className="brand-mark" />
            SeatLock
          </Link>
          <nav className="nav-links">
            <NavLink to="/" end>Home</NavLink>
            <NavLink to="/venues">Venues</NavLink>
          </nav>
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </>
  )
}
