package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.MainActivity.AUTO_COMPLETE_WORDS_REMOVE;
import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.Arrays;
import java.util.Optional;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity5 extends AppCompatActivity {

  public static final String WIPEOUT_DATA_BUTTON = "WIPEOUT_DATA_BUTTON";
  private static final String FILE_NAME = "FILE_NAME";
  private ListView listView;
  private Context context;
  private FileService fileService;
  private Repository repository;
  private String[] words;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.activity_main5);
    context = getBaseContext();
    listView = findViewById(R.id.wordsList);
    setListener();
    fileService = new FileService("deletedwords.txt");
    repository = new Repository();
    supplyAsync(this::getLastFewWords).thenAccept(_words -> {
      words = _words;
      runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, words)));
    });
  }

  public void deletedWordsActivity(View view) {
    Intent intent = new Intent(this, OfflineAndDeletedWordsActivity.class);
    intent.putExtra(FILE_NAME, "deletedwords.txt");
    intent.putExtra(WIPEOUT_DATA_BUTTON, true);
    startActivity(intent);
  }

  private void setListener() {
    listView.setOnItemClickListener((parent, view, position, id) -> {
      String word = (String) listView.getAdapter().getItem(position);
      try {
        writeToTextFile(word);
        deleteFromDb(word);
        deleteWordFromAutoComplete(word);
      } catch (Exception exc) {
        Optional.of(exc).ifPresent(e -> runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context,
                R.layout.custom_layout, new String[]{word, ExceptionUtils.getStackTrace(e)}))));
      }
    });
  }

  @SuppressLint("NewApi")
  private void deleteWordFromAutoComplete(String word) {
    AUTO_COMPLETE_WORDS_REMOVE.add(word);
  }

  private void deleteFromDb(String word) {
    repository.remove(word);
    words = Arrays.stream(words).filter(w -> !w.equals(word)).toArray(String[]::new);
    ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.custom_layout, words);
    runOnUiThread(() -> {
      listView.setAdapter(adapter);
      Toast.makeText(context, format("%s has been deleted.", word), LENGTH_SHORT).show();
    });
  }

  private void writeToTextFile(String word) {
    fileService.writeFileExternalStorage(true, word);
  }

  private String[] getLastFewWords() {
    String[] words = repository.getWords().toArray(new String[0]);
    ArrayUtils.reverse(words);
    return words;
  }
}
