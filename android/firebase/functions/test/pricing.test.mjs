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
