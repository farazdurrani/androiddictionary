package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static com.android.volley.Request.Method.POST;
import static com.faraz.dictionary.MainActivity2.getItem;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.github.wnameless.json.flattener.JsonFlattener;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.IntStream;

public class MainActivity extends AppCompatActivity {

    static final String MONGO_PARTIAL_BODY = "{\"collection\":\"dictionary\",\"database\":\"myFirstDatabase\",\"dataSource\":\"Cluster0\"";
    static final String MONGO_ACTION_FIND_ALL = "find";
    static final String MONGO_ACTION_AGGREGATE = "aggregate";
    static final String MONGO_ACTION_UPDATE_MANY = "updateMany";
    static final String CLOSE_CURLY = "}";
    static final String MONGODB_URI = "mongodb.data.uri";
    static final String MONGODB_API_KEY = "mongodb.data.api.key";
    static final String CHICAGO = "America/Chicago";
    private static final String NO_DEFINITION_FOUND = "No definitions found for '%s'. Perhaps, you meant:";
    private static final String MONGO_ACTION_INSERT_ONE = "insertOne";
    private static final String MONGO_DOCUMENT = "\"document\" : {  \"word\": \"%s\",\"lookupTime\": {  \"$date\" : {  \"$numberLong\" : \"%d\"} }, \"reminded\": %s }";
    private static final String REGEX_WHITE_SPACES = "\\s+";
    private static final String MERRIAM_WEBSTER_KEY = "dictionary.merriamWebster.key";
    private static final String MERRIAM_WEBSTER_URL = "dictionary.merriamWebster.url";
    private Properties properties;
    private RequestQueue requestQueue;

    private EditText lookupWord;
    private String originalLookupWord;
    private TextView googleLink;
    private TextView definitionsView;
    private TextView saveView;

