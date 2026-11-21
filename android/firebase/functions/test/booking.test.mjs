import assert from 'node:assert/strict';
import test from 'node:test';
import { bookingFingerprint, dateInParis, formatEmail, normalize } from '../booking.mjs';

const valid = {
  requestId: 'ec91fecd-b099-470e-9ced-6c8e6ca9a72b',
  service: 'NAVETTE', departure: '4 rue fictive, Senonches', arrival: 'Gare de La Loupe',
  date: '2027-04-16', time: '11:30', returnDate: '', returnTime: '',
  name: 'Passager Test', phone: '+33 6 00 00 00 00', email: 'test@example.test',
  passengers: 2, luggage: 3, needs: '', notes: '', consent: true, pushToken: '',
};

test('parse la date de Paris et rejette les créneaux ambigus ou inexistants', () => {
  assert.equal(dateInParis('2027-04-16', '11:30').toISOString(), '2027-04-16T09:30:00.000Z');
  assert.throws(() => dateInParis('2027-03-28', '02:20'));
  assert.throws(() => dateInParis('2027-10-31', '02:20'));
  assert.throws(() => dateInParis('2027-02-30', '11:30'));
});

test('accepte un retour ultérieur et rejette les valeurs invalides', () => {
  assert.equal(normalize({...valid, returnDate: valid.date, returnTime: '20:00'}).booking.returnTime, '20:00');
  for (const diff of [
    {consent:false}, {passengers:5}, {luggage:-1}, {email:'incorrect'},
    {arrival: valid.departure.toUpperCase()}, {returnDate: valid.date}, {returnDate: valid.date, returnTime:'09:00'},
  ]) assert.throws(() => normalize({...valid, ...diff}));
});

test('la même demande conserve son empreinte même si le jeton de notification change', () => {
  assert.equal(bookingFingerprint(normalize(valid).booking),
    bookingFingerprint(normalize({...valid, pushToken:'second-jeton'}).booking));
  assert.notEqual(bookingFingerprint(normalize(valid).booking),
    bookingFingerprint(normalize({...valid, passengers:3}).booking));
});

test('le devis détaille le retour, les bagages et laisse la demande en attente', () => {
  const booking = normalize({...valid, service:'DEVIS', returnDate: valid.date, returnTime: '20:00'}).booking;
  const message = formatEmail({...booking, reference:'NP-TEST'});
  assert.match(message.subject, /devis.*NP-TEST/);
  assert.match(message.text, /Retour.*20:00/);
  assert.match(message.text, /Bagages : 3/);
  assert.match(message.text, /attend la validation/);
});
