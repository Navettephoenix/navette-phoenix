import assert from 'node:assert/strict';
import { test } from 'node:test';
import { calculatePrice, normalizeCommune } from '../pricing.mjs';

test('forêts du Perche : 1, 3 passagers et aller-retour', () => {
  assert.deepEqual(calculatePrice('Digny', 'Senonches', false, 1), {
    pricingType: 'FIXED', pricingSector: 'FORETS_DU_PERCHE', pricingZone: 2,
    pricePerPassenger: 6, priceOneWay: 6, priceTotal: 6,
  });
  assert.equal(calculatePrice('Digny', 'Senonches', false, 3).priceTotal, 18);
  assert.equal(calculatePrice('Digny', 'Senonches', true, 3).priceTotal, 36);
});

test('terres du Perche : Saint-Éliph → Fontaine-Simon', () => {
  const simple = calculatePrice('Saint-Éliph', 'Fontaine-Simon', false, 2);
  assert.equal(simple.priceTotal, 12);
  assert.equal(simple.pricingSector, 'TERRES_DE_PERCHE');
  assert.equal(calculatePrice('SAINT ELIPH', 'fontaine-simon', true, 2).priceTotal, 24);
});

test('trajets dans la même zone : prix de cette zone par passager', () => {
  const zone2 = calculatePrice('Digny', 'La Framboisière', false, 3);
  assert.equal(zone2.pricingZone, 2);
  assert.equal(zone2.pricePerPassenger, 6);
  assert.equal(zone2.priceTotal, 18);
  assert.equal(calculatePrice('Digny', 'La Chapelle-Fortin', true, 3).priceTotal, 36);

  const zone3 = calculatePrice('Jaudrais', 'Lamblore', false, 2);
  assert.equal(zone3.pricingZone, 3);
  assert.equal(zone3.pricePerPassenger, 8);
  assert.equal(zone3.priceTotal, 16);

  const zone4 = calculatePrice('La Ferté-Vidame', 'La Saucelle', true, 4);
  assert.equal(zone4.pricingZone, 4);
  assert.equal(zone4.pricePerPassenger, 10);
  assert.equal(zone4.priceOneWay, 40);
  assert.equal(zone4.priceTotal, 80);

  assert.equal(calculatePrice('Manou', 'Montireau', false, 3).priceTotal, 24);
  assert.equal(calculatePrice('Combres', 'Frazé', false, 2).priceTotal, 20);
  assert.equal(calculatePrice('Senonches', 'Senonches', false, 2).priceTotal, 10);
});

test('hors zone, entre secteurs et bagages non facturés', () => {
  assert.equal(calculatePrice('Digny', 'La Loupe', false, 2).pricingType, 'QUOTE');
  assert.equal(calculatePrice('Autre commune', 'La Loupe', false, 2).pricingType, 'QUOTE');
  assert.equal(calculatePrice('Ville inconnue', 'Senonches', false, 1).priceTotal, 0);
  assert.equal(normalizeCommune(' BOISSY-LÈS-PERCHE '), 'boissylesperche');
});

test('refuse les nombres de passagers invalides', () => {
  for (const number of [0, 5, -1, 2.4]) {
    assert.throws(() => calculatePrice('Digny', 'Senonches', false, number));
  }
});
