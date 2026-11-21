package fr.navettephoenix.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class FareActivity extends Activity {
    private final List<Commune> communes = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int margin = Math.round(22 * getResources().getDisplayMetrics().density);
            content.setPadding(margin + insets.getSystemWindowInsetLeft(),
                    margin + insets.getSystemWindowInsetTop(),
                    margin + insets.getSystemWindowInsetRight(),
                    margin + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);

        label(content, "Estimer mon trajet", 28);
        label(content, "Tarif indicatif calculé depuis le centre des communes. L’adresse exacte et le parcours routier restent à vérifier. Paiement à bord après confirmation du service.", 16);

        try {
            communes.addAll(Commune.load(getAssets().open("communes.csv")));
            communes.add(null);
        } catch (IOException | IllegalArgumentException error) {
            label(content, "La liste des communes est indisponible.", 16);
            return;
        }

        Spinner departure = communePicker(content, "Commune de départ", 1);
        Spinner arrival = communePicker(content, "Commune d’arrivée", 2);
        label(content, "Nombre de passagers", 16);
        EditText passengers = new EditText(this);
        passengers.setId(3);
        passengers.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        passengers.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(3)
        });
        passengers.setText("1");
        content.addView(passengers);
        CheckBox roundTrip = checkbox(content, "Aller-retour", 4);
        CheckBox station = checkbox(content, "Départ ou arrivée à la gare de La Loupe ou de Verneuil-sur-Avre", 5);
        Button calculate = new Button(this);
        calculate.setText("Calculer l’estimation");
        calculate.setAllCaps(false);
        content.addView(calculate);
        TextView result = label(content, "", 18);
        result.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        calculate.setOnClickListener(view -> {
            int count;
            try {
                count = Integer.parseInt(passengers.getText().toString());
                if (count < 1) throw new NumberFormatException();
            } catch (NumberFormatException error) {
                passengers.setError("Indiquez au moins un passager.");
                result.setText("");
                return;
            }
            if (departure.getSelectedItemPosition() == 0 || arrival.getSelectedItemPosition() == 0) {
                result.setText("Choisissez les deux communes du trajet.");
                return;
            }
            Commune from = communes.get(departure.getSelectedItemPosition() - 1);
            Commune to = communes.get(arrival.getSelectedItemPosition() - 1);
            if (from == null || to == null) {
                result.setText("Sur devis · hors secteur ou trajet particulier");
                return;
            }
            if (station.isChecked()) {
                result.setText("Tarif à confirmer · distance par la route nécessaire");
                return;
            }
            int total = Fare.estimate(from.metres, to.metres, count, roundTrip.isChecked());
            int zone = Fare.zone(Math.max(from.metres, to.metres));
            boolean needsReview = from.needsReview || to.needsReview
                    || !from.sector.equals(to.sector);
            String review = needsReview ? "\nTarif à confirmer par le service." : "";
            result.setText(total == 0
                    ? "Au-delà des 20 km des affiches · traitement sur devis à confirmer"
                    : total + " € estimés · zone " + zone + " · règlement à bord" + review);
        });
    }

    private Spinner communePicker(LinearLayout parent, String title, int id) {
        List<String> choices = new ArrayList<>();
        choices.add("Choisir une commune");
        for (Commune commune : communes) {
            choices.add(commune == null ? "Autre commune" : commune.name + " · " + commune.sector);
        }
        TextView label = label(parent, title, 16);
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        spinner.setId(id);
        spinner.setPrompt(title);
        label.setLabelFor(id);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        parent.addView(spinner);
        return spinner;
    }

    private CheckBox checkbox(LinearLayout parent, String title, int id) {
        CheckBox box = new CheckBox(this);
        box.setId(id);
        box.setText(title);
        parent.addView(box);
        return box;
    }

    private TextView label(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        parent.addView(view);
        return view;
    }

}
