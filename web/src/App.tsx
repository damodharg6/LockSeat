import { Route, Routes } from 'react-router-dom'
import AuthProvider from './auth/AuthProvider'
import Layout from './components/Layout'
import Account from './pages/Account'
import CreateEvent from './pages/CreateEvent'
import EventDetail from './pages/EventDetail'
import Events from './pages/Events'
import Home from './pages/Home'
import Login from './pages/Login'
import Signup from './pages/Signup'
import VenueDetail from './pages/VenueDetail'
import Venues from './pages/Venues'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Home />} />
          <Route path="/events" element={<Events />} />
          <Route path="/events/new" element={<CreateEvent />} />
          <Route path="/events/:eventId" element={<EventDetail />} />
          <Route path="/venues" element={<Venues />} />
          <Route path="/venues/:venueId" element={<VenueDetail />} />
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="/account" element={<Account />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}
