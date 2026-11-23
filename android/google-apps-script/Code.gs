// Boîte officielle des réservations Phoenix LTS (ne dépend pas des propriétés privées).
const RESERVATION_MAIL_TO_ = 'caro.resanavettephoenix@gmail.com';
/**
 * Activation uniquement APRÈS vérification du quota, de la confidentialité et de l'index.
 * false : le flux existant Phoenix LTS reste STRICTEMENT inchangé.
 * Les nouvelles demandes, et non l'historique, recevront un accusé après transmission.
 */
const ENABLE_CUSTOMER_RECEIPTS_ = false;

function relayGoogleMailRequests() {
  const properties = PropertiesService.getScriptProperties();
  const project = (properties.getProperty('FIREBASE_PROJECT_ID') || 'navette-phoenix').trim();
  const recipient = RESERVATION_MAIL_TO_;
  if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(project) || !mailAddress_(recipient)) {
    throw new Error('Identifiant du projet Firebase invalide.');
  }

  // Un seul déclencheur traite la file. Le verrou se libère à la fin de l'exécution.
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) return;
  try {
    const limit = Math.min(10, MailApp.getRemainingDailyQuota());
    if (limit < 1) return;
    const token = ScriptApp.getOAuthToken();
    const parent = 'projects/' + project + '/databases/(default)/documents';
    const rows = firestore_(parent + ':runQuery', 'post', token, {
      structuredQuery: {
        from: [{ collectionId: 'googleMailRequests', allDescendants: true }],
        where: { fieldFilter: {
          field: { fieldPath: 'mailStatus' }, op: 'EQUAL', value: { stringValue: 'PENDING' }
        } },
        orderBy: [{ field: { fieldPath: 'createdAt' }, direction: 'ASCENDING' }],
        limit: limit
      }
    });

    for (const row of rows) {
      const document = row.document;
      if (!document || !document.name.startsWith(parent + '/googleMailAccounts/')) continue;
      const path = document.name.slice(parent.length + 1).split('/');
      if (path.length !== 4 || path[2] !== 'googleMailRequests') continue;
      const message = bookingMessage_(document, path[3]);
      if (!message) {
        updateMailState_(document, 'INVALID', token);
        continue;
      }

      // Firestore confirme le verrou avant que Google reçoive l'ordre d'envoi.
      // SENDING reste à vérifier si Google répond par une erreur ou si la tâche est interrompue.
      const claimed = updateMailState_(document, 'SENDING', token);
      if (!claimed) continue;
      try {
        MailApp.sendEmail({
          to: recipient, subject: message.subject, body: message.body,
          htmlBody: message.htmlBody,
          replyTo: message.replyTo, name: 'Navette Phoenix 28'
        });
      } catch (_) {
        console.warn('Transmission à vérifier dans Firestore pour une demande en état SENDING.');
        continue;
      }
      updateMailState_(claimed, 'SENT', token);
    }
    if (ENABLE_CUSTOMER_RECEIPTS_) {
      relayCustomerReceipts_(parent, token);
    }
  } finally {
    lock.releaseLock();
  }
}

function mailAddress_(value) {
  return /^[^@\s,;<>]+@[^@\s,;<>]+\.[^@\s,;<>]+$/.test(value);
}

