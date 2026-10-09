import type { FlightOffer } from '../components/FlightOfferPicker'
export const offerExpired = (offer: FlightOffer) => Number.isNaN(Date.parse(offer.expiresAt)) || Date.parse(offer.expiresAt) <= Date.now()
export function money(amount: string, currency: string) { const value = Number(amount); try { return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value) } catch { return currency + ' ' + amount } }
