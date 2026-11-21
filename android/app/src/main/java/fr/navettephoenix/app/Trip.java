package fr.navettephoenix.app;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class Trip {
    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", Locale.FRANCE);

    static String validate(String departure, String destination, LocalDateTime when,
                           LocalDateTime now) {
        if (departure.trim().isEmpty() || destination.trim().isEmpty()) {
            return "Renseignez le départ et la destination.";
        }
        if (departure.trim().equalsIgnoreCase(destination.trim())) {
            return "Choisissez deux lieux différents.";
        }
        if (when == null || !when.isAfter(now)) {
            return "Choisissez une date et une heure à venir.";
        }
        return null;
    }

    static String summary(String departure, String destination, LocalDateTime when) {
        return "Départ · " + departure.trim()
                + "\nDestination · " + destination.trim()
                + "\nDate · " + when.format(DISPLAY) + " (heure de Paris)";
    }
}