function bookingMessage_(document, id) {
  const fields = document.fields || {};
  const text = name => ((fields[name] || {}).stringValue || '').trim();
  const integer = name => Number((fields[name] || {}).integerValue);
  const email = text('email');
  const departure = text('departure');
  const arrival = text('arrival');
  const date = text('date');
  const time = text('time');
  const returnDate = text('returnDate');
  const returnTime = text('returnTime');
  const passengers = integer('passengers');
  const luggage = integer('luggage');
  const opened = Date.parse((fields.createdAt || {}).timestampValue);
  const pickup = parisTime_(date, time);
  if (!/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(id)
      || text('requestId') !== id || !mailAddress_(email)
      || !['NAVETTE', 'DEVIS'].includes(text('service'))
      || departure.length < 3 || arrival.length < 3
      || departure.localeCompare(arrival, 'fr', { sensitivity: 'accent' }) === 0
      || !/^[+0-9 ().-]{7,24}$/.test(text('phone'))
      || !/^\d{8,15}$/.test(text('phone').replace(/\D/g, ''))
      || text('name').length < 2 || (fields.consent || {}).booleanValue !== true
      || !Number.isInteger(passengers) || passengers < 1 || passengers > 4
      || !Number.isInteger(luggage) || luggage < 0 || luggage > 16
      || !(pickup > opened)
      || ((returnDate || returnTime) && !(parisTime_(returnDate, returnTime) > pickup))) {
    return null;
  }
  // Les anciens envois ne contiennent pas de prix ; les nouvelles estimations
  // sont recalculées ici, sans faire confiance au montant écrit par le téléphone.
  const proposed = text('pricingType');
  let pricing = null;
  if (proposed) {
    pricing = bookingPrice_(text('departureCommune'), text('arrivalCommune'),
      Boolean(returnDate), passengers);
    const claimedZone = integer('pricingZone');
    const claimedPrice = integer('pricePerPassenger');
    const claimedOneWay = integer('priceOneWay');
    const claimedTotal = integer('priceTotal');
    const claimedSector = text('pricingSector');
    if (proposed !== (pricing ? 'FIXED' : 'QUOTE')
        || claimedZone !== (pricing ? pricing.zone : 0)
        || claimedPrice !== (pricing ? pricing.price : 0)
        || claimedOneWay !== (pricing ? pricing.oneWay : 0)
        || claimedTotal !== (pricing ? pricing.total : 0)
        || claimedSector !== (pricing ? pricing.sector : '')) return null;
  }
  const reference = 'NP-' + id.replace(/-/g, '').slice(0, 16).toUpperCase();
  const title = text('service') === 'DEVIS' ? 'Demande de devis' : 'Demande de navette';
  return {
    subject: 'Navette Phoenix 28 · ' + title + ' · ' + reference,
    replyTo: email,
    body: [
      title + ' à traiter — ' + reference,
      'Départ : ' + departure,
      'Arrivée : ' + arrival,
      'Aller (heure de Paris) : ' + date + ' à ' + time,
      returnDate ? 'Retour (heure de Paris) : ' + returnDate + ' à ' + returnTime : 'Aller simple',
      'Passagers : ' + passengers + ' · Bagages : ' + luggage,
      proposed ? (pricing
        ? 'Tarif estimé : ' + pricing.price + ' € par passager · zone ' + pricing.zone +
          ' (' + (pricing.sector === 'FORETS_DU_PERCHE' ? 'Forêts du Perche' : 'Terres de Perche') +
          ') · ' + pricing.oneWay + ' € le trajet · ' + pricing.total + ' € au total'
        : 'Tarif sur devis') : '',
      proposed ? 'Tarif estimé, confirmé par le service de réservation.' : '',
      'Nom : ' + text('name'),
      'Téléphone : ' + text('phone'),
      'E-mail : ' + email,
      text('needs') ? 'Besoins particuliers : ' + text('needs') : '',
      text('notes') ? 'Commentaire : ' + text('notes') : '',
      'Cette demande attend la validation de Phoenix LTS.'
    ].filter(Boolean).join('\n'),
    htmlBody: bookingHtml_(title, reference, {
      departure, arrival, date, time, returnDate, returnTime, passengers, luggage,
      pricing, proposed, name: text('name'), phone: text('phone'), email,
      needs: text('needs'), notes: text('notes')
    })
  };
}


