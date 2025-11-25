package com.faraz.dictionary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.faraz.dictionary.httpclient.HttpClient;
import com.github.wnameless.json.flattener.JsonFlattener;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
  public static final String FILE_NAME = "FILE_NAME";
  public static final List<String> AUTO_COMPLETE_WORDS = new ArrayList<>();
  public static final List<String> AUTO_COMPLETE_WORDS_REMOVE = new ArrayList<>();
  public static final Consumer<Object> NOOP = ignore -> {
  };
  public static final String TAG = MainActivity.class.getSimpleName();
  public static final String CHICAGO = "America/Chicago";
  public static final String REGEX_WHITE_SPACES = "\\s+";
  private static final String NO_DEFINITION_FOUND = "No definitions found for '%s'. Perhaps, you meant:";
  private static final String MERRIAM_WEBSTER_KEY = "dictionary.merriamWebster.key";
  private static final String MERRIAM_WEBSTER_URL = "dictionary.merriamWebster.url";
  public static Properties properties;
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
      runOnUiThread(() -> lookupWord.setHint(StringUtils.EMPTY));
      AUTO_COMPLETE_WORDS.removeAll(AUTO_COMPLETE_WORDS_REMOVE);
      List<String> offlineWords = offlineWordsFileService.readFile();
      if (!Collections.disjoint(offlineWords, AUTO_COMPLETE_WORDS)) {
        offlineWords.removeAll(AUTO_COMPLETE_WORDS_REMOVE);
        Optional.of(offlineWords).filter(ObjectUtils::isNotEmpty)
                .ifPresentOrElse(ow -> offlineWordsFileService.writeFileExternalStorage(false,
                        String.join(System.lineSeparator(), ow)), () -> offlineWordsFileService.clearFile());
      }
      AUTO_COMPLETE_WORDS_REMOVE.clear();
      runOnUiThread(() -> lookupWord.setAdapter(
              new ShowRemindedArrayAdapter(this, android.R.layout.select_dialog_item, AUTO_COMPLETE_WORDS)));
      runOnUiThread(() -> lookupWord.setHint(AUTO_COMPLETE_WORDS.size() + " autocomplete words."));
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    offlineActivityButton = findViewById(R.id.offlineActivity);
    offlineActivityButton.setVisibility(View.INVISIBLE);
    context = getBaseContext();
    offlineWordsFileService = new FileService("offlinewords.txt", Optional.ofNullable(getExternalFilesDir(null))
            .map(File::getAbsolutePath).orElseThrow());
    lookupWord = findViewById(R.id.wordBox);
    lookupWord.setThreshold(1);
    lookupWord.setAdapter(new ShowRemindedArrayAdapter(this, android.R.layout.select_dialog_item, AUTO_COMPLETE_WORDS));
    definitionsView = findViewById(R.id.definitions);
    definitionsView.setMovementMethod(new ScrollingMovementMethod());
    googleLink = findViewById(R.id.google);
    saveView = findViewById(R.id.save);
    deleteButton = findViewById(R.id.deleteButton);
    deleteButton.setVisibility(View.INVISIBLE);
    setOpenInBrowserListener();
    setLookupWordListener();
    setStoreWordListener();
    Optional.of(isOffline()).ifPresent(this::setOfflineFlagAndButton);
    repository = new Repository();
    Completable.runSync(this::loadWordsForAutoComplete).thenRunSync(() -> Optional.ofNullable(getIntent().getExtras())
            .map(e -> e.getString(OfflineAndDeletedWordsActivity.LOOKUPTHISWORD)).ifPresent(this::doLookup));
  }

  /**
   * Call this method only at startup!
   */
  private void loadWordsForAutoComplete() {
    List<View> views = findViewById(R.id.mainactivity).getTouchables();
    runOnUiThread(() -> views.forEach(v -> v.setEnabled(false)));
    runOnUiThread(() -> lookupWord.setHint("autocomplete is loading. please wait..."));
    AUTO_COMPLETE_WORDS.clear();
    AUTO_COMPLETE_WORDS.addAll(repository.getWords());
    runOnUiThread(() -> lookupWord.setHint(AUTO_COMPLETE_WORDS.size() + " autocomplete words."));
    Optional.ofNullable(getIntent().getExtras()).map(e -> e.getString(OfflineAndDeletedWordsActivity.LOOKUPTHISWORD))
            .ifPresentOrElse(NOOP, () -> runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    String.format("Loaded %s words for autocomplete.", AUTO_COMPLETE_WORDS.size()),
                    Toast.LENGTH_SHORT).show()));
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
    runOnUiThread(() -> offlineActivityButton.setVisibility(offline ? View.VISIBLE : View.INVISIBLE));
  }

  public void deleteButton(View view) {
    try {
      offlineWordsFileService.delete(originalLookupWord);
      runOnUiThread(() -> deleteButton.setVisibility(View.INVISIBLE));
    } catch (Exception e) {
      Log.e(TAG, ExceptionUtils.getStackTrace(e));
      runOnUiThread(() -> definitionsView.setText(String.format("Error deleting '%s'. %s ", originalLookupWord,
              ExceptionUtils.getStackTrace(e))));
    }
  }

  private void setOfflineFlagAndButton(boolean isOffline) {
    offline = isOffline;
    runOnUiThread(() -> offlineActivityButton.setVisibility(offline ? View.VISIBLE : View.INVISIBLE));
  }

  private void setStoreWordListener() {
    saveView.setOnClickListener(ignoreView -> storeWord());
  }

  private void setOpenInBrowserListener() {
    googleLink.setOnClickListener(ignore -> CompletableFuture.runAsync(this::openInWebBrowser));
  }

  private void setLookupWordListener() {
    lookupWord.setOnKeyListener((view, code, event) -> {
      if ((event.getAction() == KeyEvent.ACTION_DOWN) && (code == KeyEvent.KEYCODE_ENTER)) {
        doLookup();
        return true;
      }
      return false;
    });
    lookupWord.setOnFocusChangeListener((view, b) -> deleteButton.setVisibility(View.INVISIBLE));
  }

  private void doLookup() {
    runOnUiThread(() -> offlineActivityButton.setVisibility(offline ? View.VISIBLE : View.INVISIBLE));
    doInitialWork();
    Optional.of(offline).filter(BooleanUtils::isTrue)
            .ifPresent(ignore -> CompletableFuture.runAsync(this::writeToOfflineFile));
    Optional.of(offline).filter(BooleanUtils::isFalse).ifPresent(this::lookupAndstoreInDbAndOpenBrowser);
  }

  private void lookupAndstoreInDbAndOpenBrowser(boolean ignore) {
    CompletableFuture.runAsync(this::lookupWord);
    openInWebBrowser();
  }

  @SuppressLint("SetTextI18n")
  private void writeToOfflineFile() {
    if (StringUtils.isNotBlank(originalLookupWord)) {
      offlineWordsFileService.writeFileExternalStorage(true, originalLookupWord);
      runOnUiThread(() -> definitionsView.setText(String.format("'%s' has been stored offline.", originalLookupWord)));
    } else {
      runOnUiThread(() -> definitionsView.setText("...yeah we don't store empty words buddy!"));
    }
  }

  private String formMerriamWebsterUrl() {
    String word = originalLookupWord;
    String mk = loadProperty(MERRIAM_WEBSTER_KEY);
    String mUrl = loadProperty(MERRIAM_WEBSTER_URL);
    return String.format(mUrl, word, mk);
  }

  private void lookupWord() {
    try {
      if (existingWord()) {
        runOnUiThread(() -> definitionsView.setText(String.format("'%s's already looked-up.", originalLookupWord)));
        deleteButton(null);
        markWordsAsReminded();
        return;
      }
      String[] definition = lookupInMerriamWebster();
      runOnUiThread(() -> definitionsView.setText(definition[1]));
      Optional.of(definition[0]).map(BooleanUtils::toBoolean).filter(BooleanUtils::isTrue)
              .ifPresent(ignore -> CompletableFuture.runAsync(this::saveWordInDb)
                      .thenRun(this::storeWordInAutoComplete));
      Optional.of(definition[0]).map(BooleanUtils::toBoolean).filter(BooleanUtils::isFalse)
              .flatMap(ignore -> offlineWordsFileService.readFile().stream().filter(originalLookupWord::equals)
                      .findFirst())
              .ifPresent(_ignore -> runOnUiThread(() -> deleteButton.setVisibility(View.VISIBLE)));
    } catch (Exception e) {
      runOnUiThread(() -> definitionsView.setText(String.format("welp...%s%s%s", originalLookupWord,
              System.lineSeparator(), ExceptionUtils.getStackTrace(e))));
      writeToOfflineFile();
    }
  }

  private boolean existingWord() {
    return AUTO_COMPLETE_WORDS.contains(originalLookupWord);
  }

  private void openInWebBrowser() {
    if (StringUtils.isBlank(originalLookupWord)) {
      runOnUiThread(() -> Toast.makeText(context, "Nothing to lookup", Toast.LENGTH_SHORT).show());
      return;
    }
    Uri uri = Uri.parse(String.format("https://www.google.com/search?q=define: %s", originalLookupWord));
    startActivity(new Intent(Intent.ACTION_VIEW, uri));
  }

  private void doInitialWork() {
    originalLookupWord = cleanWord();
    runOnUiThread(() -> lookupWord.setText(null));
  }

  private String cleanWord() {
    return Arrays.stream(lookupWord.getText().toString().split(REGEX_WHITE_SPACES)).map(String::trim)
            .map(String::toLowerCase).collect(Collectors.joining(StringUtils.SPACE)).trim();
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
    Optional.of(Optional.ofNullable(originalLookupWord).orElse(StringUtils.EMPTY)).filter(StringUtils::isBlank)
            .ifPresent(ignore -> runOnUiThread(() -> Toast.makeText(MainActivity.this, "Nothing to save.",
                    Toast.LENGTH_SHORT).show()));
    Optional.ofNullable(originalLookupWord).filter(StringUtils::isNotBlank).ifPresent(this::storeWord);
  }

  private void storeWord(String ignore) {
    runOnUiThread(() -> offlineActivityButton.setVisibility(offline ? View.VISIBLE : View.INVISIBLE));
    Optional.of(offline).filter(BooleanUtils::isTrue)
            .ifPresent(_ignore -> CompletableFuture.runAsync(this::writeToOfflineFile));
    try {
      Optional.of(offline).filter(BooleanUtils::isFalse).ifPresent(_ignore ->
              CompletableFuture.runAsync(this::firstCheckIfExistsAndIfNotThenSaveWordInDb)
                      .thenRun(this::storeWordInAutoComplete));
    } catch (Exception e) {
      runOnUiThread(() -> definitionsView.setText(String.format("welp...%s%s%s", originalLookupWord,
              System.lineSeparator(), ExceptionUtils.getStackTrace(e))));
      writeToOfflineFile();
    }
  }

  private void storeWordInAutoComplete() {
    runOnUiThread(() -> lookupWord.setHint(StringUtils.EMPTY));
    if (!AUTO_COMPLETE_WORDS.contains(originalLookupWord)) {
      AUTO_COMPLETE_WORDS.add(originalLookupWord);
    }
    runOnUiThread(() -> {
      lookupWord.setAdapter(
              new ShowRemindedArrayAdapter(this, android.R.layout.select_dialog_item, AUTO_COMPLETE_WORDS));
      lookupWord.setHint(AUTO_COMPLETE_WORDS.size() + " autocomplete words.");
    });
  }

  private void firstCheckIfExistsAndIfNotThenSaveWordInDb() {
    try {
      if (existingWord()) {
        runOnUiThread(() -> definitionsView.setText(String.format("'%s's already stored.", originalLookupWord)));
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
      runOnUiThread(() -> definitionsView.setText(String.format("welp...%s%s%s", originalLookupWord,
              System.lineSeparator(), ExceptionUtils.getStackTrace(e))));
      throw new RuntimeException();
    }
  }

  private void saveWordInInMemoryDb() {
    DBResult response = repository.upsert(originalLookupWord);
    Optional.of(response).filter(DBResult.INSERT::equals).ifPresent(ignore -> runOnUiThread(
            () -> definitionsView.setText(String.format("'%s's saved!", StringUtils.capitalize(originalLookupWord)))));
    Optional.of(response).filter(DBResult.UPDATE::equals)
            .ifPresent(ignore -> runOnUiThread(() -> definitionsView.setText(
                    String.format("%s%s's already looked-up.", System.lineSeparator(),
                            StringUtils.capitalize(originalLookupWord)))));
  }

  private String[] parseMerriamWebsterResponse(String json) {
    Map<String, Object> flattenJson = JsonFlattener.flattenAsMap(json);
    List<Object> orig = new ArrayList<>(flattenJson.values());
    flattenJson.keySet().removeIf(x -> !x.contains("shortdef"));
    if (flattenJson.values().isEmpty()) {
      orig.add(0, String.format(NO_DEFINITION_FOUND, originalLookupWord));
      String alternativeWords =
              orig.stream().filter(Objects::nonNull).filter(String.class::isInstance).map(String.class::cast)
                      .filter(StringUtils::isNotBlank).map(System.lineSeparator()::concat).map("----------"::concat)
                      .collect(Collectors.joining(System.lineSeparator()));
      return new String[]{"false", alternativeWords};
    } else {
      String result = flattenJson.values().stream().filter(Objects::nonNull).filter(String.class::isInstance)
              .map(String.class::cast).limit(3).filter(StringUtils::isNotBlank).map(System.lineSeparator()::concat)
              .map("----------"::concat).collect(Collectors.joining(System.lineSeparator()));
      String definition = result +
              orig.stream().filter(Objects::nonNull).filter(String.class::isInstance).map(String.class::cast)
                      .filter(StringUtils::isNotBlank).filter(x -> x.contains("\\{wi}") && x.contains("\\{/wi}"))
                      .map(x -> x.replaceAll("\\{wi}", StringUtils.EMPTY)).map(x -> x.replaceAll("\\{/wi}",
                              StringUtils.EMPTY))
                      .map("// "::concat).collect(Collectors.joining(System.lineSeparator()));
      return new String[]{"true", definition};
    }
  }

  private String[] lookupInMerriamWebster() {
    return parseMerriamWebsterResponse(HttpClient.get(formMerriamWebsterUrl()));
  }

  private void doLookup(String word) {
    lookupWord.setText(word); //TODO perhaps you never want to execute 'setText' on a ui thread this one time.
    doLookup();
  }

  private void markWordsAsReminded() {
    repository.upsert(originalLookupWord);
  }

  public boolean isOffline() {
    return pingURL("google.com", 555);
  }

  /**
   * Pings a HTTP URL. This effectively sends a HEAD request and returns <code>true</code> if the response code is in
   * the 200-399 range.
   *
   * @param url     The HTTP URL to be pinged.
   * @param timeout The timeout in millis for both the connection timeout and the response read timeout. Note that
   *                the total timeout is effectively two times the given timeout.
   * @return <code>true</code> if the given HTTP URL has returned response code 200-399 on a HEAD request within the
   * given timeout, otherwise <code>false</code>.
   */
  public static boolean pingURL(String url, int timeout) {
    url = url.replaceFirst("^https", "http"); // Otherwise an exception may be thrown on invalid SSL certificates.
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
      connection.setConnectTimeout(timeout);
      connection.setReadTimeout(timeout);
      connection.setRequestMethod("HEAD");
      int responseCode = connection.getResponseCode();
      return (200 <= responseCode && responseCode <= 399);
    } catch (IOException exception) {
      return false;
    }
  }

  private class ShowRemindedArrayAdapter extends ArrayAdapter<String> {
    public ShowRemindedArrayAdapter(@NonNull Context context, int resource,
                                    @NonNull List<String> objects) {
      super(context, resource, objects);
    }

    @SuppressLint("SetTextI18n")
    @androidx.annotation.NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @androidx.annotation.NonNull ViewGroup parent) {
      TextView view = (TextView) super.getView(position, convertView, parent);
      String text = view.getText().toString();
      if (repository.isReminded(text)) {
        text = "**" + text;
      }
      view.setText(text);
      return view;
    }
  }
}
