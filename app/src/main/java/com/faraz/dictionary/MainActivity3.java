package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.CollectionOptional.ofEmptyable;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.runAsync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class MainActivity3 extends AppCompatActivity {

  private static final String activity = MainActivity3.class.getSimpleName();
  private static final String NUMBER_OF_WORDS_TO_SHOW = "number.of.words.to.show";
  private String[] words;
  private TextView remindedWordCountView;
  private ListView listView;
  private Context context;
  private Properties properties;
  private Repository repository;

  @SuppressLint("SetTextI18n")
  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.activity_main3);
    context = getBaseContext();
    properties = properties();
    listView = findViewById(R.id.wordsList);
    remindedWordCountView = findViewById(R.id.remindedWordsCount);
    repository = new Repository();
    toggleButtons(false);
    runOnUiThread(() -> remindedWordCountView.setText("Loading..."));
    runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, new String[0])));
    runAsync(() -> {
      try {
        fetchWordsOr(true, Collections.emptyList());
        toggleButtons(true);
      } catch (Exception e) {
        Log.e(activity, e.getLocalizedMessage(), e);
        runOnUiThread(() -> Toast.makeText(context, "Mongo has gone belly up!", LENGTH_SHORT).show());
      }
    });
  }

  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  @SuppressLint("SetTextI18n")
  private void fetchWordsOr(boolean setItemClickListener, List<String> showWords) {
    words = ofEmptyable(showWords).orElseGet(
                    () -> repository.getWordsForReminder(parseInt(properties.getProperty(NUMBER_OF_WORDS_TO_SHOW))))
            .toArray(new String[0]);
    ArrayUtils.reverse(words);
    ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.custom_layout, words);
    runOnUiThread(() -> listView.setAdapter(adapter));
    if (setItemClickListener) {
      listView.setOnItemClickListener((parent, view, position, id) -> {
        String word = (String) listView.getAdapter().getItem(position);
        Uri uri = Uri.parse(format("https://www.google.com/search?q=define: %s", word));
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
      });
    }
    String remindedWordCount = Optional.of(repository.getRemindedCount()).filter(count -> count > 0)
            .map(String::valueOf).orElse("Can't find none.");
    runOnUiThread(() -> remindedWordCountView.setText(format("'%s' words have been marked as reminded.",
            remindedWordCount)));
  }

  private void toggleButtons(boolean visible) {
    runOnUiThread(() -> findViewById(R.id.markAsReminded).setEnabled(visible));
    runOnUiThread(() -> findViewById(R.id.undoRemind).setEnabled(visible));
  }

  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  @SuppressLint("SetTextI18n")
  public void undoRemind(View view) {
    toggleButtons(false);
    runOnUiThread(() -> remindedWordCountView.setText("Loading..."));
    runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, new String[0])));
    runAsync(() -> {
      try {
        List<String> words = repository.getByRemindedTime(parseInt(properties.getProperty(NUMBER_OF_WORDS_TO_SHOW)));
        repository.unsetRemindedTime(words);
        clearWords();
        fetchWordsOr(false, words);
        toggleButtons(true);
      } catch (Exception e) {
        runOnUiThread(() -> Toast.makeText(context, "Not sure what went wrong.", LENGTH_LONG).show());
      }
    });
  }

  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  @SuppressLint("SetTextI18n")
  public void markWordsAsReminded(View view) {
    toggleButtons(false);
    runOnUiThread(() -> remindedWordCountView.setText("Loading..."));
    runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, new String[0])));
    runAsync(() -> {
      try {
        repository.markAsReminded(Arrays.asList(words));
        clearWords();
        fetchWordsOr(false, Collections.emptyList());
        toggleButtons(true);
      } catch (Exception e) {
        Log.e(activity, e.getLocalizedMessage(), e);
        runOnUiThread(() -> Toast.makeText(context, "Not sure what went wrong.", LENGTH_LONG).show());
      }
    });
  }

  private void clearWords() {
    words = null;
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
}