/** Présentation HTML lisible dans Gmail, avec version texte conservée. */
function bookingHtml_(title, reference, b) {
  const esc = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  const item = (label, value) => '<tr><td style="padding:10px 12px;color:#75665e;vertical-align:top;width:125px;font-size:13px">'
    + esc(label) + '</td><td style="padding:10px 12px;color:#241b17;font-weight:600;font-size:14px;overflow-wrap:anywhere">'
    + esc(value) + '</td></tr>';
  const section = (title, rows) => '<section style="margin:18px 0;background:#fff;border:1px solid #eadbd2;border-radius:14px;overflow:hidden">'
    + '<h2 style="margin:0;padding:15px 16px 8px;font-size:16px;color:#241b17">' + esc(title) + '</h2>'
    + '<table role="presentation" cellpadding="0" cellspacing="0" style="width:100%;border-collapse:collapse">'
    + rows.join('') + '</table></section>';
  const price = b.proposed ? (b.pricing
    ? b.pricing.total + ' € au total (estimation, zone ' + b.pricing.zone + ')'
    : 'Sur devis') : 'À confirmer';
  const route = section('Trajet', [
    item('Départ', b.departure), item('Arrivée', b.arrival),
    item('Aller', b.date + ' à ' + b.time + ' (heure de Paris)'),
    ...(b.returnDate ? [item('Retour', b.returnDate + ' à ' + b.returnTime + ' (heure de Paris)')] : [])
  ]);
  const passengers = section('Passagers et tarif', [
    item('Passagers', b.passengers), item('Bagages', b.luggage), item('Tarif', price)
  ]);
  const contact = section('Contact', [
    item('Nom', b.name), item('Téléphone', b.phone), item('E-mail', b.email),
    ...(b.needs ? [item('Besoins', b.needs)] : []),
    ...(b.notes ? [item('Commentaires', b.notes)] : [])
  ]);
  return '<!doctype html><html lang="fr"><body style="margin:0;padding:28px 12px;background:#f8f4f0;font-family:Arial,Helvetica,sans-serif">'
    + '<main style="max-width:620px;margin:auto">'
    + '<header style="padding:24px;background:#4b281d;border-radius:18px 18px 0 0;color:white">'
    + '<div style="font-size:13px;letter-spacing:1.5px;color:#ffd5c0">PHOENIX LTS</div>'
    + '<h1 style="margin:8px 0;font-size:25px">Navette Phoenix 28</h1>'
    + '<div style="font-size:15px">' + esc(title) + '</div></header>'
    + '<div style="background:#fffaf6;padding:20px;border:1px solid #eadbd2;border-radius:0 0 18px 18px">'
    + '<div style="padding:12px 14px;border-radius:10px;background:#fff0e8;color:#9d3212;font-weight:700">'
    + 'À TRAITER · RÉSERVATION NON CONFIRMÉE</div>'
    + '<p style="color:#51433c;font-size:14px">Référence : <strong>' + esc(reference) + '</strong></p>'
    + route + passengers + contact
    + '<p style="font-size:13px;line-height:1.6;color:#675950">Le tarif est indicatif. La demande nécessite une validation par le service de réservation.</p>'
    + '</div></main></body></html>';
}

function parisTime_(date, time) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time)) return NaN;
  const utc = Date.parse(date + 'T' + time + ':00Z');
  if (!Number.isFinite(utc)) return NaN;
  const matches = [1, 2].map(hours => utc - hours * 3600000).filter(value =>
    Utilities.formatDate(new Date(value), 'Europe/Paris', 'yyyy-MM-dd HH:mm') === date + ' ' + time);
  return matches.length === 1 ? matches[0] : NaN;
}

function updateMailState_(document, state, token) {
  if (!document.updateTime) throw new Error('Version de demande Firestore absente.');
  // Seules les nouvelles demandes traitées APRÈS activation seront éligibles.
  const prepareReceipt = ENABLE_CUSTOMER_RECEIPTS_ && state === 'SENT';
  const fields = { mailStatus: { stringValue: state } };
  let mask = 'updateMask.fieldPaths=mailStatus';
  if (prepareReceipt) {
    fields.customerReceiptStatus = { stringValue: 'PENDING' };
    mask += '&updateMask.fieldPaths=customerReceiptStatus';
  }
  return firestore_(document.name + '?' + mask + '&currentDocument.updateTime='
    + encodeURIComponent(document.updateTime), 'patch', token, { fields }, true);
}

/** Ne réessaie jamais automatiquement un accusé SENDING : vérifier Gmail d'abord. */
function updateCustomerReceiptState_(document, state, token) {
  if (!document.updateTime) throw new Error('Version de demande Firestore absente.');
  return firestore_(document.name + '?updateMask.fieldPaths=customerReceiptStatus&currentDocument.updateTime='
    + encodeURIComponent(document.updateTime), 'patch', token, {
    fields: { customerReceiptStatus: { stringValue: state } }
  }, true);
}

/**
 * Envoi facultatif APRÈS acceptation du courriel destiné à Phoenix LTS.
 * Deux étapes Firestore SENDING/SENT limitent les doublons après interruption.
 * Une demande en SENDING reste à contrôler manuellement, jamais à réenvoyer à l'aveugle.
 */
