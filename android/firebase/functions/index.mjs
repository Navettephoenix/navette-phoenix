import { randomBytes } from 'node:crypto';
import { initializeApp } from 'firebase-admin/app';
import { FieldValue, Timestamp, getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { defineSecret } from 'firebase-functions/params';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { onDocumentCreated, onDocumentUpdated } from 'firebase-functions/v2/firestore';
import nodemailer from 'nodemailer';
import { bookingFingerprint, formatEmail, normalize } from './booking.mjs';

initializeApp();
const db = getFirestore();
const region = 'europe-west1';
const callable = { region, enforceAppCheck: true, maxInstances: 5 };
const mailTo = defineSecret('RESERVATION_MAIL_TO');
const smtpUrl = defineSecret('RESERVATION_SMTP_URL');
const mapsServerKey = defineSecret('GOOGLE_MAPS_SERVER_KEY');

function uidOf(request) {
  if (!request.auth?.uid) throw new HttpsError('unauthenticated', 'Identification requise.');
  return request.auth.uid;
}

export const createReservation = onCall(callable, async request => {
  const uid = uidOf(request);
  let input;
  try {
    input = normalize(request.data);
  } catch (error) {
    throw new HttpsError('invalid-argument', error.message);
  }

  const reference = `NP-${randomBytes(8).toString('hex').toUpperCase()}`;
  const reservation = db.collection('reservations').doc(`${uid}_${input.requestId}`);
  const fingerprint = bookingFingerprint(input.booking);
  const quota = db.collection('reservationUsage').doc(uid);

  return db.runTransaction(async transaction => {
    const existing = await transaction.get(reservation);
    if (existing.exists) {
      if (existing.get('fingerprint') !== fingerprint) {
        throw new HttpsError('already-exists', 'Une autre demande utilise cet identifiant.');
      }
      return { reference: existing.get('reference'), status: existing.get('status') };
    }
    if (input.pickup.getTime() <= Date.now()) {
      throw new HttpsError('invalid-argument', 'Choisissez un créneau à venir.');
    }
    const today = new Date().toISOString().slice(0, 10);
    const usage = await transaction.get(quota);
    const count = usage.get('day') === today ? usage.get('count') : 0;
    if (count >= 12) throw new HttpsError('resource-exhausted', 'Limite quotidienne atteinte.');
    transaction.set(quota, { day: today, count: count + 1 });
    transaction.create(reservation, {
      ...input.booking,
      reference, fingerprint, uid, status: 'RECEIVED', createdAt: FieldValue.serverTimestamp(),
      pushToken: input.pushToken || null, notifyOnStatus: Boolean(input.pushToken),
      mailStatus: 'PENDING',
    });
    return { reference, status: 'RECEIVED' };
  });
});

export const listReservations = onCall(callable, async request => {
  const snapshot = await db.collection('reservations').where('uid', '==', uidOf(request))
    .orderBy('createdAt', 'desc').limit(30).get();
  return { reservations: snapshot.docs.map(doc => ({
    reference: doc.get('reference'), status: doc.get('status'),
  })) };
});

export const refreshReservationNotifications = onCall(callable, async request => {
  const uid = uidOf(request);
  const token = request.data?.pushToken;
  if (typeof token !== 'string' || token.length < 1 || token.length > 512 || /\s/.test(token)) {
    throw new HttpsError('invalid-argument', 'Jeton de notification invalide.');
  }
  const recent = db.collection('reservations').where('uid', '==', uid)
    .orderBy('createdAt', 'desc').limit(30);
  await db.runTransaction(async transaction => {
    const snapshot = await transaction.get(recent);
    for (const doc of snapshot.docs) {
      const previous = doc.get('pushToken');
      const optedIn = doc.get('notifyOnStatus') ?? Boolean(previous);
      if (doc.get('status') === 'RECEIVED' && optedIn && previous !== token) {
        transaction.update(doc.ref, { pushToken: token, notifyOnStatus: true });
      }
    }
  });
  return { received: true };
});

export const requestDeletion = onCall(callable, async request => {
  const uid = uidOf(request);
  const ref = db.collection('deletionRequests').doc(uid);
  await db.runTransaction(async transaction => {
    const existing = await transaction.get(ref);
    if (existing.get('status') === 'PENDING') return;
    transaction.set(ref, {
      uid, status: 'PENDING', createdAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });
  return { received: true };
});

export const sendReservationEmail = onDocumentCreated({
  document: 'reservations/{bookingId}', region, retry: true, maxInstances: 3,
  secrets: [mailTo, smtpUrl],
}, async event => {
  const ref = event.data?.ref;
  if (!ref) return;
  const leaseId = randomBytes(16).toString('hex');
  const booking = await db.runTransaction(async transaction => {
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists || snapshot.get('mailStatus') === 'SENT') return null;
    if (snapshot.get('mailLeaseUntil')?.toMillis() > Date.now()) throw new Error('Envoi encore en cours.');
    transaction.update(ref, { mailLeaseId: leaseId, mailLeaseUntil: Timestamp.fromMillis(Date.now() + 120000) });
    return snapshot.data();
  });
  if (!booking) return;
  let smtp;
  try {
    smtp = new URL(smtpUrl.value());
  } catch {
    throw new Error('Configuration SMTP invalide.');
  }
  if (smtp.protocol !== 'smtps:' || !smtp.username || !smtp.password) throw new Error('Configuration SMTP invalide.');
  const recipient = mailTo.value().trim();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(recipient)) throw new Error('Adresse de notification invalide.');
  const sender = decodeURIComponent(smtp.username);
  const transport = nodemailer.createTransport({
    host: smtp.hostname, port: Number(smtp.port || 465), secure: true,
    auth: { user: sender, pass: decodeURIComponent(smtp.password) },
    connectionTimeout: 15000, greetingTimeout: 10000, socketTimeout: 20000,
    disableFileAccess: true, disableUrlAccess: true,
  });

  try {
    const delivery = await transport.sendMail({
      from: { name: 'Navette Phoenix 28', address: sender },
      to: recipient, replyTo: booking.email, ...formatEmail(booking),
    });
    if (!delivery.accepted?.length) throw new Error('Destination SMTP refusée.');
  } catch {
    await db.runTransaction(async transaction => {
      const doc = await transaction.get(ref);
      if (doc.exists && doc.get('mailLeaseId') === leaseId) {
        transaction.update(ref, { mailStatus: 'RETRY', mailLeaseUntil: FieldValue.delete(), mailLeaseId: FieldValue.delete() });
      }
    });
    throw new Error('Échec SMTP, nouvelle tentative programmée.');
  }
  await db.runTransaction(async transaction => {
    const doc = await transaction.get(ref);
    if (doc.exists && doc.get('mailLeaseId') === leaseId) {
      transaction.update(ref, { mailStatus: 'SENT', mailSentAt: FieldValue.serverTimestamp(),
        mailLeaseUntil: FieldValue.delete(), mailLeaseId: FieldValue.delete() });
    }
  });
});

export const notifyReservationStatus = onDocumentUpdated({
  document: 'reservations/{bookingId}', region, maxInstances: 3,
}, async event => {
  const before = event.data?.before;
  const after = event.data?.after;
  if (!before || !after || before.get('status') === after.get('status') || !after.get('pushToken')) return;
  if (!['CONFIRMED', 'DECLINED', 'CANCELLED'].includes(after.get('status'))) return;
  try {
    await getMessaging().send({
      token: after.get('pushToken'),
      data: { type: 'reservation-status' },
      android: { priority: 'high' },
    });
  } catch (error) {
    if (error.code === 'messaging/registration-token-not-registered') {
      await db.runTransaction(async transaction => {
        const latest = await transaction.get(after.ref);
        if (latest.exists && latest.get('pushToken') === after.get('pushToken')) {
          transaction.update(after.ref, { pushToken: FieldValue.delete(), notifyOnStatus: true });
        }
      });
      return;
    }
    throw new Error('Notification de statut indisponible.');
  }
});

async function guardMapsUse(request) {
  const uid = uidOf(request);
  const ref = db.collection('mapUsage').doc(uid);
  const day = new Date().toISOString().slice(0, 10);
  await db.runTransaction(async transaction => {
    const doc = await transaction.get(ref);
    const count = doc.get('day') === day ? doc.get('count') : 0;
    if (count >= 40) throw new HttpsError('resource-exhausted', 'Limite de recherches atteinte.');
    transaction.set(ref, { day, count: count + 1 });
  });
}

function geoInput(value) {
  if (typeof value !== 'string' || value.trim().length < 3 || value.length > 250) {
    throw new HttpsError('invalid-argument', 'Adresse incomplète.');
  }
  return value.trim();
}

async function google(url, mask, body) {
  try {
    const response = await fetch(url, {
      method: 'POST', signal: AbortSignal.timeout(8000),
      headers: {
        'Content-Type': 'application/json',
        'X-Goog-Api-Key': mapsServerKey.value(),
        'X-Goog-FieldMask': mask,
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) throw new Error('Réponse Google Maps indisponible');
    return await response.json();
  } catch {
    throw new HttpsError('unavailable', 'Service cartographique indisponible.');
  }
}

export const suggestAddresses = onCall({ ...callable, secrets: [mapsServerKey] }, async request => {
  const query = geoInput(request.data?.query);
  await guardMapsUse(request);
  const result = await google('https://places.googleapis.com/v1/places:autocomplete',
    'suggestions.placePrediction.text.text',
    { input: query, languageCode: 'fr', includedRegionCodes: ['fr'] });
  return { addresses: (result.suggestions ?? []).flatMap(item => {
    const value = item.placePrediction?.text?.text;
    return typeof value === 'string' ? [value] : [];
  }).slice(0, 5) };
});

export const computeRoute = onCall({ ...callable, secrets: [mapsServerKey] }, async request => {
  const from = geoInput(request.data?.from);
  const to = geoInput(request.data?.to);
  await guardMapsUse(request);
  const result = await google('https://routes.googleapis.com/directions/v2:computeRoutes',
    'routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline',
    { origin: { address: from }, destination: { address: to }, travelMode: 'DRIVE',
      routingPreference: 'TRAFFIC_UNAWARE', languageCode: 'fr-FR' });
  const route = result.routes?.[0];
  if (!route) throw new HttpsError('not-found', 'Itinéraire introuvable.');
  return { distanceMeters: route.distanceMeters,
    durationSeconds: Math.ceil(Number.parseFloat(route.duration)),
    polyline: route.polyline?.encodedPolyline };
});
