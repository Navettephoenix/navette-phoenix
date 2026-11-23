import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const source = readFileSync(new URL('./Code.gs', import.meta.url), 'utf8');
const requestId = '12345678-1234-4123-8123-123456789abc';
const recipient = 'caro.resanavettephoenix@gmail.com';
const fixture = {
  requestId, service: 'NAVETTE', departure: 'Gare de La Loupe', arrival: 'Senonches',
  date: '2099-01-15', time: '14:00', returnDate: '2099-01-15', returnTime: '19:00',
  name: 'Passager fictif', phone: '0600000000', email: 'client@example.test', needs: '', notes: '',
};

function sandbox({ quota = 10, conflict = false, providerError = false, priced = false } = {}) {
  const mail = [];
  const states = [];
  const project = 'demo-navette';
  const document = {
    name: `projects/${project}/databases/(default)/documents/googleMailAccounts/test/googleMailRequests/${requestId}`,
    updateTime: '2099-01-12T10:00:00Z',
    fields: {
      ...Object.fromEntries(Object.entries(fixture).map(([key, stringValue]) => [key, { stringValue }])),
      createdAt: { timestampValue: '2099-01-12T10:00:00Z' },
      passengers: { integerValue: '2' }, luggage: { integerValue: '1' },
      consent: { booleanValue: true },
      ...(priced ? {
        departure: { stringValue: 'Digny' },
        arrival: { stringValue: 'Senonches' },
        departureCommune: { stringValue: 'Digny' },
        arrivalCommune: { stringValue: 'Senonches' },
        departureDetail: { stringValue: '' },
        arrivalDetail: { stringValue: '' },
        pricingType: { stringValue: 'FIXED' },
        pricingSector: { stringValue: 'FORETS_DU_PERCHE' },
        pricingZone: { integerValue: '2' },
        pricePerPassenger: { integerValue: '6' },
        priceOneWay: { integerValue: '18' },
        priceTotal: { integerValue: '36' },
        passengers: { integerValue: '3' },
      } : {}),
    },
  };
  let status = 'PENDING';
  let queries = 0;
  const dateFormat = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Europe/Paris', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  });
  const settings = { FIREBASE_PROJECT_ID: project, RESERVATION_MAIL_TO: recipient };
  const run = runInNewContext(source + '\nrelayGoogleMailRequests;', {
    PropertiesService: { getScriptProperties: () => ({ getProperty: name => settings[name] }) },
    LockService: { getScriptLock: () => ({ tryLock: () => true, releaseLock() {} }) },
    ScriptApp: { getOAuthToken: () => 'test-token' },
    Utilities: { formatDate: date => dateFormat.format(date) },
    console: { warn() {} },
    MailApp: {
      getRemainingDailyQuota: () => quota,
      sendEmail(message) {
        assert.equal(status, 'SENDING');
        mail.push(message);
        if (providerError) throw new Error('service indisponible');
      },
    },
    UrlFetchApp: {
      fetch(url, request) {
        assert.equal(request.headers.Authorization, 'Bearer test-token');
        const payload = JSON.parse(request.payload);
        let response;
        let code = 200;
        if (url.endsWith(':runQuery')) {
          queries += 1;
          assert.equal(payload.structuredQuery.where.fieldFilter.value.stringValue, 'PENDING');
          response = status === 'PENDING' ? [{ document }] : [];
        } else {
          assert.equal(request.method, 'patch');
          assert.equal(new URL(url).searchParams.get('currentDocument.updateTime'), document.updateTime);
          if (conflict) {
            code = 412;
          } else {
            status = payload.fields.mailStatus.stringValue;
            states.push(status);
            document.updateTime = `2099-01-12T10:00:0${states.length}Z`;
            response = document;
          }
        }
        return { getResponseCode: () => code, getContentText: () => JSON.stringify(response) };
      },
    },
  });
  return { run, mail, states, queryCount: () => queries };
}

test('transmet une seule fois la demande à la boîte réglée dans le script', () => {
  const relay = sandbox();
  relay.run();
  relay.run();
  assert.deepEqual(relay.states, ['SENDING', 'SENT']);
  assert.equal(relay.mail.length, 1);
  assert.equal(relay.mail[0].to, recipient);
  assert.equal(relay.mail[0].replyTo, fixture.email);
  assert.match(relay.mail[0].subject, /NP-1234567812344123/);
  assert.match(relay.mail[0].body, /Retour \(heure de Paris\).*19:00/);
});

test('calcule et transmet le tarif forfaitaire réel avec le nombre de passagers', () => {
  const check = runInNewContext(source + '\nbookingPrice_;', {});
  const fromForet = check('Digny', 'Senonches', true, 3);
  assert.equal(fromForet.zone, 2);
  assert.equal(fromForet.price, 6);
  assert.equal(fromForet.oneWay, 18);
  assert.equal(fromForet.total, 36);
  assert.equal(check('SAINT-ELIPH', 'fontaine simon', true, 2).total, 24);
  assert.equal(check('Senonches', 'La Loupe', false, 1), null);
  assert.equal(check('Autre commune', 'Senonches', false, 1), null);
});

test('envoie un e-mail contenant le total et la zone, vérifiés par le relais', () => {
  const relay = sandbox({ priced: true });
  relay.run();
  assert.equal(relay.mail.length, 1);
  assert.match(relay.mail[0].body, /zone 2/);
  assert.match(relay.mail[0].body, /18 € le trajet/);
  assert.match(relay.mail[0].body, /36 € au total/);
});

test('garde les demandes en attente si le quota est épuisé', () => {
  const relay = sandbox({ quota: 0 });
  relay.run();
  assert.equal(relay.queryCount(), 0);
  assert.equal(relay.mail.length, 0);
});

test('laisse une demande en cours de vérification quand Google renvoie une erreur', () => {
  const relay = sandbox({ providerError: true });
  relay.run();
  relay.run();
  assert.deepEqual(relay.states, ['SENDING']);
  assert.equal(relay.mail.length, 1);
});

test('saute une demande qui a changé avant le verrouillage', () => {
  const relay = sandbox({ conflict: true });
  relay.run();
  assert.equal(relay.mail.length, 0);
});
