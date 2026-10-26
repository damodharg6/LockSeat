export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Venue {
  id: number
  name: string
  city: string
  createdAt: string
}

export interface RowSummary {
  label: string
  seats: number
}

export interface SectionSummary {
  id: number
  name: string
  seatCount: number
  rows: RowSummary[]
}

export interface VenueDetail extends Venue {
  totalSeats: number
  sections: SectionSummary[]
}

export interface AddSeatsResponse {
  sectionId: number
  section: string
  seatsCreated: number
}

export type Role = 'BUYER' | 'ORGANISER' | 'ADMIN'

export interface User {
  id: number
  email: string
  fullName: string
  role: Role
  createdAt: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED'
export type EventSeatStatus = 'AVAILABLE' | 'HELD' | 'SOLD'

export interface VenueSummary {
  id: number
  name: string
  city: string
}

export interface EventSummary {
  id: number
  title: string
  startsAt: string
  status: EventStatus
  venue: VenueSummary
  organiserId: number
}

export interface SectionPrice {
  sectionId: number
  section: string
  pricePaise: number
}

export interface EventDetail extends EventSummary {
  createdAt: string
  publishedAt: string | null
  cancelledAt: string | null
  sectionPrices: SectionPrice[]
  totalSeats: number
  seatCounts: Partial<Record<EventSeatStatus, number>>
}

export interface EventSeat {
  id: number
  seatId: number
  section: string
  row: string
  number: number
  pricePaise: number
  status: EventSeatStatus
}
