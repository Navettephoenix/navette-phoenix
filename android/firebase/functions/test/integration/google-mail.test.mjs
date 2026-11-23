import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { test } from 'node:test';

const project = process.env.GCLOUD_PROJECT;
const emulator = process.env.FIRESTORE_EMULATOR_HOST;
assert.match(project ?? '', /^demo-/);
assert.match(emulator ?? '', /^(localhost|127\.0\.0\.1):\d+$/);
const database = `projects/${project}/databases/(default)/documents`;
const base = `http://${emulator}/v1/${database}`;

function headers(uid) {
  const now = Math.floor(Date.now() / 1000);
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const claims = {
    sub: uid, user_id: uid, aud: project, iss: `https://securetoken.google.com/${project}`,
    auth_time: now, iat: now, exp: now + 3600, firebase: { sign_in_provider: 'anonymous' },
  };
  return {
    Authorization: `Bearer ${encode({ alg: 'none', typ: 'JWT' })}.${encode(claims)}.`,
    'Content-Type': 'application/json',
  };
}

const string = stringValue => ({ stringValue });
const serverTime = fieldPath => ({ fieldPath, setToServerValue: 'REQUEST_TIME' });
const uidPath = uid => `googleMailAccounts/${uid}`;

function enqueue(uid, owner, id, mailStatus = 'PENDING', extraFields = {}) {
  const account = `${database}/${uidPath(owner)}`;
  const fields = {
    requestId: string(id), service: string('NAVETTE'),
    departure: string('Gare de La Loupe'), arrival: string('Senonches'),
    date: string('2099-01-15'), time: string('14:00'),
    returnDate: string(''), returnTime: string(''),
    passengers: { integerValue: '2' }, luggage: { integerValue: '1' },
    name: string('Passager fictif'), email: string('test@example.test'), phone: string('0600000000'),
    needs: string(''), notes: string(''), consent: { booleanValue: true }, mailStatus: string(mailStatus),
    ...extraFields,
  };
  return fetch(`${base}:commit`, {
    method: 'POST', headers: headers(uid),
    body: JSON.stringify({ writes: [
      {
        update: { name: account, fields: { lastRequestId: string(id) } },
        updateTransforms: [serverTime('lastRequestAt')],
      },
      {
        update: { name: `${account}/googleMailRequests/${id}`, fields },
        updateTransforms: [serverTime('createdAt')],
      },
    ] }),
  });
}

test('Firestore accepte une demande du propriétaire et protège la file de transmission', async () => {
  const uid = randomUUID();
  const other = randomUUID();
  const id = randomUUID();
  const path = `${base}/${uidPath(uid)}/googleMailRequests/${id}`;
  assert.equal((await enqueue(uid, uid, id, 'SENT')).status, 403);
  assert.equal((await enqueue(other, uid, id)).status, 403);

  const result = await enqueue(uid, uid, id);
  assert.equal(result.status, 200, await result.text());
  const own = await fetch(path, { headers: headers(uid) });
  assert.equal(own.status, 200);
  assert.equal((await own.json()).fields.mailStatus.stringValue, 'PENDING');
  assert.equal((await fetch(path, { headers: headers(other) })).status, 403);
  const list = caller => fetch(`${base}/${uidPath(uid)}:runQuery`, {
    method: 'POST', headers: headers(caller),
    body: JSON.stringify({ structuredQuery: {
      from: [{ collectionId: 'googleMailRequests' }],
      orderBy: [{ field: { fieldPath: 'createdAt' }, direction: 'DESCENDING' }],
      limit: 30,
    } }),
  });
  const history = await list(uid);
  assert.equal(history.status, 200);
  assert.ok((await history.json())[0].document.name.endsWith(id));
  assert.equal((await list(other)).status, 403);
  assert.equal((await enqueue(uid, uid, randomUUID())).status, 403);
  assert.equal((await fetch(path + '?updateMask.fieldPaths=mailStatus', {
    method: 'PATCH', headers: headers(uid),
    body: JSON.stringify({ fields: { mailStatus: string('SENT') } }),
  })).status, 403);
});

test('Firestore accepte les champs tarifaires cohérents et refuse un montant modifié', async () => {
  const basePrice = {
    departureCommune: string('Digny'), arrivalCommune: string('Senonches'),
    departureDetail: string(''), arrivalDetail: string(''),
    pricingType: string('FIXED'), pricingSector: string('FORETS_DU_PERCHE'),
    pricingZone: { integerValue: '2' }, pricePerPassenger: { integerValue: '6' },
    priceOneWay: { integerValue: '12' }, priceTotal: { integerValue: '12' },
  };
  const uid = randomUUID();
  const accepted = await enqueue(uid, uid, randomUUID(), 'PENDING', basePrice);
  assert.equal(accepted.status, 200, await accepted.text());

  const tamperedUid = randomUUID();
  const tampered = await enqueue(tamperedUid, tamperedUid, randomUUID(), 'PENDING', {
    ...basePrice, priceTotal: { integerValue: '999' },
  });
  assert.equal(tampered.status, 403, await tampered.text());
});
