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