function relayCustomerReceipts_(parent, token) {
  const limit = Math.min(10, MailApp.getRemainingDailyQuota());
  if (limit < 1) return;
  const rows = firestore_(parent + ':runQuery', 'post', token, {
    structuredQuery: {
      from: [{ collectionId: 'googleMailRequests', allDescendants: true }],
      where: { fieldFilter: {
        field: { fieldPath: 'customerReceiptStatus' }, op: 'EQUAL',
        value: { stringValue: 'PENDING' }
      } },
      limit
    }
  });
  for (const row of rows) {
    if (MailApp.getRemainingDailyQuota() < 1) break;
    const document = row.document;
    if (!document || !document.name.startsWith(parent + '/googleMailAccounts/')) continue;
    const path = document.name.slice(parent.length + 1).split('/');
    if (path.length !== 4 || path[2] !== 'googleMailRequests') continue;
    if ((document.fields.mailStatus || {}).stringValue !== 'SENT') continue;
    const message = customerReceiptMessage_(document, path[3]);
    if (!message) {
      updateCustomerReceiptState_(document, 'INVALID', token);
      continue;
    }
    const claimed = updateCustomerReceiptState_(document, 'SENDING', token);
    if (!claimed) continue;
    try {
      MailApp.sendEmail({
        to: message.to, subject: message.subject, body: message.body,
        htmlBody: message.htmlBody,
        replyTo: RESERVATION_MAIL_TO_, name: 'Navette Phoenix 28'
      });
    } catch (_) {
      console.warn('Accusé client à vérifier : statut SENDING conservé, aucun renvoi automatique.');
      continue;
    }
    updateCustomerReceiptState_(claimed, 'SENT', token);
  }
}

/** Le client reçoit un accusé de demande, jamais une confirmation de trajet. */
function customerReceiptMessage_(document, id) {
  const fields = document.fields || {};
  const text = name => String((fields[name] || {}).stringValue || '').trim();
  const to = text('email');
  if (!mailAddress_(to) || !/^[a-f0-9-]{36}$/.test(id)) return null;
  const reference = 'NP-' + id.replace(/-/g, '').slice(0, 16).toUpperCase();
  const service = text('service') === 'DEVIS' ? 'devis' : 'navette';
  const lines = [
    'Votre demande ' + service + ' a été transmise à Navette Phoenix 28.',
    'Référence : ' + reference,
    'Départ : ' + text('departure'),
    'Arrivée : ' + text('arrival'),
    'Date et heure : ' + text('date') + ' à ' + text('time') + ' (heure de Paris)',
    'Cette demande est EN ATTENTE DE CONFIRMATION par Phoenix LTS.',
    'Pour toute question, répondez à cet e-mail.'
  ];
  const escape = value => String(value).replace(/&/g, '&amp;')
    .replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const htmlBody = '<div style="font:16px Arial,sans-serif;max-width:600px;margin:auto;color:#241b17">'
    + '<div style="background:#4b281d;color:white;padding:22px;border-radius:12px 12px 0 0">'
    + '<strong>Navette Phoenix 28</strong></div>'
    + '<div style="background:#fffaf6;padding:22px;border:1px solid #eadbd2">'
    + '<h2>Demande reçue par notre service</h2>'
    + '<p style="color:#9b2a0d;font-weight:bold">EN ATTENTE DE CONFIRMATION</p>'
    + '<p>Référence : <strong>' + escape(reference) + '</strong></p>'
    + '<p>Départ : ' + escape(text('departure')) + '</p>'
    + '<p>Arrivée : ' + escape(text('arrival')) + '</p>'
    + '<p>Aller : ' + escape(text('date') + ' à ' + text('time')) + ' (heure de Paris)</p>'
    + '<p>Le trajet n’est confirmé qu’après validation explicite par Phoenix LTS.</p>'
    + '<p>Vous pouvez répondre à cet e-mail pour nous contacter.</p>'
    + '</div></div>';
  return {
    to,
    subject: 'Navette Phoenix 28 · Demande transmise · ' + reference,
    body: lines.join('\n'),
    htmlBody
  };
}

function firestore_(path, method, token, body, allowConflict) {
  const response = UrlFetchApp.fetch('https://firestore.googleapis.com/v1/' + path, {
    method: method, contentType: 'application/json', payload: JSON.stringify(body),
    headers: { Authorization: 'Bearer ' + token }, muteHttpExceptions: true,
    followRedirects: false
  });
  const code = response.getResponseCode();
  if (code < 200 || code >= 300) {
    const details = (() => {
      try { return JSON.parse(response.getContentText()).error || {}; }
      catch (_) { return {}; }
    })();
    if (allowConflict && ([404, 409, 412].includes(code) ||
        (code === 400 && details.status === 'FAILED_PRECONDITION'))) return null;
    // Exposer la réponse officielle Firestore sans jeton OAuth ni données personnelles.
    // Une erreur FAILED_PRECONDITION signale souvent un index COLLECTION_GROUP manquant.
    const message = String(details.message || 'Vérifier les index et les autorisations Firestore.')
      .slice(0, 1200);
    throw new Error('Firestore HTTP ' + code + ' (' +
      (details.status || 'ERREUR') + ') : ' + message);
  }
  return JSON.parse(response.getContentText());
}

