function relayGoogleMailRequests() {
  const properties = PropertiesService.getScriptProperties();
  const project = properties.getProperty('FIREBASE_PROJECT_ID') || '';
  const recipient = (properties.getProperty('RESERVATION_MAIL_TO') || '').trim();
  if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(project) || !mailAddress_(recipient)) {
    throw new Error('Configurez le projet Firebase et la boîte de réservation dans les propriétés du script.');
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
          replyTo: message.replyTo, name: 'Navette Phoenix 28'
        });
      } catch (_) {
        console.warn('Transmission à vérifier dans Firestore pour une demande en état SENDING.');
        continue;
      }
      updateMailState_(claimed, 'SENT', token);
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
      'Nom : ' + text('name'),
      'Téléphone : ' + text('phone'),
      'E-mail : ' + email,
      text('needs') ? 'Besoins particuliers : ' + text('needs') : '',
      text('notes') ? 'Commentaire : ' + text('notes') : '',
      'Cette demande attend la validation de Phoenix LTS.'
    ].filter(Boolean).join('\n')
  };
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
  return firestore_(document.name + '?updateMask.fieldPaths=mailStatus&currentDocument.updateTime='
    + encodeURIComponent(document.updateTime), 'patch', token, {
    fields: { mailStatus: { stringValue: state } }
  }, true);
}

function firestore_(path, method, token, body, allowConflict) {
  const response = UrlFetchApp.fetch('https://firestore.googleapis.com/v1/' + path, {
    method: method, contentType: 'application/json', payload: JSON.stringify(body),
    headers: { Authorization: 'Bearer ' + token }, muteHttpExceptions: true,
    followRedirects: false
  });
  const code = response.getResponseCode();
  if (code < 200 || code >= 300) {
    if (allowConflict && [404, 409, 412].includes(code)) return null;
    if (allowConflict && code === 400 &&
        JSON.parse(response.getContentText()).error.status === 'FAILED_PRECONDITION') return null;
    throw new Error('Firestore a renvoyé une erreur HTTP ' + code + '.');
  }
  return JSON.parse(response.getContentText());
}

/**
 * Diagnostic non destructif : ne transmet aucun e-mail et ne modifie aucune demande.
 * Exécuter depuis l'éditeur Apps Script avec le compte qui possède le déclencheur.
 */
function diagnostiquerNavettePhoenix() {
  const properties = PropertiesService.getScriptProperties();
  const project = (properties.getProperty('FIREBASE_PROJECT_ID') || '').trim();
  const recipient = (properties.getProperty('RESERVATION_MAIL_TO') || '').trim();
  if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(project) || !mailAddress_(recipient)) {
    throw new Error('Propriétés FIREBASE_PROJECT_ID ou RESERVATION_MAIL_TO manquantes / invalides.');
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
