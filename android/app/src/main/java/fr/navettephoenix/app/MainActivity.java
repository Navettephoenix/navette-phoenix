package fr.navettephoenix.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.graphics.drawable.RippleDrawable;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String EMAIL = BuildConfig.BOOKING_EMAIL.trim();
    private static final String PHONE = BuildConfig.BOOKING_PHONE.trim();
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final int PAPER = 0xFFF7F5F0;
    private static final int INK = 0xFF152B25;
    private static final int GREEN = 0xFF173F35;

    private static final int MUTED = 0xFF4F625A;
    private static final int BORDER = 0xFFD8DFD8;
    private static final String HOME = "home";
    private static final String FORESTS = "Forêts du Perche";
    private static final String LANDS = "Terres de Perche";
    private static final String QUOTE = "Devis hors secteur";
    private static final String[] CATEGORIES = {"Aéroport", "Gare à Paris", "Autre destination"};
    private static final String[] STATIONS = {"Gare de La Loupe", "Gare de Verneuil-sur-Avre"};

    private SharedPreferences storage;
    private List<Commune> communes = new ArrayList<>();
    private Bundle drafts = new Bundle();
    private EditText passengerField, nameField, phoneField;
    private String name = "", phone = "";
    private String fromPlace = "";
    private String toPlace = "";
    private String passengers = "1";
    private String category = "";
    private String bookingScreen = FORESTS;
    private LinearLayout page;
    private EditText departureField;
    private EditText destinationField;
    private String departure = "";
    private String destination = "";
    private LocalDate date;
    private LocalTime time;
    private String screen = HOME;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        storage = getSharedPreferences("trips", MODE_PRIVATE);
        try {
            communes = Commune.load(getAssets().open("communes.csv"));
        } catch (IOException | IllegalArgumentException error) {
            message("La liste des communes est indisponible.");
        }
        if (state != null) {
            Bundle saved = state.getBundle("drafts");
            if (saved != null) drafts = saved;
            bookingScreen = state.getString("bookingScreen", FORESTS);
            restoreDraft(bookingScreen);
            screen = state.getString("screen", HOME);
        }
        show(screen);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        capture();
        saveDraft();
        state.putBundle("drafts", drafts);
        state.putString("screen", screen);
        state.putString("bookingScreen", bookingScreen);
    }

    private void capture() {
        if (departureField != null) {
            departure = departureField.getText().toString();
            destination = destinationField.getText().toString();
            name = nameField.getText().toString().trim();
            phone = phoneField.getText().toString().trim();
            passengers = passengerField.getText().toString();
        }
    }

    private void saveDraft() {
        Bundle draft = new Bundle();
        draft.putString("name", name);
        draft.putString("phone", phone);
        draft.putString("departure", departure);
        draft.putString("destination", destination);
        draft.putString("fromPlace", fromPlace);
        draft.putString("toPlace", toPlace);
        draft.putString("passengers", passengers);
        draft.putString("category", category);
        if (date != null) draft.putString("date", date.toString());
        if (time != null) draft.putString("time", time.toString());
        drafts.putBundle(bookingScreen, draft);
    }

    private void restoreDraft(String key) {
        Bundle saved = drafts.getBundle(key);
        Bundle draft = saved == null ? new Bundle() : saved;
        name = draft.getString("name", "");
        phone = draft.getString("phone", "");
        departure = draft.getString("departure", "");
        destination = draft.getString("destination", "");
        fromPlace = draft.getString("fromPlace", "");
        toPlace = draft.getString("toPlace", "");
        passengers = draft.getString("passengers", "1");
        category = draft.getString("category", "");
        date = draft.containsKey("date") ? LocalDate.parse(draft.getString("date")) : null;
        time = draft.containsKey("time") ? LocalTime.parse(draft.getString("time")) : null;
    }

    private void show(String next) {
        capture();
        departureField = null;
        destinationField = null;
        if ((FORESTS.equals(next) || LANDS.equals(next) || QUOTE.equals(next))
                && !bookingScreen.equals(next)) {
            saveDraft();
            bookingScreen = next;
            restoreDraft(next);
        }
        screen = next;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PAPER);
        scroll.setFillViewport(true);
        page = column();
        scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            page.setPadding(dp(22) + insets.getSystemWindowInsetLeft(),
                    dp(22) + insets.getSystemWindowInsetTop(),
                    dp(22) + insets.getSystemWindowInsetRight(),
                    dp(22) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        page.setFocusableInTouchMode(true);
        setContentView(scroll);
        page.requestFocus();

        if (HOME.equals(screen)) {
            home();
            return;
        }
        action(page, "‹  Accueil", false, () -> show(HOME));
        switch (screen) {
            case "history": history(); break;
            case "contact": contact(); break;
            default: booking();
        }
    }

    private void home() {
        LinearLayout header = column();
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF285A49, GREEN, 0xFF102B24}));
        ((GradientDrawable) header.getBackground()).setCornerRadius(dp(26));
        header.setPadding(dp(20), dp(20), dp(20), dp(18));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.np_logo_officiel);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackground(background(GREEN));
        logo.setClipToOutline(true);
        header.addView(logo, dp(58), dp(58));
        text(header, "Navette Phoenix", 31, Color.WHITE, true);
        text(header, "Transport à la demande dans le Perche", 15, 0xFFE1EDE5, false);
        page.addView(header);

        homeOption(FORESTS, "15 communes autour de Senonches", FORESTS);
        homeOption(LANDS, "22 communes autour de La Loupe", LANDS);
        homeOption(QUOTE, "Aéroports, gares à Paris et autres destinations", QUOTE);
        action(page, "Mes demandes", false, () -> show("history"));
        action(page, "Estimer mon tarif", false,
                () -> startActivity(new Intent(this, FareActivity.class)));
        action(page, "Contact", false, () -> show("contact"));
    }

    private void homeOption(String title, String detail, String target) {
        LinearLayout option = column();
        option.setPadding(dp(15), dp(9), dp(15), dp(9));
        option.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24173F35),
                outlined(Color.WHITE), null));
        option.setOnClickListener(view -> show(target));
        option.setFocusable(true);
        option.setContentDescription(title + ". " + detail);
        TextView name = text(option, title + "  ›", 18, INK, true);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        TextView description = text(option, detail, 14, MUTED, false);
        description.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(9);
        page.addView(option, params);
    }

    private void booking() {
        boolean quote = QUOTE.equals(screen);
        text(page, quote ? "Demander un devis" : screen, 26, INK, true);
        LinearLayout form = card("Votre trajet");
        String[] options = quote ? CATEGORIES : optionsForSector();
        if (quote) {
            choice(form, "Type de destination", category, options, value -> category = value);
        } else {
            if (options.length == STATIONS.length) {
                text(form, "La liste des communes est indisponible.", 16, INK, false);
                return;
            }
            choice(form, "Commune ou gare de départ", fromPlace, options, value -> fromPlace = value);
        }
        departureField = field(form, quote ? "Adresse et commune de départ" : "Adresse de prise en charge", departure);
        if (!quote) {
            choice(form, "Commune ou gare d’arrivée", toPlace, options, value -> toPlace = value);
        }
        destinationField = field(form, quote ? "Aéroport, gare ou adresse de destination" : "Adresse de destination", destination);
        LinearLayout schedule = card("Date et passagers");
        action(schedule, date == null ? "Choisir la date"
                : "Date · " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                false, this::pickDate);
        action(schedule, time == null ? "Choisir l’heure"
                : "Heure · " + time.format(DateTimeFormatter.ofPattern("HH:mm")),
                false, this::pickTime);
        passengerField = field(schedule, "Nombre de passagers (1 à 4)", passengers);
        passengerField.setInputType(InputType.TYPE_CLASS_NUMBER);
        passengerField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});

        LinearLayout contact = card("Vos coordonnées");
        nameField = field(contact, "Nom du passager", name);
        phoneField = field(contact, "Téléphone de rappel", phone);
        phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
        action(page, "Vérifier ma demande", true, this::review);
    }

    private String[] optionsForSector() {
        List<String> places = new ArrayList<>();
        for (Commune commune : communes) {
            if (screen.equals(commune.sector)) places.add(commune.name);
        }
        places.sort(java.text.Collator.getInstance(java.util.Locale.FRANCE));
        java.util.Collections.addAll(places, STATIONS);
        return places.toArray(new String[0]);
    }

    private void choice(LinearLayout form, String title, String current, String[] options,
                        java.util.function.Consumer<String> onSelect) {
        text(form, title, 15, INK, true);
        action(form, current.isEmpty() ? "Choisir" : current, false,
                () -> new AlertDialog.Builder(this).setTitle(title)
                        .setItems(options, (dialog, which) -> {
                            onSelect.accept(options[which]);
                            show(screen);
                        }).setNegativeButton("Annuler", null).show());
    }

    private void pickDate() {
        LocalDate today = LocalDate.now(PARIS);
        LocalDate selected = date == null ? today : date;
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            date = LocalDate.of(year, month + 1, day);
            show(screen);
        }, selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth());
        dialog.show();
    }

    private void pickTime() {
        LocalTime selected = time == null ? LocalTime.now(PARIS) : time;
        new TimePickerDialog(this, (picker, hour, minute) -> {
            time = LocalTime.of(hour, minute);
            show(screen);
        }, selected.getHour(), selected.getMinute(), true).show();
    }

    private void review() {
        capture();
        LocalDateTime when = date == null || time == null ? null : LocalDateTime.of(date, time);
        boolean quote = QUOTE.equals(screen);
        if (quote ? category.isEmpty() : fromPlace.isEmpty() || toPlace.isEmpty()) {
            message(quote ? "Choisissez un type de destination."
                    : "Choisissez une commune ou une gare pour le départ et l’arrivée.");
            return;
        }
        int count;
        try {
            count = Integer.parseInt(passengers.trim());
            if (count < 1 || count > 4) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            message("Choisissez entre 1 et 4 passagers.");
            return;
        }
        String from = departure.trim();
        String to = destination.trim();
        if (!quote) {
            from = from.isEmpty() ? fromPlace : from + ", " + fromPlace;
            to = to.isEmpty() ? toPlace : to + ", " + toPlace;
        }
        String validation = Trip.validate(from, to, when, LocalDateTime.now(PARIS));
        if (validation != null) {
            message(validation);
            return;
        }
        if (name.isEmpty() || !android.util.Patterns.PHONE.matcher(phone).matches()
                || phone.replaceAll("[^0-9]", "").length() < 6) {
            message("Renseignez votre nom et votre numéro de rappel.");
            return;
        }
        String fare = quote ? "Sur devis" : localFare(count);
        String summary = (quote ? "Devis · " + category : screen)
                + "\n" + Trip.summary(from, to, when) + "\nPassagers · " + count
                + "\n" + fare + "\n" + name + " · " + phone;
        try {
            JSONObject request = new JSONObject()
                    .put("clientId", "NAVETTE_PHOENIX").put("serviceType", quote ? "HORS_SECTEUR" : "TAD")
                    .put("passengers", count).put("travelDate", date.toString())
                    .put("travelTime", time.format(DateTimeFormatter.ofPattern("HH:mm")))
                    .put("pickupAddress", from).put("destinationLabel", to)
                    .put("customerName", name).put("customerPhone", phone)
                    .put("comment", quote ? category : screen).put("fareLabel", fare)
                    .put("fareMode", "QUOTE");
            new AlertDialog.Builder(this).setTitle("Récapitulatif du trajet")
                    .setMessage(summary + "\n\nLe service de réservation vous contactera pour confirmer la disponibilité.")
                    .setNegativeButton("Modifier", null)
                    .setPositiveButton("Envoyer la demande", (dialog, which) -> submit(request, summary))
                    .show();
        } catch (JSONException error) {
            message("Impossible de préparer la demande.");
        }
    }

    private String localFare(int count) {
        Commune from = null;
        Commune to = null;
        for (Commune commune : communes) {
            if (!screen.equals(commune.sector)) continue;
            if (commune.name.equals(fromPlace)) from = commune;
            if (commune.name.equals(toPlace)) to = commune;
        }
        if (from == null || to == null) {
            return "Tarif à confirmer · distance routière vers la gare";
        }
        int total = Fare.estimate(from.metres, to.metres, count, false);
        if (total == 0) return "Tarif à confirmer · hors de la grille des affiches";
        return "Estimation · " + total + " € · à confirmer par le service";
    }

    private void submit(JSONObject request, String summary) {
        URL endpoint;
        try {
            endpoint = ReservationApi.configuredEndpoint();
        } catch (IOException error) {
            message("Le service d’envoi doit être activé pour cette version.");
            return;
        }
        String attempt = java.util.UUID.randomUUID().toString();
        try {
            JSONArray old = trips();
            for (int i = 0; i < old.length(); i++) {
                JSONObject entry = old.getJSONObject(i);
                if (summary.equals(entry.optString("summary")) && entry.has("attempt")
                        && !"rejected".equals(entry.optString("state"))) {
                    show("history");
                    message("Cette demande figure déjà dans votre historique.");
                    return;
                }
            }
            JSONArray updated = new JSONArray().put(new JSONObject().put("summary", summary)
                    .put("attempt", attempt).put("state", "pending"));
            for (int i = 0; i < old.length(); i++) updated.put(old.get(i));
            if (!storage.edit().putString("trips", updated.toString()).commit()) {
                message("Stockage indisponible. La demande n’a pas été envoyée.");
                return;
            }
        } catch (JSONException error) {
            message("Impossible de préparer l’historique des demandes.");
            return;
        }
        show("history");
        new Thread(() -> {
            String state = "unknown";
            String reference = "";
            try {
                reference = ReservationApi.submit(endpoint, request);
                state = "received";
            } catch (ReservationApi.Rejected error) {
                state = "rejected";
            } catch (IOException | JSONException error) {
                // Une coupure réseau peut survenir après l’enregistrement sur le serveur.
            }
            final String outcome = state;
            final String receipt = reference;
            runOnUiThread(() -> {
                try {
                    JSONArray list = trips();
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject entry = list.getJSONObject(i);
                        if (attempt.equals(entry.optString("attempt"))) {
                            entry.put("state", outcome).put("reference", receipt);
                            break;
                        }
                    }
                    storage.edit().putString("trips", list.toString()).apply();
                } catch (JSONException ignored) {
                    // La référence reste visible à la fin de l’envoi.
                }
                if (isFinishing() || isDestroyed()) return;
                if ("history".equals(screen)) show(screen);
                message(receipt.isEmpty() ? statusLabel(outcome) : "Demande reçue · " + receipt);
            });
        }, "transmission-reservation").start();
    }

    private String statusLabel(String state) {
        switch (state) {
            case "received": return "Demande reçue · en attente de confirmation";
            case "rejected": return "Demande refusée par le serveur. Vérifiez les informations.";
            case "pending": return "Envoi en cours ou interrompu · vérifiez auprès du service en cas de doute.";
            default: return "Réception incertaine · contactez le service avant de renouveler la demande.";
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if ("history".equals(screen)) show(screen);
    }

    private JSONArray trips() throws JSONException {
        return new JSONArray(storage.getString("trips", "[]"));
    }

    private void history() {
        try {
            JSONArray trips = trips();
            if (trips.length() == 0) {
                text(card("Mes demandes"), "Aucune demande enregistrée.", 16, INK, false);
            }
            for (int i = 0; i < trips.length(); i++) {
                String summary = trips.getJSONObject(i).getString("summary");
                JSONObject entry = trips.getJSONObject(i);
                boolean legacy = !entry.has("attempt");
                LinearLayout item = card(legacy ? "Ancien brouillon" : statusLabel(entry.optString("state")));
                if (!entry.optString("reference").isEmpty())
                    text(item, "Référence · " + entry.optString("reference"), 16, INK, true);
                text(item, summary, 16, INK, false);
            }
        } catch (JSONException error) {
            text(card("Mes demandes"), "Impossible de lire l’historique.", 16, INK, false);
        }
    }

    private void contact() {
        LinearLayout item = card("Contacter Navette Phoenix");
        if (!PHONE.isEmpty()) {
            action(item, "Appeler le " + PHONE, true,
                    () -> open(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", PHONE, null))));
        }
        if (!EMAIL.isEmpty()) {
            text(item, EMAIL, 15, INK, false);
            action(item, "Écrire au service", false,
                    () -> open(new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", EMAIL, null))));
        }
        if (PHONE.isEmpty() && EMAIL.isEmpty()) {
            text(item, "Les coordonnées seront affichées après la configuration du service.", 15, MUTED, false);
        }
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            message("Aucune application compatible sur cet appareil.");
        }
    }

    private void message(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
    }

    private EditText field(LinearLayout parent, String label, String value) {
        TextView title = text(parent, label, 15, INK, true);
        EditText field = new EditText(this);
        field.setId(View.generateViewId());
        title.setLabelFor(field.getId());
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
        field.setText(value);
        field.setTextColor(INK);
        field.setMinHeight(dp(52));
        field.setBackground(outlined(0xFFFAFBF9));
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        parent.addView(field, -1, -2);
        return field;
    }

    private LinearLayout card(String title) {
        LinearLayout card = column();
        card.setBackground(outlined(Color.WHITE));
        card.setPadding(dp(18), dp(15), dp(18), dp(15));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(9);
        layout.bottomMargin = dp(4);
        page.addView(card, layout);
        text(card, title, 20, INK, true);
        return card;
    }

    private TextView text(LinearLayout parent, String label, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setPadding(0, dp(4), 0, dp(5));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(view, -1, -2);
        return view;
    }

    private void action(LinearLayout parent, String label, boolean primary, Runnable action) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(6);
        parent.addView(button(label, primary, action), layout);
    }

    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setText(label);
        button.setTextColor(primary ? Color.WHITE : INK);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setGravity(Gravity.CENTER);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x36FFFFFF : 0x24173F35),
                primary ? background(GREEN) : outlined(Color.WHITE), null));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(16));
        return drawable;
    }

    private GradientDrawable outlined(int color) {
        GradientDrawable drawable = background(color);
        drawable.setStroke(dp(1), BORDER);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