/**
 * Diagnostic non destructif : ne transmet aucun e-mail et ne modifie aucune demande.
 * Exécuter depuis l'éditeur Apps Script avec le compte qui possède le déclencheur.
 */
function diagnostiquerNavettePhoenix() {
  const properties = PropertiesService.getScriptProperties();
  const project = (properties.getProperty('FIREBASE_PROJECT_ID') || 'navette-phoenix').trim();
  const recipient = RESERVATION_MAIL_TO_;
  if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(project) || !mailAddress_(recipient)) {
    throw new Error('Projet Firebase ou adresse de réception invalides.');
  }
  const triggers = ScriptApp.getProjectTriggers()
    .filter(trigger => trigger.getHandlerFunction() === 'relayGoogleMailRequests');
  const parent = 'projects/' + project + '/databases/(default)/documents';
  const counts = {};
  const token = ScriptApp.getOAuthToken();
  for (const status of ['PENDING', 'SENDING', 'SENT', 'INVALID']) {
    const response = firestore_(parent + ':runQuery', 'post', token, {
      structuredQuery: {
        from: [{ collectionId: 'googleMailRequests', allDescendants: true }],
        where: { fieldFilter: {
          field: { fieldPath: 'mailStatus' }, op: 'EQUAL', value: { stringValue: status }
        } },
        // Même index composite que le relais : mailStatus + createdAt ASC.
        // Sans tri, Firestore peut exiger un index COLLECTION_GROUP distinct.
        orderBy: [{ field: { fieldPath: 'createdAt' }, direction: 'ASCENDING' }],
        limit: 100
      }
    });
    // Le diagnostic compte les 100 premières demandes par statut, sans exposer les coordonnées.
    counts[status] = response.filter(row => row.document).length;
  }
  const report = {
    project: project,
    recipientConfigured: true,
    activeRelayTriggers: triggers.length,
    remainingDailyMailQuota: MailApp.getRemainingDailyQuota(),
    countsFirst100: counts,
    action: triggers.length === 0
      ? 'Installer le déclencheur relayGoogleMailRequests (toutes les cinq minutes).'
      : counts.SENDING > 0
        ? 'Vérifier la réception et les exécutions Google avant toute relance manuelle des demandes SENDING.'
        : counts.PENDING > 0
          ? 'Exécuter relayGoogleMailRequests puis consulter Mes demandes et la boîte de réservation.'
          : 'Aucune demande en attente parmi les 100 premières par statut.'
  };
  console.log(JSON.stringify(report));
  return report;
}

/** Même grille par commune que PricingCalculator.kt, recalculée côté relais. */
function bookingPrice_(departure, arrival, returnTrip, passengers) {
  const normalized = value => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]/g, '');
  const zones = [
    ['FORETS_DU_PERCHE', [
      ['Senonches'],
      ['Digny', 'La Framboisière', 'La Chapelle-Fortin'],
      ['Jaudrais', 'Lamblore', 'La Puisaye', 'Le Mesnil-Thomas', 'Les Ressuintes'],
      ['Boissy-lès-Perche', 'Louvilliers-lès-Perche', 'La Ferté-Vidame', 'La Saucelle', 'Rohaire']
    ]],
    ['TERRES_DE_PERCHE', [
      ['La Loupe'],
      ['Meaucé', 'Saint-Éliph', 'Fontaine-Simon', 'Vaupillon',
        'Saint-Maurice-Saint-Germain', 'Les Corvées-les-Yys'],
      ['Belhomert-Guéhouville', 'Champrond-en-Gâtine', 'Manou', 'Montireau',
        'Montlandon', 'Saint-Victor-de-Buthon'],
      ['Chassant', 'Combres', 'Frazé', 'Happonvilliers', 'La Croix-du-Perche',
        'Marolles-les-Buis', 'Nonvilliers-Grandhoux', 'Saintigny', 'Thiron-Gardais']
    ]]
  ];
  function find(name) {
    for (const [sector, levels] of zones) {
      for (let index = 0; index < levels.length; index++) {
        if (levels[index].some(value => normalized(value) === normalized(name))) {
          return { sector, zone: index + 1 };
        }
      }
    }
    return null;
  }
  const first = find(departure);
  const last = find(arrival);
  if (!first || !last || first.sector !== last.sector) return null;
  const zone = Math.max(first.zone, last.zone);
  const price = [5, 6, 8, 10][zone - 1];
  const oneWay = price * passengers;
  return { zone, sector: first.sector, price, oneWay, total: oneWay * (returnTrip ? 2 : 1) };
}
