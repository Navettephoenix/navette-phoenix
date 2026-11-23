import { calculatePrice } from './pricing.mjs';
import { createHash } from 'node:crypto';

const paris = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Europe/Paris', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
});

function required(data, key, min, max) {
  const value = typeof data[key] === 'string' ? data[key].trim() : '';
  if (value.length < min || value.length > max) throw new Error(`Champ invalide : ${key}`);
  return value;
}

function number(data, key, min, max) {
  const value = data[key];
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`Nombre invalide : ${key}`);
  }
  return value;
}

export function dateInParis(date, time) {
  if (!/^\d{4}-\d\d-\d\d$/.test(date) || !/^\d\d:\d\d$/.test(time)) {
    throw new Error('Date ou heure invalide');
  }
  const wall = Date.parse(`${date}T${time}:00Z`);
  if (!Number.isFinite(wall)) throw new Error('Date ou heure invalide');
  const candidates = [60, 120].map(offset => new Date(wall - offset * 60000))
    .filter(instant => paris.format(instant) === `${date} ${time}`);
  if (candidates.length !== 1) throw new Error('Créneau local invalide ou ambigu');
  return candidates[0];
}

export function normalize(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('Demande invalide');
  const requestId = required(data, 'requestId', 36, 36);
  if (!/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(requestId)) {
    throw new Error('Identifiant de demande invalide');
  }
  const service = required(data, 'service', 5, 7);
  if (!['NAVETTE', 'DEVIS'].includes(service)) throw new Error('Service inconnu');
  const email = required(data, 'email', 5, 120).toLowerCase();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) throw new Error('E-mail invalide');
  const phone = required(data, 'phone', 7, 24);
  if (!/^[+0-9 ().-]+$/.test(phone) || !/^\d{8,15}$/.test(phone.replace(/\D/g, ''))) {
    throw new Error('Téléphone invalide');
  }
  const departure = required(data, 'departure', 3, 250);
  const arrival = required(data, 'arrival', 3, 250);
  if (departure.localeCompare(arrival, 'fr', { sensitivity: 'accent' }) === 0) {
    throw new Error('Départ et arrivée identiques');
  }
  const date = required(data, 'date', 10, 10);
  const time = required(data, 'time', 5, 5);
  const pickup = dateInParis(date, time);
  const returnDate = required(data, 'returnDate', 0, 10);
  const returnTime = required(data, 'returnTime', 0, 5);
  if ((returnDate || returnTime) && dateInParis(returnDate, returnTime) <= pickup) {
    throw new Error('Le retour doit suivre l’aller');
  }
  if (data.consent !== true) throw new Error('Autorisation requise');
  const hasNewPricing = Object.prototype.hasOwnProperty.call(data, 'pricingType');
  let pricing = {};
  if (hasNewPricing) {
    const departureCommune = required(data, 'departureCommune', 0, 120);
    const arrivalCommune = required(data, 'arrivalCommune', 0, 120);
    const departureDetail = required(data, 'departureDetail', 0, 150);
    const arrivalDetail = required(data, 'arrivalDetail', 0, 150);
    const passengers = number(data, 'passengers', 1, 4);
    pricing = calculatePrice(departureCommune, arrivalCommune, Boolean(returnDate), passengers);
    for (const [key, expected] of Object.entries(pricing)) {
      if (data[key] !== expected) throw new Error('Tarif transmis incohérent : ' + key);
    }
    pricing = { ...pricing, departureCommune, arrivalCommune, departureDetail, arrivalDetail };
  }
  return {
    requestId, pickup,
    booking: {
      service, name: required(data, 'name', 2, 100), phone, email,
      departure, arrival, date, time, returnDate, returnTime,
      passengers: number(data, 'passengers', 1, 4),
      luggage: number(data, 'luggage', 0, 16),
      needs: required(data, 'needs', 0, 300),
      notes: required(data, 'notes', 0, 500),
      ...pricing,
      consentVersion: 'V20',
    },
    pushToken: required(data, 'pushToken', 0, 512),
  };
}

export function bookingFingerprint(booking) {
  return createHash('sha256').update(JSON.stringify(booking)).digest('hex');
}

export function formatEmail(value) {
  const title = value.service === 'DEVIS' ? 'Demande de devis' : 'Demande de navette';
  return {
    subject: `Navette Phoenix 28 · ${title} · ${value.reference}`,
    text: [
      `${title} à traiter — ${value.reference}`,
      '',
      `Départ : ${value.departure}`,
      `Arrivée : ${value.arrival}`,
      `Aller (heure de Paris) : ${value.date} à ${value.time}`,
      value.returnDate ? `Retour (heure de Paris) : ${value.returnDate} à ${value.returnTime}` : 'Aller simple',
      `Passagers : ${value.passengers} · Bagages : ${value.luggage}`,
      value.pricingType === 'FIXED'
        ? `Tarif estimé : zone ${value.pricingZone} · ${value.pricePerPassenger} € par passager · ${value.priceOneWay} € par trajet · ${value.priceTotal} € au total`
        : value.pricingType === 'QUOTE' ? 'Tarif sur devis' : '',
      value.pricingType ? 'Tarif estimé, confirmé par le service de réservation.' : '',
      `Nom : ${value.name}`,
      `Téléphone : ${value.phone}`,
      `E-mail : ${value.email}`,
      value.needs ? `Besoins particuliers : ${value.needs}` : '',
      value.notes ? `Commentaire : ${value.notes}` : '',
      'Cette demande attend la validation de Phoenix LTS.',
    ].filter(Boolean).join('\n'),
  };
}
