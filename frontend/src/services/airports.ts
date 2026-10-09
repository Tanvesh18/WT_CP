export type Airport = { code: string; city: string; name: string; country: string }
// Search aid only. Flight availability always comes from Duffel.
export const airports: Airport[] = [
  ['DEL','New Delhi','Indira Gandhi International','India'], ['BOM','Mumbai','Chhatrapati Shivaji Maharaj International','India'],
  ['BLR','Bengaluru','Kempegowda International','India'], ['HYD','Hyderabad','Rajiv Gandhi International','India'],
  ['MAA','Chennai','Chennai International','India'], ['CCU','Kolkata','Netaji Subhas Chandra Bose International','India'],
  ['PNQ','Pune','Pune Airport','India'], ['AMD','Ahmedabad','Sardar Vallabhbhai Patel International','India'],
  ['GOI','Goa','Dabolim Airport','India'], ['GOX','Goa','Manohar International','India'],
  ['COK','Kochi','Cochin International','India'], ['TRV','Thiruvananthapuram','Trivandrum International','India'],
  ['JAI','Jaipur','Jaipur International','India'], ['LKO','Lucknow','Chaudhary Charan Singh International','India'],
  ['BBI','Bhubaneswar','Biju Patnaik International','India'], ['GAU','Guwahati','Lokpriya Gopinath Bordoloi International','India'],
  ['IXC','Chandigarh','Shaheed Bhagat Singh International','India'], ['PAT','Patna','Jay Prakash Narayan Airport','India'],
  ['IDR','Indore','Devi Ahilya Bai Holkar Airport','India'], ['NAG','Nagpur','Dr. Babasaheb Ambedkar International','India'],
  ['VNS','Varanasi','Lal Bahadur Shastri International','India'], ['VTZ','Visakhapatnam','Visakhapatnam Airport','India'],
  ['SXR','Srinagar','Sheikh ul-Alam International','India'], ['ATQ','Amritsar','Sri Guru Ram Dass Jee International','India'],
  ['IXB','Bagdogra','Bagdogra Airport','India'], ['IXR','Ranchi','Birsa Munda Airport','India'],
  ['DXB','Dubai','Dubai International','UAE'], ['SIN','Singapore','Changi Airport','Singapore'],
  ['LHR','London','Heathrow','United Kingdom'], ['JFK','New York','John F. Kennedy International','United States'],
].map(([code,city,name,country]) => ({ code,city,name,country }))
export const airportByCode = (code: string) => airports.find(airport => airport.code === code)

export const searchAirports = (query: string, exclude = '') => airports.filter(airport => airport.code !== exclude && (!query || [airport.code, airport.city, airport.name, airport.country].some(text => text.toLowerCase().includes(query.trim().toLowerCase())))).slice(0, 8)
