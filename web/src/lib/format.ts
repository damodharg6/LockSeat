const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 })

/** Money travels as integer paise; only the UI turns it into rupees. */
export function formatPaise(paise: number): string {
  return rupees.format(paise / 100)
}

/** "499" or "499.50" rupees -> 49950 paise, or null if it is not a valid amount. */
export function rupeesToPaise(input: string): number | null {
  if (!/^\d+(\.\d{1,2})?$/.test(input.trim())) return null
  const [whole, fraction = ''] = input.trim().split('.')
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}
