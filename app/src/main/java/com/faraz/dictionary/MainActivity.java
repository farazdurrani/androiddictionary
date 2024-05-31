package com.faraz.dictionary;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.MainActivity4.FILE_NAME;
import static com.faraz.dictionary.MainActivity4.LOOKUPTHISWORD;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.joining;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.github.wnameless.json.flattener.JsonFlattener;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;

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
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@RequiresApi(api = Build.VERSION_CODES.O)
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
    private static final String REGEX_WHITE_SPACES = "\\s+";
    private static final String MERRIAM_WEBSTER_KEY = "dictionary.merriamWebster.key";
    private static final String MERRIAM_WEBSTER_URL = "dictionary.merriamWebster.url";
    private Properties properties;
    private RequestQueue requestQueue;
    private ApiService apiService;
    private FileService fileService;
    private Context context;

    private EditText lookupWord;
    private String originalLookupWord;
    private TextView googleLink;
    private TextView definitionsView;
    private TextView saveView;
    private Button deleteButton;
    private Button offlineActivityButton;
    private boolean offline;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = getBaseContext();
        apiService = new ApiService(Volley.newRequestQueue(this), properties());
        fileService = new FileService(getExternalFilesDir(null), FILE_NAME);
        lookupWord = findViewById(R.id.wordBox);
        definitionsView = findViewById(R.id.definitions);
        definitionsView.setMovementMethod(new ScrollingMovementMethod());
        googleLink = findViewById(R.id.google);
        saveView = findViewById(R.id.save);
        deleteButton = findViewById(R.id.deleteButton);
        deleteButton.setVisibility(INVISIBLE);
        offlineActivityButton = findViewById(R.id.offlineActivity);
        offlineActivityButton.setVisibility(INVISIBLE);
        setRequestQueue();
        setOpenInBrowserListener();
        setLookupWordListenerNew();
        setStoreWordListener();
        supplyAsync(this::isOffline).thenAccept(isOffline -> {
            offline = isOffline;
            offlineActivityButton.setVisibility(offline ? VISIBLE : INVISIBLE);
            runOnUiThread(() -> Toast.makeText(context, offline ? "You are offline." : "You are online.", LENGTH_SHORT).show());
        });
        ofNullable(getIntent().getExtras()).map(e -> e.getString(LOOKUPTHISWORD)).ifPresent(this::doLookup);
    }

    public void goTo2ndActivity(View view) {
        Intent intent = new Intent(this, MainActivity2.class);
        startActivity(intent);
    }

    private void setStoreWordListener() {
        saveView.setOnClickListener(view -> runAsync(this::storeWord));
    }

    private void setOpenInBrowserListener() {
        googleLink.setOnClickListener(ignore -> runAsync(this::openInWebBrowser));
    }

    private void setLookupWordListenerNew() {
        lookupWord.setOnKeyListener((view, code, event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN) && (code == KeyEvent.KEYCODE_ENTER)) {
                doLookup();
                return true;
            }
            return false;
        });
        lookupWord.setOnFocusChangeListener((view, b) -> deleteButton.setVisibility(INVISIBLE));
    }

    private void doLookup() {
        doInitialWork();
        Optional.of(offline).filter(BooleanUtils::isTrue).ifPresent(this::writeToFile);
        Optional.of(offline).filter(BooleanUtils::isFalse).ifPresent(this::doLookup);
    }

    private void doLookup(boolean ignore) {
        supplyAsync(this::isOffline).thenAccept(this::writeToFileOrStoreInDbAndOpenBrowser);
    }

    private void storeInDbAndOpenBrowser(boolean ignore) {
        runAsync(this::lookupWord);
        openInWebBrowser();
    }

    private void writeToFile(boolean ignore) {
        runAsync(() -> fileService.writeFileExternalStorage(true, originalLookupWord)).thenAccept(nothing -> definitionsView.setText(format("'%s' has been stored offline.", originalLookupWord)));
    }

    private String formMerriamWebsterUrl() {
        String word = originalLookupWord;
        String mk = loadProperty(MERRIAM_WEBSTER_KEY);
        String mUrl = loadProperty(MERRIAM_WEBSTER_URL);
        return format(mUrl, word, mk);
    }

    @SuppressLint("SetTextI18n")
    private void lookupWord() {
        try {
            String[] definition = lookupInMerriamWebsterNew();
            definitionsView.setText(definition[1]);
            Optional.of(definition[0]).map(BooleanUtils::toBoolean).filter(BooleanUtils::isTrue).ifPresent(this::saveWordInDb);
            Optional.of(definition[0]).map(BooleanUtils::toBoolean).filter(BooleanUtils::isFalse).ifPresent(ignore -> runOnUiThread(() -> deleteButton.setVisibility(VISIBLE)));
        } catch (Exception e) {
            definitionsView.setText(format("welp...%s%s%s", originalLookupWord, lineSeparator(), getStackTrace(e)));
            runOnUiThread(() -> Toast.makeText(context, "Not sure what went wrong.", LENGTH_LONG).show());
        }
    }

    private void deleteFromFileIfPresent() {
        Arrays.stream(fileService.readFile()).filter(s -> s.equals(originalLookupWord)).findFirst().ifPresent(ignore -> deleteButton(null));
    }

    private void openInWebBrowser() {
        if (isBlank(originalLookupWord)) {
            runOnUiThread(() -> Toast.makeText(context, "Nothing to lookup", LENGTH_SHORT).show());
            return;
        }
        Uri uri = Uri.parse(format("https://www.google.com/search?q=define: %s", originalLookupWord));
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    private void doInitialWork() {
        originalLookupWord = cleanWord();
        lookupWord.setText(null);
        runOnUiThread(() -> Toast.makeText(MainActivity.this, format("Sending '%s'", originalLookupWord), LENGTH_SHORT).show());
    }

    private String cleanWord() {
        return Arrays.stream(lookupWord.getText().toString().split(REGEX_WHITE_SPACES)).map(String::trim).map(String::toLowerCase).collect(joining(SPACE)).trim();
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

    private void storeWord() {
        Optional.of(ofNullable(originalLookupWord).orElse(EMPTY)).filter(StringUtils::isBlank).ifPresent(ignore -> runOnUiThread(() -> Toast.makeText(MainActivity.this, "Nothing to save.", LENGTH_SHORT).show()));
        ofNullable(originalLookupWord).filter(StringUtils::isNotBlank).ifPresent(this::doMoreChecks);
    }

    private void doMoreChecks(String ignore) {
        Optional.of(offline).filter(BooleanUtils::isTrue).ifPresent(this::writeToFile);
        Optional.of(offline).filter(BooleanUtils::isFalse).ifPresent(this::checkIfOfflineAndDoMoreWork);

    }

    private void checkIfOfflineAndDoMoreWork(Boolean ignore) {
        supplyAsync(this::isOffline).thenAccept(this::doMoreWork);
    }

    private void doMoreWork(Boolean isOffline) {
        runOnUiThread(() -> offlineActivityButton.setVisibility(isOffline ? VISIBLE : INVISIBLE));
        Optional.of(isOffline).filter(BooleanUtils::isTrue).ifPresent(this::writeToFile);
        Optional.of(isOffline).filter(BooleanUtils::isFalse).ifPresent(this::saveWordInDb);
    }

    private void writeToFileOrStoreInDbAndOpenBrowser(Boolean isOffline) {
        runOnUiThread(() -> offlineActivityButton.setVisibility(isOffline ? VISIBLE : INVISIBLE));
        Optional.of(isOffline).filter(BooleanUtils::isTrue).ifPresent(this::writeToFile);
        Optional.of(isOffline).filter(BooleanUtils::isFalse).ifPresent(this::storeInDbAndOpenBrowser);
    }

    private void saveWordInDb(Boolean... ignore) {
        try {
            saveWordInMongo();
            deleteFromFileIfPresent();
        } catch (Exception e) {
            definitionsView.setText(format("welp...%s%s%s", originalLookupWord, lineSeparator(), getStackTrace(e)));
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Not sure what went wrong.", LENGTH_LONG).show());
        }
    }

    private String wordExistsQuery() {
        return format("\"filter\": { \"word\": \"%s\" }", originalLookupWord);
    }

    private void saveWordInMongo() throws Exception {
        String filter = wordExistsQuery();
        String update = getUpdateQueryToUpdateReminded();
        String options = format("\"upsert\" : %b", true);
        String query = MONGO_PARTIAL_BODY + "," + filter + ", " + update + ", " + options + CLOSE_CURLY;
        Map<String, Object> response = apiService.upsert(query, MONGO_ACTION_UPDATE_MANY);
        Predicate<Map<String, Object>> upsert = r -> r.containsKey("upsertedId");
        Optional.of(response).filter(upsert).ifPresent(ignore -> runOnUiThread(() -> Toast.makeText(context, format("'%s' saved!", capitalize(originalLookupWord)), LENGTH_SHORT).show()));
        Optional.of(response).filter(upsert.negate()).ifPresent(ignore -> {
            runOnUiThread(() -> Toast.makeText(context, format("'%s' is already saved.", capitalize(originalLookupWord)), LENGTH_SHORT).show());
            definitionsView.setText(format("%s%s's already looked-up.", lineSeparator(), capitalize(originalLookupWord)));
        });
    }

    @SuppressLint({"NewApi", "DefaultLocale"})
    private String getUpdateQueryToUpdateReminded() {
        return format("\"update\": { \"$set\" : { \"word\": \"%s\" }, \"$setOnInsert\" : { \"lookupTime\" : {  \"$date\" : {  \"$numberLong\" : \"%d\"} } } }", originalLookupWord, Instant.now(Clock.system(ZoneId.of(CHICAGO))).toEpochMilli());
    }

    private String[] parseMerriamWebsterResponse(String json) {
        Map<String, Object> flattenJson = JsonFlattener.flattenAsMap(json);
        List<Object> orig = new ArrayList<>(flattenJson.values());
        flattenJson.keySet().removeIf(x -> !x.contains("shortdef"));
        if (flattenJson.values().isEmpty()) {
            orig.add(0, format(NO_DEFINITION_FOUND, originalLookupWord));
            String alternativeWords = orig.stream().filter(Objects::nonNull).filter(String.class::isInstance).map(String.class::cast).filter(StringUtils::isNotBlank).map(lineSeparator()::concat).map("----------"::concat).collect(joining(lineSeparator()));
            return new String[]{"false", alternativeWords};
        } else {
            String result = flattenJson.values().stream().filter(Objects::nonNull).filter(String.class::isInstance).map(String.class::cast).limit(3).filter(StringUtils::isNotBlank).map(lineSeparator()::concat).map("----------"::concat).collect(joining(lineSeparator()));
            String definition = result + orig.stream().filter(Objects::nonNull).filter(String.class::isInstance).map(String.class::cast).filter(StringUtils::isNotBlank).filter(x -> x.contains("\\{wi}") && x.contains("\\{/wi}")).map(x -> x.replaceAll("\\{wi}", EMPTY)).map(x -> x.replaceAll("\\{/wi}", EMPTY)).map("// "::concat).collect(joining(lineSeparator()));
            return new String[]{"true", definition};
        }
    }

    @SuppressLint("SetTextI18n")
    private String[] lookupInMerriamWebsterNew() throws ExecutionException, InterruptedException {
        String url = formMerriamWebsterUrl();
        RequestFuture<JSONArray> requestFuture = RequestFuture.newFuture();
        JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(url, requestFuture, requestFuture);
        requestQueue.add(jsonObjectRequest);
        return parseMerriamWebsterResponse(requestFuture.get().toString());
    }

    private Properties properties() {
        Properties properties = new Properties();
        try (InputStream is = getBaseContext().getAssets().open("application.properties")) {
            properties.load(is);
        } catch (IOException e) {
            runOnUiThread(() -> Toast.makeText(context, "Can't load properties.", LENGTH_SHORT).show());
        }
        return properties;
    }

    private void setRequestQueue() {
        this.requestQueue = Volley.newRequestQueue(this);
    }

    public void offlineActivity(View view) {
        Intent intent = new Intent(this, MainActivity4.class);
        startActivity(intent);
    }

    public void offlineMode(View view) {
        offline = !offline;
        runOnUiThread(() -> Toast.makeText(context, offline ? "You are offline." : "You are online.", LENGTH_SHORT).show());
        offlineActivityButton.setVisibility(offline ? VISIBLE : INVISIBLE);
    }

    public void deleteButton(View view) {
        try {
            fileService.delete(originalLookupWord);
            definitionsView.setText(format("'%s' has been deleted from the offline files.", originalLookupWord));
            deleteButton.setVisibility(INVISIBLE);
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "error...", e);
            definitionsView.setText(format("Error deleting '%s'. %s ", originalLookupWord, ExceptionUtils.getStackTrace(e)));
        }
    }

    private void doLookup(String word) {
        lookupWord.setText(word);
        doLookup();
    }

    public boolean isOffline() {
        try {
            Process p = Runtime.getRuntime().exec("ping -c 1 google.com");
            return p.waitFor(500L, TimeUnit.MILLISECONDS) && p.exitValue() != 0;
        } catch (Exception e) {
            return true;
        }
    }
}