    @SuppressLint("NewApi")
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
        setLookupWordListenerNew();
        setStoreWordListener();
    }

    public void goTo2ndActivity(View view) {
        Intent intent = new Intent(this, MainActivity2.class);
        startActivity(intent);
    }

    private void setRequestQueue() {
        this.requestQueue = Volley.newRequestQueue(this);
    }

    private void setStoreWordListener() {
        saveView.setOnClickListener(view -> AsyncTask.execute(this::storeWord));
    }

    private void storeWord() {
        try {
            if (isBlank(originalLookupWord)) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Nothing to save.", LENGTH_SHORT).show());
            } else if (alreadyStored()) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, format("'%s''s already stored", originalLookupWord), LENGTH_SHORT).show());
            } else {
                saveWordInMongo();
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Not sure what went wrong.", LENGTH_LONG).show());
        }
    }

    private boolean alreadyStored() {
        String query = wordExistsQuery();
        return !getWordsFromMongo(query).isEmpty();
    }

    private String wordExistsQuery() {
        String filter = format(",\"filter\": { \"word\": \"%s\" }", originalLookupWord);
        return MONGO_PARTIAL_BODY + filter + CLOSE_CURLY;
    }

    private void setOpenInBrowserListener() {
        googleLink.setOnClickListener(ignore -> AsyncTask.execute(this::openInWebBrowser));
    }

    private void saveWordInMongo() {
        String body = getSaveQuery();
        RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
        JsonObjectRequest stringRequest = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI), MONGO_ACTION_INSERT_ONE),
                requestFuture, requestFuture, body, loadProperty(MONGODB_API_KEY));
        requestQueue.add(stringRequest);
        try {
            JSONObject ans = requestFuture.get();
            String ignore = ans.getString("insertedId");
            runOnUiThread(() -> Toast.makeText(MainActivity.this, format("'%s' saved!", originalLookupWord), LENGTH_SHORT).show());
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Mongo's belly up!", LENGTH_LONG).show());
        }
    }

    @SuppressLint({"NewApi", "DefaultLocale"})
    private String getSaveQuery() {
        return MONGO_PARTIAL_BODY + "," + format(MONGO_DOCUMENT, originalLookupWord,
                Instant.now(Clock.system(ZoneId.of(CHICAGO))).toEpochMilli(), false) + CLOSE_CURLY;
    }

    private String[] parseMerriamWebsterResponse(String json) {
        Map<String, Object> flattenJson = JsonFlattener.flattenAsMap(json);
        List<Object> orig = new ArrayList<>(flattenJson.values());
        flattenJson.keySet().removeIf(x -> !x.contains("shortdef"));
        if (flattenJson.values().isEmpty()) {
            orig.add(0, format(NO_DEFINITION_FOUND, originalLookupWord));
            String alternativeWords = orig.stream().filter(Objects::nonNull).filter(String.class::isInstance)
                    .map(String.class::cast).filter(StringUtils::isNotBlank).map(lineSeparator()::concat)
                    .map("----------"::concat).collect(joining(lineSeparator()));
            return new String[]{"false", alternativeWords};
        } else {
            String result = flattenJson.values().stream().filter(Objects::nonNull).filter(String.class::isInstance)
                    .map(String.class::cast).limit(3).filter(StringUtils::isNotBlank).map(lineSeparator()::concat)
                    .map("----------"::concat).collect(joining(lineSeparator()));
            String definition = result + orig.stream().filter(Objects::nonNull).filter(String.class::isInstance)
                    .map(String.class::cast).filter(StringUtils::isNotBlank).filter(x -> x.contains("\\{wi}") &&
                            x.contains("\\{/wi}")).map(x -> x.replaceAll("\\{wi}", EMPTY)).map(x ->
                            x.replaceAll("\\{/wi}", EMPTY)).map("// "::concat).collect(joining(lineSeparator()));
            return new String[]{"true", definition};
        }
    }

    @SuppressLint("SetTextI18n")
    private String[] lookupInMerriamWebsterNew() {
        String url = formMerriamWebsterUrl();
        RequestFuture<JSONArray> requestFuture = RequestFuture.newFuture();
        JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(url, requestFuture, requestFuture);
        requestQueue.add(jsonObjectRequest);
        try {
            return parseMerriamWebsterResponse(requestFuture.get().toString());
        } catch (Exception e) {
            definitionsView.setText("Welp... merriam webster's gone belly up!");
        }
        throw new RuntimeException();
    }

    private String formMerriamWebsterUrl() {
        String word = originalLookupWord;
        String mk = loadProperty(MERRIAM_WEBSTER_KEY);
        String mUrl = loadProperty(MERRIAM_WEBSTER_URL);
        return format(mUrl, word, mk);
    }

    private void setLookupWordListenerNew() {
        lookupWord.setOnKeyListener((view, code, event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN) && (code == KeyEvent.KEYCODE_ENTER)) {
                doInitialWork();
                AsyncTask.execute(() -> {
                    openInWebBrowser();
                    lookupWord();
                });
                return true;
            }
            return false;
        });
    }

    private void lookupWord() {
        try {
            if (alreadyStored()) {
                definitionsView.setText(format("'%s' already looked-up", originalLookupWord));
                runOnUiThread(() -> Toast.makeText(MainActivity.this, format("Not storing '%s'", originalLookupWord), LENGTH_SHORT).show());
            } else {
                String[] definition = lookupInMerriamWebsterNew();
                definitionsView.setText(definition[1]);
                if (definition[0].equalsIgnoreCase("true")) {
                    saveWordInMongo();
                }
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Not sure what went wrong.", LENGTH_LONG).show());
        }
    }

    private void openInWebBrowser() {
        if (isBlank(originalLookupWord)) {
            Toast.makeText(this, "Nothing to lookup", LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse(format("https://www.google.com/search?q=define: %s", originalLookupWord));
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    private void doInitialWork() {
        originalLookupWord = cleanWord();
        lookupWord.setText(null);
        Toast.makeText(MainActivity.this, format("Sending '%s'", originalLookupWord), LENGTH_SHORT).show();
    }

    private String cleanWord() {
        return Arrays.stream(lookupWord.getText().toString().split(REGEX_WHITE_SPACES))
                .map(String::trim).map(String::toLowerCase).collect(joining(SPACE)).trim();
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

    @SuppressLint("SetTextI18n")
    private List<String> getWordsFromMongo(String operation) {
        RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
        JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI),
                MONGO_ACTION_FIND_ALL), requestFuture, requestFuture, operation, loadProperty(MONGODB_API_KEY));
        request.setShouldCache(true);
        requestQueue.add(request);
        try {
            JSONArray ans = requestFuture.get().getJSONArray("documents");
            return IntStream.range(0, ans.length()).mapToObj(i -> getItem(i, ans, "word")).collect(toList());
        } catch (Exception e) {
            definitionsView.setText("Mongo's gone belly up!");
        }
        throw new RuntimeException();
    }
}
