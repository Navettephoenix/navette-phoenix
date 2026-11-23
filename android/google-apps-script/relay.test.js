'use strict';

/**
 * Tests locaux sans Google, sans Firebase et sans envoi réel.
 * Depuis android/google-apps-script : node relay.test.js
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, 'Code.gs'), 'utf8');
const id = '12345678-1234-4123-8123-123456789012';
const parent = 'projects/navette-phoenix/databases/(default)/documents';
const name = parent + '/googleMailAccounts/client/googleMailRequests/' + id;

function harness({ customer = false, failBusiness = false, failFinalPatch = false, quota = 100 } = {}) {
  const docs = [{
    name,
    updateTime: '2026-11-23T09:00:00.000Z',
    fields: {
      requestId: { stringValue: id }, service: { stringValue: 'NAVETTE' },
      departure: { stringValue: 'Senonches · Mairie' },
      arrival: { stringValue: 'La Loupe · Gare' },
      date: { stringValue: '2027-04-16' }, time: { stringValue: '10:15' },
      returnDate: { stringValue: '' }, returnTime: { stringValue: '' },
      passengers: { integerValue: '2' }, luggage: { integerValue: '1' },
      name: { stringValue: 'Test Voyageur' }, phone: { stringValue: '0612345678' },
      email: { stringValue: 'client@example.test' }, needs: { stringValue: '' },
      notes: { stringValue: '' }, consent: { booleanValue: true },
      createdAt: { timestampValue: '2026-11-23T09:00:00.000Z' },
      mailStatus: { stringValue: 'PENDING' }
    }
  }];
  const sent = [];
  const logs = [];
  let updates = 0;
  function response(code, data) {
    return { getResponseCode: () => code, getContentText: () => JSON.stringify(data) };
  }
  const mocks = {
    PropertiesService: { getScriptProperties: () => ({ getProperty: () => null }) },
    ScriptApp: { getOAuthToken: () => 'TEST', getProjectTriggers: () =>
      [{ getHandlerFunction: () => 'relayGoogleMailRequests' }] },
    LockService: { getScriptLock: () => ({ tryLock: () => true, releaseLock: () => {} }) },
    MailApp: {
      getRemainingDailyQuota: () => quota,
      sendEmail: email => {
        if (failBusiness && email.to === 'caro.resanavettephoenix@gmail.com') {
          throw new Error('simulated Gmail outage');
        }
        if (quota < 1) throw new Error('quota exhausted');
        quota--;
        sent.push(email);
      }
    },
    UrlFetchApp: {
      fetch: (url, args) => {
        if (url.includes(':runQuery')) {
          const query = JSON.parse(args.payload).structuredQuery;
          const [filter] = [query.where.fieldFilter];
          const found = docs.filter(document =>
            document.fields[filter.field.fieldPath]?.stringValue === filter.value.stringValue
          );
          return response(200, found.slice(0, query.limit).map(document =>
            ({ document: JSON.parse(JSON.stringify(document)) })));
        }
        if (args.method === 'patch') {
          const parsed = new URL(url);
          const doc = docs.find(document => url.startsWith('https://firestore.googleapis.com/v1/' + document.name + '?'));
          if (!doc) return response(404, { error: { status: 'NOT_FOUND' } });
          if (parsed.searchParams.get('currentDocument.updateTime') !== doc.updateTime) {
            return response(412, { error: { status: 'FAILED_PRECONDITION' } });
          }
          const patch = JSON.parse(args.payload).fields;
          if (failFinalPatch && patch.mailStatus?.stringValue === 'SENT') {
            return response(503, { error: { status: 'UNAVAILABLE' } });
          }
          Object.assign(doc.fields, patch);
          updates++;
          doc.updateTime = '2026-11-23T09:' + String(updates).padStart(2, '0') + ':00.000Z';
          return response(200, JSON.parse(JSON.stringify(doc)));
        }
        throw new Error('unexpected URL: ' + url);
      }
    },
    Utilities: {
      formatDate: date => {
        const parts = new Intl.DateTimeFormat('en-GB', {
          timeZone: 'Europe/Paris', year: 'numeric', month: '2-digit', day: '2-digit',
          hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
        }).formatToParts(date);
        const get = type => parts.find(part => part.type === type).value;
        return get('year') + '-' + get('month') + '-' + get('day') + ' ' +
          get('hour') + ':' + get('minute');
      }
    },
    console: { log: msg => logs.push(msg), warn: msg => logs.push(msg), error: msg => logs.push(msg) }
  };
  // Activation uniquement dans l'environnement de tests, jamais dans Code.gs.
  const source = customer
    ? code.replace('const ENABLE_CUSTOMER_RECEIPTS_ = false;', 'const ENABLE_CUSTOMER_RECEIPTS_ = true;')
    : code;
  const names = Object.keys(mocks);
  const api = new Function(...names, source + '\nreturn {' +
    'relayGoogleMailRequests, verifierActivationAccuseClient, testerAccuseClientSansEnvoi,' +
    'diagnostiquerNavettePhoenix, customerReceiptMessage_};')(...Object.values(mocks));
  return { api, docs, sent, logs, remaining: () => quota };
}

function test(name, fn) {
  fn();
  process.stdout.write('OK  ' + name + '\n');
}

test('Test sans envoi et préflight', () => {
  const h = harness();
  assert.equal(h.api.testerAccuseClientSansEnvoi(), true);
  const result = h.api.verifierActivationAccuseClient();
  assert.equal(result.technicalPreflightOk, true);
  assert.equal(result.automaticCustomerMailEnabled, false);
  assert.equal(h.sent.length, 0);
});
test('Envoi Phoenix LTS unique, mode actuel', () => {
  const h = harness();
  h.api.relayGoogleMailRequests();
  h.api.relayGoogleMailRequests();
  assert.equal(h.sent.length, 1);
  assert.equal(h.docs[0].fields.mailStatus.stringValue, 'SENT');
  assert.equal(h.docs[0].fields.customerReceiptStatus, undefined);
});
test('Accusé client unique avec une réserve de quota', () => {
  const h = harness({ customer: true });
  h.api.relayGoogleMailRequests();
  h.api.relayGoogleMailRequests();
  assert.equal(h.sent.length, 2);
  assert.equal(h.sent[0].to, 'caro.resanavettephoenix@gmail.com');
  assert.equal(h.sent[1].to, 'client@example.test');
  assert.match(h.sent[1].body, /EN ATTENTE DE CONFIRMATION/);
  assert.equal(h.docs[0].fields.mailStatus.stringValue, 'SENT');
  assert.equal(h.docs[0].fields.customerReceiptStatus.stringValue, 'SENT');
});
test('Si Gmail échoue, SENDING empêche un second envoi automatique', () => {
  const h = harness({ failBusiness: true });
  h.api.relayGoogleMailRequests();
  h.api.relayGoogleMailRequests();
  assert.equal(h.sent.length, 0);
  assert.equal(h.docs[0].fields.mailStatus.stringValue, 'SENDING');
});
test('Si Firestore échoue après Gmail, ne pas renvoyer le courriel', () => {
  const h = harness({ failFinalPatch: true });
  assert.throws(() => h.api.relayGoogleMailRequests(), /Firestore HTTP 503/);
  h.api.relayGoogleMailRequests();
  assert.equal(h.sent.length, 1);
  assert.equal(h.docs[0].fields.mailStatus.stringValue, 'SENDING');
});
test('Quota un destinataire : aucun nouvel envoi double', () => {
  const h = harness({ customer: true, quota: 1 });
  h.api.relayGoogleMailRequests();
  assert.equal(h.sent.length, 0);
  assert.equal(h.docs[0].fields.mailStatus.stringValue, 'PENDING');
});
test('Diagnostic lecture seule sans dévoiler les contacts', () => {
  const h = harness();
  const report = h.api.diagnostiquerNavettePhoenix();
  assert.equal(report.countsFirst100.PENDING, 1);
  assert.equal(h.sent.length, 0);
  assert.equal(JSON.stringify(report).includes('client@example.test'), false);
});
