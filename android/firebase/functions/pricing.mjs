// Mirror of the official PricingCalculator.kt tariff zones for server-side validation.
const sectors = {
  FORETS_DU_PERCHE: [
    ['Senonches'],
    ['Digny', 'La Framboisière', 'La Chapelle-Fortin'],
    ['Jaudrais', 'Lamblore', 'La Puisaye', 'Le Mesnil-Thomas', 'Les Ressuintes'],
    ['Boissy-lès-Perche', 'Louvilliers-lès-Perche', 'La Ferté-Vidame', 'La Saucelle', 'Rohaire'],
  ],
  TERRES_DE_PERCHE: [
    ['La Loupe'],
    ['Meaucé', 'Saint-Éliph', 'Fontaine-Simon', 'Vaupillon', 'Saint-Maurice-Saint-Germain', 'Les Corvées-les-Yys'],
    ['Belhomert-Guéhouville', 'Champrond-en-Gâtine', 'Manou', 'Montireau', 'Montlandon', 'Saint-Victor-de-Buthon'],
    ['Chassant', 'Combres', 'Frazé', 'Happonvilliers', 'La Croix-du-Perche', 'Marolles-les-Buis',
      'Nonvilliers-Grandhoux', 'Saintigny', 'Thiron-Gardais'],
  ],
};

export function normalizeCommune(value) {
  return String(value).normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]/g, '');
}

const index = new Map(Object.entries(sectors).flatMap(([sector, levels]) =>
  levels.flatMap((names, index) =>
    names.map(name => [normalizeCommune(name), { sector, zone: index + 1 }]))
));

export function calculatePrice(departureCommune, arrivalCommune, roundTrip, passengers) {
  if (!Number.isInteger(passengers) || passengers < 1 || passengers > 4) {
    throw new Error('Nombre de passagers invalide');
  }
  const first = index.get(normalizeCommune(departureCommune));
  const second = index.get(normalizeCommune(arrivalCommune));
  if (!first || !second || first.sector !== second.sector) {
    return { pricingType: 'QUOTE', pricingSector: '', pricingZone: 0,
      pricePerPassenger: 0, priceOneWay: 0, priceTotal: 0 };
  }
  const pricingZone = Math.max(first.zone, second.zone);
  const pricePerPassenger = [5, 6, 8, 10][pricingZone - 1];
  const priceOneWay = pricePerPassenger * passengers;
  return { pricingType: 'FIXED', pricingSector: first.sector, pricingZone,
    pricePerPassenger, priceOneWay, priceTotal: priceOneWay * (roundTrip ? 2 : 1) };
}
