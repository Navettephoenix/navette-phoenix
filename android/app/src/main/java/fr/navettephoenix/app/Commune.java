package fr.navettephoenix.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class Commune {
    final String name;
    final String sector;
    final double metres;
    final boolean needsReview;

    private Commune(String name, String sector, double metres, boolean needsReview) {
        this.name = name;
        this.sector = sector;
        this.metres = metres;
        this.needsReview = needsReview;
    }

    static List<Commune> load(InputStream input) throws IOException {
        List<Commune> communes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(";", -1);
                if (fields.length != 7) throw new IOException("Ligne de commune invalide");
                double metres = Double.parseDouble(fields[3]);
                boolean needsReview = !Integer.toString(Fare.zone(metres)).equals(fields[6]);
                communes.add(new Commune(fields[2], fields[0], metres, needsReview));
            }
        }
        if (communes.isEmpty()) throw new IOException("Liste vide");
        return communes;
    }
}
