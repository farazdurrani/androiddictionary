package com.faraz.dictionary;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.Completable.runSync;
import static com.faraz.dictionary.DBResult.INSERT;
import static com.faraz.dictionary.DBResult.UPDATE;
import static com.faraz.dictionary.MainActivity2.JAVAMAIL_PASS;
import static com.faraz.dictionary.MainActivity2.JAVAMAIL_USER;
import static com.faraz.dictionary.OfflineAndDeletedWordsActivity.LOOKUPTHISWORD;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.runAsync;
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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
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
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class MainActivity extends AppCompatActivity {
  public static final String FILE_NAME = "FILE_NAME";
  public static final List<String> AUTO_COMPLETE_WORDS_REMOVE = new ArrayList<>();
  public static final Consumer<Object> NOOP = ignore -> {
  };
  static final String CHICAGO = "America/Chicago";
  private static final String NO_DEFINITION_FOUND = "No definitions found for '%s'. Perhaps, you meant:";
  private static final String REGEX_WHITE_SPACES = "\\s+";
  private static final String MERRIAM_WEBSTER_KEY = "dictionary.merriamWebster.key";
  private static final String MERRIAM_WEBSTER_URL = "dictionary.merriamWebster.url";
  public static Properties properties;
  private final List<String> AUTO_COMPLETE_WORDS = new ArrayList<>();
  private RequestQueue requestQueue;
  private Repository repository;
  private FileService offlineWordsFileService;
  private Context context;
  private AutoCompleteTextView lookupWord;
  private String originalLookupWord;
  private TextView googleLink;
  private TextView definitionsView;
  private TextView saveView;
  private Button deleteButton;
  private Button offlineActivityButton;
  private boolean offline;

  @Override
  protected void onResume() {
    super.onResume();
    if (!AUTO_COMPLETE_WORDS_REMOVE.isEmpty()) {
      runOnUiThread(() -> lookupWord.setHint(EMPTY));
      AUTO_COMPLETE_WORDS.removeAll(AUTO_COMPLETE_WORDS_REMOVE);
      List<String> offlineWords = offlineWordsFileService.readFile();
      offlineWords.removeAll(AUTO_COMPLETE_WORDS_REMOVE);
      Optional.of(offlineWords).filter(ObjectUtils::isNotEmpty)
              .ifPresentOrElse(ow -> offlineWordsFileService.writeFileExternalStorage(false,
                      String.join(lineSeparator(), ow)), () -> offlineWordsFileService.clearFile());
      AUTO_COMPLETE_WORDS_REMOVE.clear();
      runOnUiThread(() -> lookupWord.setAdapter(new ArrayAdapter<>(this, android.R.layout.select_dialog_item,
              AUTO_COMPLETE_WORDS)));
      runOnUiThread(() -> lookupWord.setHint(AUTO_COMPLETE_WORDS.size() + " autocomplete words."));
    }
  }

  @SuppressLint("InlinedApi")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    offlineActivityButton = findViewById(R.id.offlineActivity);
    offlineActivityButton.setVisibility(INVISIBLE);
    context = getBaseContext();
    offlineWordsFileService = new FileService("offlinewords.txt", Optional.ofNullable(getExternalFilesDir(null))
            .map(File::getAbsolutePath).orElseThrow());
    lookupWord = findViewById(R.id.wordBox);
    lookupWord.setThreshold(1);
    lookupWord.setAdapter(new ArrayAdapter<>(this, android.R.layout.select_dialog_item, AUTO_COMPLETE_WORDS));
    definitionsView = findViewById(R.id.definitions);
    definitionsView.setMovementMethod(new ScrollingMovementMethod());
    googleLink = findViewById(R.id.google);
    saveView = findViewById(R.id.save);
    deleteButton = findViewById(R.id.deleteButton);
    deleteButton.setVisibility(INVISIBLE);
    setRequestQueue();
    setOpenInBrowserListener();
    setLookupWordListener();
    setStoreWordListener();
    Optional.of(isOffline()).ifPresent(this::setOfflineFlagAndButton);
    repository = new Repository(loadProperty(JAVAMAIL_USER), loadProperty(JAVAMAIL_PASS));
    runSync(this::loadWordsForAutoComplete).thenRunSync(() -> ofNullable(getIntent().getExtras())
            .map(e -> e.getString(LOOKUPTHISWORD)).ifPresent(this::doLookup));
  }

  /**
   * Call this method only at startup!
   */
  @SuppressLint("NewApi")
  private void loadWordsForAutoComplete() {
    List<View> views = findViewById(R.id.mainactivity).getTouchables();
    runOnUiThread(() -> views.forEach(v -> v.setEnabled(false)));
    runOnUiThread(() -> lookupWord.setHint("autocomplete is loading. please wait..."));
    AUTO_COMPLETE_WORDS.clear();
    AUTO_COMPLETE_WORDS.addAll(repository.getWords());
    runOnUiThread(() -> lookupWord.setHint(AUTO_COMPLETE_WORDS.size() + " autocomplete words."));
    ofNullable(getIntent().getExtras()).map(e -> e.getString(LOOKUPTHISWORD)).ifPresentOrElse(NOOP,
            () -> runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    format("Loaded %s words for autocomplete.", AUTO_COMPLETE_WORDS.size()), LENGTH_SHORT).show()));
    runOnUiThread(() -> views.forEach(v -> v.setEnabled(true)));
  }

  public void goTo2ndActivity(View view) {
    Intent intent = new Intent(this, MainActivity2.class);
    startActivity(intent);
  }

  public void offlineActivity(View view) {
    Intent intent = new Intent(this, OfflineAndDeletedWordsActivity.class);
    intent.putExtra(FILE_NAME, "offlinewords.txt");
    startActivity(intent);
  }

  public void offlineMode(View view) {
    offline = !offline;
    runOnUiThread(() -> offlineActivityButton.setVisibility(offline ? VISIBLE : INVISIBLE));
  }

  @SuppressLint("NewApi")
  public void deleteButton(View view) {
    try {
      offlineWordsFileService.delete(originalLookupWord);
      runOnUiThread(() -> deleteButton.setVisibility(INVISIBLE));
    } catch (Exception e) {
      Log.e(this.getClass().getSimpleName(), "error...", e);
      runOnUiThread(() -> definitionsView.setText(format("Error deleting '%s'. %s ", originalLookupWord,
              ExceptionUtils.getStackTrace(e))));
    }
  }

  private void setOfflineFlagAndButton(boolean isOffline) {
    offline = isOffline;
    runOnUiThread(() -> offlineActivityButton.setVisibility(offline ? VISIBLE : INVISIBLE));
  }

  private void setStoreWordListener() {
    saveView.setOnClickListener(ignoreView -> storeWord());
  }

  private void setOpenInBrowserListener() {
    googleLink.setOnClickListener(ignore -> runAsync(this::openInWebBrowser));
  }

  private void setLookupWordListener() {
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
    runOnUiThread(() -> offlineActivityButton.setVisibility(offline ? VISIBLE : INVISIBLE));
    doInitialWork();
    Optional.of(offline).filter(BooleanUtils::isTrue).ifPresent(ignore -> runAsync(this::writeToOfflineFile));
    Optional.of(offline).filter(BooleanUtils::isFalse).ifPresent(this::lookupAndstoreInDbAndOpenBrowser);
  }

  private void lookupAndstoreInDbAndOpenBrowser(boolean ignore) {
    runAsync(this::lookupWord);
    openInWebBrowser();
  }

  @SuppressLint("SetTextI18n")
  private void writeToOfflineFile() {
    if (StringUtils.isNotBlank(originalLookupWord)) {
      offlineWordsFileService.writeFileExternalStorage(true, originalLookupWord);
      runOnUiThread(() -> definitionsView.setText(format("'%s' has been stored offline.", originalLookupWord)));
    } else {
      runOnUiThread(() -> definitionsView.setText("...yeah we don't store empty words buddy!"));
    }
  }

  private String formMerriamWebsterUrl() {
    String word = originalLookupWord;
    String mk = loadProperty(MERRIAM_WEBSTER_KEY);
    String mUrl = loadProperty(MERRIAM_WEBSTER_URL);
    return format(mUrl, word, mk);
  }

  @SuppressLint({"SetTextI18n", "NewApi"})
  private void lookupWord() {
    try {
      if (existingWord()) {
        runOnUiThread(() -> definitionsView.setText(format("'%s's already looked-up.", originalLookupWord)));
        deleteButton(null);
        markWordsAsReminded();
        return;
      }
      String[] definition = lookupInMerriamWebster();
      runOnUiThread(() -> definitionsView.setText(definition[1]));
      Optional.of(definition[0]).map(BooleanUtils::toBoolean).filter(BooleanUtils::isTrue)
              .ifPresent(ignore -> runAsync(this::saveWordInDb).thenRunAsync(this::storeWordInAutoComplete));
      Optional.of(definition[0]).map(BooleanUtils::toBoolean).filter(BooleanUtils::isFalse)
              .flatMap(ignore -> offlineWordsFileService.readFile().stream().filter(originalLookupWord::equals)
                      .findFirst())
              .ifPresent(_ignore -> runOnUiThread(() -> deleteButton.setVisibility(VISIBLE)));
    } catch (Exception e) {
      runOnUiThread(() -> definitionsView.setText(format("welp...%s%s%s", originalLookupWord, lineSeparator(),
              getStackTrace(e))));
      writeToOfflineFile();
    }
  }

  private boolean existingWord() {
    return AUTO_COMPLETE_WORDS.contains(originalLookupWord);
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
    runOnUiThread(() -> lookupWord.setText(null));
  }

  private String cleanWord() {
    return Arrays.stream(lookupWord.getText().toString().split(REGEX_WHITE_SPACES)).map(String::trim)
            .map(String::toLowerCase).collect(joining(SPACE)).trim();
  }

  private String loadProperty(String property) {
    if (properties == null) {
      properties = new Properties();
      try (InputStream is = getBaseContext().getAssets().open("application.properties")) {
        properties.load(is);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return properties.getProperty(property);
  }

  private void storeWord() {
    Optional.of(ofNullable(originalLookupWord).orElse(EMPTY)).filter(StringUtils::isBlank).ifPresent(
            ignore -> runOnUiThread(() -> Toast.makeText(MainActivity.this, "Nothing to save.", LENGTH_SHORT).show()));
    ofNullable(originalLookupWord).filter(StringUtils::isNotBlank).ifPresent(this::storeWord);
  }

  private void storeWord(String ignore) {
    runOnUiThread(() -> offlineActivityButton.setVisibility(offline ? VISIBLE : INVISIBLE));
    Optional.of(offline).filter(BooleanUtils::isTrue).ifPresent(_ignore -> runAsync(this::writeToOfflineFile));
    try {
      Optional.of(offline).filter(BooleanUtils::isFalse).ifPresent(_ignore ->
              runAsync(this::firstCheckIfExistsAndIfNotThenSaveWordInDb).thenRunAsync(this::storeWordInAutoComplete));
    } catch (Exception e) {
      runOnUiThread(() -> definitionsView.setText(format("welp...%s%s%s", originalLookupWord, lineSeparator(),
              getStackTrace(e))));
      writeToOfflineFile();
    }
  }

  private void storeWordInAutoComplete() {
    runOnUiThread(() -> lookupWord.setHint(EMPTY));
    if (!AUTO_COMPLETE_WORDS.contains(originalLookupWord)) {
      AUTO_COMPLETE_WORDS.add(originalLookupWord);
    }
    runOnUiThread(() -> {
      lookupWord.setAdapter(new ArrayAdapter<>(this, android.R.layout.select_dialog_item,
              AUTO_COMPLETE_WORDS));
      lookupWord.setHint(AUTO_COMPLETE_WORDS.size() + " autocomplete words.");
    });
  }

  private void firstCheckIfExistsAndIfNotThenSaveWordInDb() {
    try {
      if (existingWord()) {
        runOnUiThread(() -> definitionsView.setText(format("'%s's already stored.", originalLookupWord)));
        deleteButton(null);
        markWordsAsReminded();
        return;
      }
      saveWordInDb();
    } catch (Exception e) {
      writeToOfflineFile();
    }
  }

  private void saveWordInDb(boolean... ignore) {
    try {
      saveWordInInMemoryDb();
      deleteButton(null);
    } catch (Exception e) {
      runOnUiThread(() -> definitionsView.setText(format("welp...%s%s%s", originalLookupWord, lineSeparator(),
              getStackTrace(e))));
      throw new RuntimeException();
    }
  }

  private void saveWordInInMemoryDb() {
    DBResult response = repository.upsert(originalLookupWord);
    Optional.of(response).filter(INSERT::equals).ifPresent(ignore -> runOnUiThread(
            () -> definitionsView.setText(format("'%s's saved!", capitalize(originalLookupWord)))));
    Optional.of(response).filter(UPDATE::equals).ifPresent(ignore -> runOnUiThread(() -> definitionsView.setText(
            format("%s%s's already looked-up.", lineSeparator(), capitalize(originalLookupWord)))));
  }

  private String[] parseMerriamWebsterResponse(String json) {
    Map<String, Object> flattenJson = JsonFlattener.flattenAsMap(json);
    List<Object> orig = new ArrayList<>(flattenJson.values());
    flattenJson.keySet().removeIf(x -> !x.contains("shortdef"));
    if (flattenJson.values().isEmpty()) {
      orig.add(0, format(NO_DEFINITION_FOUND, originalLookupWord));
      String alternativeWords =
              orig.stream().filter(Objects::nonNull).filter(String.class::isInstance).map(String.class::cast)
                      .filter(StringUtils::isNotBlank).map(lineSeparator()::concat).map("----------"::concat)
                      .collect(joining(lineSeparator()));
      return new String[]{"false", alternativeWords};
    } else {
      String result = flattenJson.values().stream().filter(Objects::nonNull).filter(String.class::isInstance)
              .map(String.class::cast).limit(3).filter(StringUtils::isNotBlank).map(lineSeparator()::concat)
              .map("----------"::concat).collect(joining(lineSeparator()));
      String definition = result +
              orig.stream().filter(Objects::nonNull).filter(String.class::isInstance).map(String.class::cast)
                      .filter(StringUtils::isNotBlank).filter(x -> x.contains("\\{wi}") && x.contains("\\{/wi}"))
                      .map(x -> x.replaceAll("\\{wi}", EMPTY)).map(x -> x.replaceAll("\\{/wi}", EMPTY))
                      .map("// "::concat).collect(joining(lineSeparator()));
      return new String[]{"true", definition};
    }
  }

  @SuppressLint("SetTextI18n")
  private String[] lookupInMerriamWebster() {
    String url = formMerriamWebsterUrl();
    RequestFuture<JSONArray> requestFuture = RequestFuture.newFuture();
    JsonArrayRequest jsonObjectRequest = new JsonArrayRequest(url, requestFuture, requestFuture);
    requestQueue.add(jsonObjectRequest);
    try {
      return parseMerriamWebsterResponse(requestFuture.get().toString());
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private void setRequestQueue() {
    this.requestQueue = Volley.newRequestQueue(this);
  }

  private void doLookup(String word) {
    lookupWord.setText(word); //TODO perhaps you never want to execute 'setText' on a ui thread this one time.
    doLookup();
  }

  private void markWordsAsReminded() {
    repository.upsert(originalLookupWord);
  }

  @SuppressLint("NewApi")
  public boolean isOffline() {
    try {
      Process p = Runtime.getRuntime().exec("ping -c 1 google.com");
      return p.waitFor(555L, TimeUnit.MILLISECONDS) && p.exitValue() != 0;
    } catch (Exception e) {
      return true;
    }
  }
}
