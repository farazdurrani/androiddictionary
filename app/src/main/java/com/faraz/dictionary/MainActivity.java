package com.faraz.dictionary;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_SHORT;
import static com.android.volley.Request.Method.POST;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.deleteWhitespace;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;

import android.annotation.SuppressLint;
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
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.wnameless.json.flattener.JsonFlattener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MainActivity extends AppCompatActivity {

  private static final String CHICAGO = "America/Chicago";
  private static final String MONGO_PARTIAL_BODY = "{\"collection\":\"dictionary\",\"database\":\"myFirstDatabase\",\"dataSource\":\"Cluster0\"";
  private static final String NO_DEFINITION_FOUND = "No definitions found for ";
  private static final String MONGO_ACTION_FIND_ONE = "findOne";
  private static final String MONGO_ACTION_INSERT_ONE = "insertOne";
  private static final String CLOSE_CURLY = "}";
  private static final String MONGO_FILTER = "\"filter\": {  \"word\" : \"%s\" } ";
  private static final String MONGO_DOCUMENT = "\"document\" : {  \"word\": \"%s\",\"lookupTime\": {  \"$date\" : {  \"$numberLong\" : \"%d\"} }, \"reminded\": %s }";

  private Properties properties;
  private static final String MERRIAM_WEBSTER_KEY = "dictionary.merriamWebster.key";
  private static final String MERRIAM_WEBSTER_URL = "dictionary.merriamWebster.url";
  private static final String FREE_DICTIONARY_URL = "dictionary.freeDictionary.url";
  private static final String MAILJET_API_KEY = "mailjet.apiKey";
  private static final String MONGODB_URI = "mongodb.data.uri";
  private static final String MONGODB_API_KEY = "mongodb.data.api.key";

  private RequestQueue requestQueue;

  private EditText lookupWord;
  private String originalLookupWord;
  private TextView googleLink;
  private TextView definitionsView;
  private TextView saveView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    lookupWord = findViewById(R.id.wordBox);
    definitionsView = findViewById(R.id.definitions);
    googleLink = findViewById(R.id.google);
    saveView = findViewById(R.id.save);

    setRequestQueue();
    setOpenInBrowserListener();
    setLookupWordListener();
    setStoreWordListener();
  }

  private void setRequestQueue() {
    if (this.requestQueue == null) {
      this.requestQueue = Volley.newRequestQueue(this);
    }
  }

  private void setStoreWordListener() {
    saveView.setOnClickListener((view) -> {
      Toast.makeText(this, format("Saved '%s'", originalLookupWord), LENGTH_SHORT).show();
      RequestFuture<JSONObject> future = RequestFuture.newFuture();
//      JsonObjectRequest request = new JsonObjectRequest(URL, new JSONObject(), future, future);
//      requestQueue.add(request);
//
//      try {
//        JSONObject response = future.get(); // this will block
//      }
//      catch (InterruptedException e) {
//        // exception handling
//      }
//      catch (ExecutionException e) {
//        // exception handling
//      }
    });
  }

  private void setOpenInBrowserListener() {
    googleLink.setOnClickListener((view) -> {
      Uri uri = Uri.parse(format("https://www.google.com/search?q=define: %s", originalLookupWord));
      startActivity(new Intent(Intent.ACTION_VIEW, uri));
    });
  }

  private void mongoSearchOperation() {
    String operation = MONGO_PARTIAL_BODY + "," + format(MONGO_FILTER, originalLookupWord) + CLOSE_CURLY;
    mongoSearchOperation(operation, MONGO_ACTION_FIND_ONE);
  }

  void mongoSearchOperation(String operation, String action) {
    StringRequest stringRequest = new MongoStringRequest(POST, format(loadProperty(MONGODB_URI), action),
        handleMongoFindResponse(), handleMongoError(), operation, loadProperty(MONGODB_API_KEY));
    requestQueue.add(stringRequest);
  }

  private Response.ErrorListener handleMongoError() {
    return ignore -> {
      definitionsView.setText("Welp... Mongo has gone belly up.");
      googleLink.setVisibility(VISIBLE);
      googleLink.setText(format("open '%s' in google", originalLookupWord));
    };
  }

  private Response.Listener<String> handleMongoFindResponse() {
    return response -> {
      try {
        JSONObject jsonObject = new JSONObject(response);
        if (jsonObject.optJSONObject("document") == null) {
          lookupInMerriamWebster();
        }
        else {
          definitionsView.setText(format("'%s' already looked-up", originalLookupWord));
          Toast.makeText(this, format("Not storing '%s'", originalLookupWord), LENGTH_SHORT).show();
        }
      }
      catch (JSONException e) {
        definitionsView.setText("Welp... Mongo has gone belly up.");
      }
      googleLink.setVisibility(VISIBLE);
      googleLink.setText(format("open '%s' in google", originalLookupWord));
    };
  }

  @SuppressLint("NewApi")
  private void saveWordInMongo() {
    String body = MONGO_PARTIAL_BODY + "," + format(MONGO_DOCUMENT, originalLookupWord,
        Instant.now(Clock.system(ZoneId.of(CHICAGO))).toEpochMilli(), false) + CLOSE_CURLY;
    StringRequest stringRequest = new MongoStringRequest(POST, format(loadProperty(MONGODB_URI), MONGO_ACTION_INSERT_ONE),
        handleMongoInsertResponse(), handleMongoError(), body, loadProperty(MONGODB_API_KEY));
    requestQueue.add(stringRequest);
  }

  private Response.Listener<String> handleMongoInsertResponse() {
    return response -> {
      try {
        JSONObject jsonObject = new JSONObject(response);
        if (isBlank(jsonObject.optString("insertedId"))) {
          Toast.makeText(this, format("'%s' is not saved for some reason...", originalLookupWord), LENGTH_SHORT).show();
        }
        else {
          Toast.makeText(this, format("%s's been stored.", originalLookupWord), LENGTH_SHORT).show();
        }
      }
      catch (JSONException e) {
        throw new RuntimeException(e);
      }
    };
  }

  private void lookupInMerriamWebster() {
    String word = originalLookupWord;
    String mk = loadProperty(MERRIAM_WEBSTER_KEY);
    String mUrl = loadProperty(MERRIAM_WEBSTER_URL);
    String url = format(mUrl, word, mk);
    JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(url, this.merriamWebsterResponse(word),
        ignoreError -> definitionsView.setText("Welp... merriam webster call has gone belly up!"));
    requestQueue.add(jsonObjectRequest);
  }

  private void setLookupWordListener() {
    lookupWord.setOnKeyListener((view, code, event) -> {
      if ((event.getAction() == KeyEvent.ACTION_DOWN) && (code == KeyEvent.KEYCODE_ENTER)) {
        originalLookupWord = lowerCase(deleteWhitespace(lookupWord.getText().toString()));
        lookupWord.setText(null);
        googleLink.setVisibility(INVISIBLE);
        saveView.setVisibility(INVISIBLE);
        Toast.makeText(this, format("Sending '%s'", originalLookupWord), LENGTH_SHORT).show();
        mongoSearchOperation();
        return true;
      }
      return false;
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
          saveView.setVisibility(VISIBLE);
          saveView.setText(format("save '%s'", originalLookupWord));
        }
        else {
          String result = flattenJson.values().stream().filter(String.class::isInstance).map(String.class::cast).limit(3)
              .collect(joining(lineSeparator()));
          String stringResult = result + orig.stream().filter(String.class::isInstance).map(String.class::cast)
              .filter(x -> x.contains("\\{wi}") && x.contains("\\{/wi}")).map(x -> x.replaceAll("\\{wi}", EMPTY)).map(x -> x.replaceAll(
                  "\\{/wi}", EMPTY)).map("// "::concat).collect(joining(lineSeparator()));
          definitionsView.setText(stringResult);
          saveWordInMongo();
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
