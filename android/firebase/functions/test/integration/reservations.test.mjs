import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { after, test } from 'node:test';
import { deleteApp, getApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const emulator = process.env.FIRESTORE_EMULATOR_HOST;
const project = process.env.GCLOUD_PROJECT;
assert.match(emulator ?? '', /^(localhost|127\.0\.0\.1):\d+$/);
assert.match(project ?? '', /^demo-/);

const {
  createReservation, listReservations, refreshReservationNotifications, requestDeletion,
} = await import('../../index.mjs');
const db = getFirestore();
after(() => deleteApp(getApp()));

const pickup = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
const trip = {
  service: 'NAVETTE',
  name: 'Passager fictif', phone: '0600000000', email: 'test@example.test',
  departure: 'Gare de La Loupe', arrival: 'Senonches',
  date: pickup, time: '14:00', returnDate: pickup, returnTime: '19:00',
  passengers: 2, luggage: 1, needs: '', notes: '', consent: true, pushToken: '',
};

test('une réservation concurrente crée un document et préserve le suivi du propriétaire', async () => {
  const uid = randomUUID();
  const requestId = randomUUID();
  const request = { auth: { uid }, data: { ...trip, requestId } };
  const replies = await Promise.all([
    createReservation.run(request),
    createReservation.run(request),
  ]);
  assert.deepEqual(replies[0], replies[1]);
  assert.match(replies[0].reference, /^NP-[A-F0-9]{16}$/);
  assert.equal(replies[0].status, 'RECEIVED');

  const document = db.collection('reservations').doc(`${uid}_${requestId}`);
  const saved = await document.get();
  assert.equal(saved.get('returnTime'), '19:00');
  assert.equal(saved.get('mailStatus'), 'PENDING');
  assert.equal((await db.collection('reservationUsage').doc(uid).get()).get('count'), 1);

  await assert.rejects(
    createReservation.run({ ...request, data: { ...request.data, arrival: 'Digny' } }),
    { code: 'already-exists' },
  );
  await assert.rejects(
    createReservation.run({ data: request.data }), { code: 'unauthenticated' },
  );
  await assert.rejects(
    createReservation.run({ ...request, data: { ...request.data, requestId: randomUUID(), passengers: 5 } }),
    { code: 'invalid-argument' },
  );
  assert.deepEqual(await listReservations.run({ auth: { uid: randomUUID() } }), { reservations: [] });

  await document.update({ status: 'CONFIRMED' });
  assert.deepEqual(await listReservations.run({ auth: { uid } }), {
    reservations: [{ reference: replies[0].reference, status: 'CONFIRMED' }],
  });

  const directUrl = `http://${emulator}/v1/projects/${project}/databases/(default)/documents/${document.path}`;
  assert.equal((await fetch(directUrl)).status, 403);
  assert.equal((await fetch(directUrl, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: { status: { stringValue: 'CANCELLED' } } }),
  })).status, 403);
});

test('une demande de suppression répétée conserve sa date initiale', async () => {
  const uid = randomUUID();
  const request = { auth: { uid } };
  const deletion = db.collection('deletionRequests').doc(uid);
  assert.deepEqual(await requestDeletion.run(request), { received: true });
  const first = (await deletion.get()).get('createdAt');
  assert.deepEqual(await requestDeletion.run(request), { received: true });
  const second = await deletion.get();
  assert.equal(second.get('status'), 'PENDING');
  assert.ok(first.isEqual(second.get('createdAt')));
});

test('un nouveau jeton ne touche que les demandes en attente qui ont accepté les notifications', async () => {
  const uid = randomUUID();
  const otherUid = randomUUID();
  const collection = db.collection('reservations');
  const accepted = collection.doc(randomUUID());
  const declined = collection.doc(randomUUID());
  const confirmed = collection.doc(randomUUID());
  const other = collection.doc(randomUUID());
  await Promise.all([
    accepted.set({ uid, status: 'RECEIVED', createdAt: new Date(), pushToken: 'ancien' }),
    declined.set({ uid, status: 'RECEIVED', createdAt: new Date(), notifyOnStatus: false }),
    confirmed.set({ uid, status: 'CONFIRMED', createdAt: new Date(), pushToken: 'ancien' }),
    other.set({ uid: otherUid, status: 'RECEIVED', createdAt: new Date(), pushToken: 'autre' }),
  ]);

  await refreshReservationNotifications.run({ auth: { uid }, data: { pushToken: 'nouveau' } });

  assert.equal((await accepted.get()).get('pushToken'), 'nouveau');
  assert.equal((await declined.get()).get('pushToken'), undefined);
  assert.equal((await confirmed.get()).get('pushToken'), 'ancien');
  assert.equal((await other.get()).get('pushToken'), 'autre');
});
