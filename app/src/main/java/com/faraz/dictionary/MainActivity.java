package com.faraz.dictionary;

import static android.view.View.INVISIBLE;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.github.wnameless.json.flattener.JsonFlattener;

import org.json.JSONArray;

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

  private EditText lookupWord;
  private String originalLookupWord;
  private TextView googleLink;
  private TextView definitionsView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    lookupWord = (EditText) findViewById(R.id.wordBox);
    definitionsView = (TextView) findViewById(R.id.definitions);
    googleLink = (TextView) findViewById(R.id.google);

    setOpenInBrowserListener();

    setLookupWordListener();
  }

  private void setLookupWordListener() {
    lookupWord.setOnKeyListener((view, code, event) -> {
      if ((event.getAction() == KeyEvent.ACTION_DOWN) && (code == KeyEvent.KEYCODE_ENTER)) {
        originalLookupWord = lookupWord.getText().toString();
        googleLink.setVisibility(INVISIBLE);
        Toast.makeText(this, "Sending " + originalLookupWord , Toast.LENGTH_SHORT).show();
        String word = originalLookupWord;
        String mk = loadProperty(MERRIAM_WEBSTER_KEY);
        String mUrl = loadProperty(MERRIAM_WEBSTER_URL);
        String url = format(mUrl, word, mk);
        JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(url,
            this.responseConsumer(word, definitionsView),
            error -> definitionsView.setText(error.toString()));
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(jsonObjectRequest);
        return true;
      }
      return false;
    });
  }

  private void setOpenInBrowserListener() {
    googleLink.setOnClickListener((view) -> {
      Uri uri = Uri.parse(format("https://www.google.com/search?q=define: %s", originalLookupWord));
      startActivity(new Intent(Intent.ACTION_VIEW, uri));
    });
  }

  private Response.Listener<JSONArray> responseConsumer(String word, TextView tv) {
    return response -> {
      try {
        googleLink.setVisibility(View.VISIBLE);
        googleLink.setText(format("open '%s' in google", originalLookupWord));
        String json = response.toString();
        Map<String, Object> flattenJson = JsonFlattener.flattenAsMap(json);
        List<Object> orig = new ArrayList<>(flattenJson.values());
        flattenJson.keySet().removeIf(x -> !x.contains("shortdef"));
        if (flattenJson.values().isEmpty()) {
          orig.add(0, NO_DEFINITION_FOUND + word + ". Perhaps, you meant:");
          tv.setText(orig.stream().filter(String.class::isInstance).map(String.class::cast)
              .collect(joining()));
        }
        else {
          String result = flattenJson.values().stream().filter(String.class::isInstance).map(String.class::cast).limit(3)
              .collect(joining(lineSeparator()));
          String stringResult = result + orig.stream().filter(String.class::isInstance).map(String.class::cast)
              .filter(x -> x.contains("\\{wi}") && x.contains("\\{/wi}")).map(x -> x.replaceAll("\\{wi}", EMPTY)).map(x -> x.replaceAll(
                  "\\{/wi}", EMPTY)).map("// "::concat).collect(joining(lineSeparator()));
          tv.setText(stringResult);
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

  private String loadProperty(String property) {
    if (this.properties == null) {
      this.properties = new Properties();
      try (InputStream is = getBaseContext().getAssets().open("application.properties")) {
        properties.load(is);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return this.properties.getProperty(property);
  }
}
