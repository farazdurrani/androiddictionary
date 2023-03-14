package com.faraz.dictionary;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.android.volley.Request.Method.POST;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.deleteWhitespace;
import static org.apache.commons.lang3.StringUtils.lowerCase;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.wnameless.json.flattener.JsonFlattener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MainActivity extends AppCompatActivity {

  private static final String MONGO_META_DATA = "{\"collection\":\"dictionary\",\"database\":\"myFirstDatabase\",\"dataSource\":\"Cluster0\"";
  private static final String NO_DEFINITION_FOUND = "No definitions found for ";
  private static final String MONGO_ACTION_FIND_ONE = "findOne";
  private static final String CLOSE_CURLY = "}";
  private static final String MONGO_FILTER = "\"filter\": {  \"word\" : \"%s\" } ";

  private Properties properties;
  private static final String MERRIAM_WEBSTER_KEY = "dictionary.merriamWebster.key";
  private static final String MERRIAM_WEBSTER_URL = "dictionary.merriamWebster.url";
  private static final String FREE_DICTIONARY_URL = "dictionary.freeDictionary.url";
  private static final String MAILJET_API_KEY = "mailjet.apiKey";
  private static final String MONGODB_URI = "mongodb.data.uri";
  private static final String MONGODB_API_KEY = "mongodb.data.api.key";

  private EditText lookupWord;
  private String originalLookupWord;
  private TextView googleLink;
  private TextView definitionsView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    lookupWord = findViewById(R.id.wordBox);
    definitionsView = findViewById(R.id.definitions);
    googleLink = findViewById(R.id.google);

    setOpenInBrowserListener();
    setLookupWordListener();
  }

  private void mongoSearchOperation() {
    String operation = MONGO_META_DATA + "," + format(MONGO_FILTER, originalLookupWord) + CLOSE_CURLY;
    mongoSearchOperation(operation, MONGO_ACTION_FIND_ONE);
  }

  void mongoSearchOperation(String operation, String action) {
    StringRequest stringRequest = new MongoStringRequest(POST, format(loadProperty(MONGODB_URI), action),
        handleMongoResponse(), handleMongoError(), operation, loadProperty(MONGODB_API_KEY));
    RequestQueue requestQueue = Volley.newRequestQueue(this);
    requestQueue.add(stringRequest);
  }

  private Response.ErrorListener handleMongoError() {
    return ignore -> {
      definitionsView.setText("Welp... Mongo has gone belly up.");
      googleLink.setVisibility(VISIBLE);
      googleLink.setText(format("open '%s' in google", originalLookupWord));
    };
  }

  private Response.Listener<String> handleMongoResponse() {
    return response -> {
      try {
        JSONObject jsonObject = new JSONObject(response);
        if (jsonObject.optJSONObject("document") == null) {
          lookupInMerriamWebster();
        }
        else {
          definitionsView.setText(format("'%s' already looked-up", originalLookupWord));
        }
      }
      catch (JSONException e) {
        definitionsView.setText("Welp... Mongo has gone belly up.");
      }
      googleLink.setVisibility(VISIBLE);
      googleLink.setText(format("open '%s' in google", originalLookupWord));
    };
  }

  private void lookupInMerriamWebster() {
    String word = originalLookupWord;
    String mk = loadProperty(MERRIAM_WEBSTER_KEY);
    String mUrl = loadProperty(MERRIAM_WEBSTER_URL);
    String url = format(mUrl, word, mk);
    JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(url, this.merriamWebsterResponse(word),
        ignoreError -> definitionsView.setText("Welp... merriam webster call has gone belly up!"));
    RequestQueue requestQueue = Volley.newRequestQueue(this);
    requestQueue.add(jsonObjectRequest);
  }

  private void setLookupWordListener() {
    lookupWord.setOnKeyListener((view, code, event) -> {
      if ((event.getAction() == KeyEvent.ACTION_DOWN) && (code == KeyEvent.KEYCODE_ENTER)) {
        originalLookupWord = lowerCase(deleteWhitespace(lookupWord.getText().toString()));
        lookupWord.setText(null);
        googleLink.setVisibility(INVISIBLE);
        Toast.makeText(this, "Sending " + originalLookupWord, Toast.LENGTH_SHORT).show();
        mongoSearchOperation();
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

  private Response.Listener<JSONArray> merriamWebsterResponse(String word) {
    return response -> {
      try {
        String json = response.toString();
        Map<String, Object> flattenJson = JsonFlattener.flattenAsMap(json);
        List<Object> orig = new ArrayList<>(flattenJson.values());
        flattenJson.keySet().removeIf(x -> !x.contains("shortdef"));
        if (flattenJson.values().isEmpty()) {
          orig.add(0, NO_DEFINITION_FOUND + word + ". Perhaps, you meant:");
          definitionsView.setText(orig.stream().filter(String.class::isInstance).map(String.class::cast)
              .collect(joining()));
        }
        else {
          String result = flattenJson.values().stream().filter(String.class::isInstance).map(String.class::cast).limit(3)
              .collect(joining(lineSeparator()));
          String stringResult = result + orig.stream().filter(String.class::isInstance).map(String.class::cast)
              .filter(x -> x.contains("\\{wi}") && x.contains("\\{/wi}")).map(x -> x.replaceAll("\\{wi}", EMPTY)).map(x -> x.replaceAll(
                  "\\{/wi}", EMPTY)).map("// "::concat).collect(joining(lineSeparator()));
          definitionsView.setText(stringResult);
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
