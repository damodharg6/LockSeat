import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export default function Layout() {
  const { user, logout } = useAuth()

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
            {user ? (
              <>
                <NavLink to="/account">{user.fullName}</NavLink>
                <button className="link-button" onClick={logout}>Sign out</button>
              </>
            ) : (
              <>
                <NavLink to="/login">Sign in</NavLink>
                <NavLink to="/signup">Sign up</NavLink>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </>
  )
}
