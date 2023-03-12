package com.faraz.dictionary;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;

import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.github.wnameless.json.flattener.JsonFlattener;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MainActivity extends AppCompatActivity {

    private static final String NO_DEFINITION_FOUND = "No definitions found for ";

    private Properties properties;
    private static final String MERRIAM_WEBSTER_KEY = "dictionary.merriamWebster.key";
    private static final String MERRIAM_WEBSTER_URL = "dictionary.merriamWebster.url";
    private static final String FREE_DICTIONARY_URL = "dictionary.freeDictionary.url";
    private static final String MAILJET_API_KEY = "mailjet.apiKey";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EditText et = (EditText) findViewById(R.id.wordBox);
        TextView tv = (TextView) findViewById(R.id.textView2);

        et.setOnKeyListener((view, code, event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN) && (code == KeyEvent.KEYCODE_ENTER)) {
                String word = et.getText().toString();
                String mk = loadProperty(MERRIAM_WEBSTER_KEY);
                String mUrl = loadProperty(MERRIAM_WEBSTER_URL);
                String url = format(mUrl, word, mk);
                JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(url,
                        response -> {
                            try {
                                System.out.println("Aagaya" + response.toString());
                                String json = response.toString();
                                Map<String, Object> flattenJson = JsonFlattener.flattenAsMap(json);
                                List<Object> orig = new ArrayList<>(flattenJson.values());
                                flattenJson.keySet().removeIf(x -> !x.contains("shortdef"));
                                if (flattenJson.values().isEmpty()) {
                                    orig.add(0, NO_DEFINITION_FOUND + word + ". Perhaps, you meant:");
                                    tv.setText(orig.stream().filter(String.class::isInstance).map(String.class::cast).collect(joining()));
                                } else {
                                    String result = flattenJson.values().stream().filter(String.class::isInstance).map(String.class::cast).limit(3)
                                            .collect(joining(lineSeparator()));
                                    String stringResult = result + orig.stream().filter(String.class::isInstance).map(String.class::cast)
                                            .filter(x -> x.contains("\\{wi}") && x.contains("\\{/wi}")).map(x -> x.replaceAll("\\{wi}", EMPTY)).map(x -> x.replaceAll(
                                                    "\\{/wi}", EMPTY)).map("// "::concat).collect(joining(lineSeparator()));
                                    tv.setText(stringResult);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            System.out.println("Kia huwa");
                        },
                        error -> {
                            System.out.println("The Land, " + error.toString());
                            tv.setText(error.toString());
                        });
                RequestQueue requestQueue = Volley.newRequestQueue(this);
                requestQueue.add(jsonObjectRequest);
                return true;
            }
            return false;
        });
    }

    private String loadProperty(String property) {
        if (this.properties == null) {
            this.properties = new Properties();
            try (InputStream is = getBaseContext().getAssets().open("application.properties")) {
                properties.load(is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this.properties.getProperty(property);
    }
}
