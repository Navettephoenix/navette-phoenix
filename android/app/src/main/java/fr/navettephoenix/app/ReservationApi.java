package fr.navettephoenix.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class ReservationApi {
    static URL configuredEndpoint() throws IOException {
        URL endpoint = new URL(BuildConfig.RESERVATION_URL.trim());
        if (!"https".equals(endpoint.getProtocol()) || endpoint.getHost().isEmpty()
                || endpoint.getUserInfo() != null) {
            throw new IOException("Adresse du service de réservation invalide");
        }
        return endpoint;
    }

    static String submit(URL endpoint, JSONObject request) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(data);
            }
            int code = connection.getResponseCode();
            if (code == 400 || code == 404 || code == 422) throw new Rejected();
            if (code != 201 && code != 200) throw new IOException("Réponse inattendue");
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (body.size() + n > 65536) throw new IOException("Réponse trop longue");
                    body.write(buffer, 0, n);
                }
                JSONObject reservation = new JSONObject(body.toString("UTF-8")).getJSONObject("reservation");
                String reference = reservation.getString("reservationId").trim();
                if (reference.isEmpty() || !request.getString("clientId").equals(reservation.getString("clientId"))) {
                    throw new IOException("Référence invalide");
                }
                return reference;
            }
        } finally {
            connection.disconnect();
        }
    }

    static final class Rejected extends IOException {}
}
