package fr.navettephoenix.app;

final class Fare {
    private static final int[] LIMITS_METRES = {3000, 8000, 13000, 20000};
    private static final int[] PRICES_EUROS = {5, 6, 8, 10};

    private Fare() {}

    static int zone(double metres) {
        for (int i = 0; i < LIMITS_METRES.length; i++) {
            if (metres <= LIMITS_METRES[i]) return i + 1;
        }
        return 0;
    }

    static int estimate(double departureMetres, double arrivalMetres, int passengers,
                        boolean returnTrip) {
        int zone = zone(Math.max(departureMetres, arrivalMetres));
        if (zone == 0) return 0;
        return PRICES_EUROS[zone - 1] * passengers * (returnTrip ? 2 : 1);
    }
}
