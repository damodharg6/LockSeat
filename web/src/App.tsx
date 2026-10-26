import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import Home from './pages/Home'
import VenueDetail from './pages/VenueDetail'
import Venues from './pages/Venues'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/venues" element={<Venues />} />
        <Route path="/venues/:venueId" element={<VenueDetail />} />
      </Route>
    </Routes>
  )
}
