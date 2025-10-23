package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static java.lang.Integer.parseInt;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.icu.math.BigDecimal;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class MainActivity3 extends AppCompatActivity {

  private static final String TAG = MainActivity3.class.getSimpleName();
  private static final String NUMBER_OF_WORDS_TO_SHOW = "number.of.words.to.show";
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private String[] words;
  private TextView remindedWordCountView;
  private ListView listView;
  private Context context;
  private Properties properties;
  private Repository repository;

  @SuppressLint("SetTextI18n")
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.activity_main3);
    context = getBaseContext();
    properties = properties();
    listView = findViewById(R.id.wordsList);
    setWordsListListener();
    remindedWordCountView = findViewById(R.id.remindedWordsCount);
    repository = new Repository();
    toggleButtons(false);
    runOnUiThread(() -> remindedWordCountView.setText("Loading..."));
    runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, new String[0])));
    CompletableFuture.runAsync(() -> {
      try {
        List<String> _words = repository.getWordsForReminder(parseInt(properties.getProperty(NUMBER_OF_WORDS_TO_SHOW)));
        showWordsAndCount(_words);
        toggleButtons(true);
      } catch (Exception e) {
        Log.e(TAG, e.getLocalizedMessage(), e);
        runOnUiThread(() -> Toast.makeText(context, "Mongo has gone belly up!", LENGTH_SHORT).show());
      }
    });
  }

  private void setWordsListListener() {
    listView.setOnItemClickListener((parent, view, position, id) -> {
      String word = (String) listView.getAdapter().getItem(position);
      Uri uri = Uri.parse(String.format("https://www.google.com/search?q=define: %s", word));
      startActivity(new Intent(Intent.ACTION_VIEW, uri));
    });
  }

  private void showWordsAndCount(List<String> _words) {
    words = _words.toArray(new String[0]);
    ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.custom_layout, words);
    runOnUiThread(() -> listView.setAdapter(adapter));
    String remindedWordCount = repository.getRemindedCount() == repository.getLength() ? "All" :
            String.valueOf(repository.getRemindedCount());
    String percentage = toPercentageOf(repository.getRemindedCount(), repository.getLength());
    runOnUiThread(() -> remindedWordCountView.setText(String.format("'%s (%s%%)' words have been marked as reminded.",
            remindedWordCount, percentage)));
  }

  private void toggleButtons(boolean visible) {
    runOnUiThread(() -> findViewById(R.id.markAsReminded).setEnabled(visible));
    runOnUiThread(() -> findViewById(R.id.undoRemind).setEnabled(visible));
  }

  @SuppressLint("SetTextI18n")
  public void undoRemind(View view) {
    toggleButtons(false);
    runOnUiThread(() -> remindedWordCountView.setText("Loading..."));
    runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, new String[0])));
    CompletableFuture.runAsync(() -> {
      try {
        List<String> _words = repository.getByRemindedTime(parseInt(properties.getProperty(NUMBER_OF_WORDS_TO_SHOW)));
        repository.unsetRemindedTime(_words);
        clearWords();
        showWordsAndCount(_words);
      } catch (Exception e) {
        runOnUiThread(() -> Toast.makeText(context, "Not sure what went wrong.", LENGTH_LONG).show());
      }
    }).thenRun(this::sleepThenEnableButtons);
  }

  @SuppressLint("SetTextI18n")
  public void markWordsAsReminded(View view) {
    toggleButtons(false);
    runOnUiThread(() -> remindedWordCountView.setText("Loading..."));
    runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, new String[0])));
    CompletableFuture.runAsync(() -> {
      try {
        repository.markAsReminded(Arrays.asList(words));
        clearWords();
        List<String> _words = repository.getWordsForReminder(parseInt(properties.getProperty(NUMBER_OF_WORDS_TO_SHOW)));
        showWordsAndCount(_words);
      } catch (Exception e) {
        Log.e(TAG, e.getLocalizedMessage(), e);
        runOnUiThread(() -> Toast.makeText(context, "Not sure what went wrong.", LENGTH_LONG).show());
      }
    }).thenRun(this::sleepThenEnableButtons);
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

  public String toPercentageOf(long value, int total) {
    String perc = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_EVEN.ordinal())
            .multiply(ONE_HUNDRED).toBigDecimal().toPlainString();
    return perc.substring(0, perc.length() > 4 ? 5 : perc.length());
  }

  /**
   * TODO: The stop-gap solution to avoid reading of the data that hasn't been written yet due to the writing of the
   * TODO: data in an asynchronous fashion.
   */
  private void sleepThenEnableButtons() {
    try {
      Thread.sleep(1000L);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    toggleButtons(true);
  }
}
