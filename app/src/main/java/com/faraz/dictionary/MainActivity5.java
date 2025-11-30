package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.MainActivity.AUTO_COMPLETE_WORDS_REMOVE;
import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MainActivity5 extends AppCompatActivity {

  public static final String WIPEOUT_DATA_BUTTON = "WIPEOUT_DATA_BUTTON";
  private static final String FILE_NAME = "FILE_NAME";
  private ListView listView;
  private AutoCompleteTextView lookupWord;
  private Context context;
  private FileService fileService;
  private Repository repository;
  private List<String> words;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.activity_main5);
    context = this;
    listView = findViewById(R.id.wordsList);
    setListener();
    fileService = new FileService("deletedwords.txt");
    repository = new Repository();
    supplyAsync(this::getAllWords).thenAccept(_words -> {
      words = _words;
      runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, words)));
    }).thenAccept(ignore -> {
      lookupWord = findViewById(R.id.deleteWordBox);
      lookupWord.setThreshold(1);
      runOnUiThread(() -> lookupWord.setAdapter(new ShowRemindedArrayAdapter(this, android.R.layout.select_dialog_item,
              words)));
      setAutocompleteListener();
    });
  }

  private void setAutocompleteListener() {
    lookupWord.setOnItemClickListener((adapterViewParent, view, position, id) -> {
      String w = (String) lookupWord.getAdapter().getItem(position);
      runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, List.of(w))));
    });
  }

  private void setListener() {
    listView.setOnItemClickListener((parent, view, position, id) -> {
      String word = (String) listView.getAdapter().getItem(position);
      new AlertDialog.Builder(context).setTitle("Confirm Action")
              .setMessage(String.format(Locale.US, "Are you sure you want to delete this word %s?", word))
              .setPositiveButton("Yes", (dialog, which) -> {
                dialog.dismiss();
                try {
                  writeToTextFile(word);
                  deleteFromDb(word);
                  deleteWordFromAutoComplete(word);
                  restoreAutocompleteBox();
                } catch (Exception exc) {
                  Optional.of(exc).ifPresent(e -> runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context,
                          R.layout.custom_layout, List.of(word, ExceptionUtils.getStackTrace(e))))));
                }
              }).setNegativeButton("No", (dialog, which) -> runOnUiThread(() -> Toast.makeText(context, "Fine.",
                      LENGTH_LONG).show())).show();
    });
  }

  private void restoreAutocompleteBox() {
    runOnUiThread(() -> {
      lookupWord.setAdapter(new ShowRemindedArrayAdapter(this, android.R.layout.select_dialog_item, words));
      lookupWord.setText(null);
      lookupWord.setHint("Enter a word to delete");
    });
  }

  public void deletedWordsActivity(View view) {
    Intent intent = new Intent(this, OfflineAndDeletedWordsActivity.class);
    intent.putExtra(FILE_NAME, "deletedwords.txt");
    intent.putExtra(WIPEOUT_DATA_BUTTON, true);
    startActivity(intent);
  }

  private void deleteWordFromAutoComplete(String word) {
    AUTO_COMPLETE_WORDS_REMOVE.add(word);
  }

  private void deleteFromDb(String word) {
    repository.delete(word);
    words = words.stream().filter(w -> !w.equals(word)).toList();
    ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.custom_layout, words);
    runOnUiThread(() -> {
      listView.setAdapter(adapter);
      Toast.makeText(context, format("%s has been deleted.", word), LENGTH_SHORT).show();
    });
  }

  private void writeToTextFile(String word) {
    fileService.writeFileExternalStorage(true, word);
  }

  private List<String> getAllWords() {
    return ImmutableList.<String>builder().addAll(repository.getWords()).build().reverse();
  }
}
