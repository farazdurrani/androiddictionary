package com.faraz.dictionary;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
    CompletableFuture.supplyAsync(this::getAllWords).thenAccept(_words -> {
      words = _words;
      runOnUiThread(() -> listView.setAdapter(new ShowRemindedArrayAdapter(context, R.layout.custom_layout, words)));
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
      String selectedWord = (String) lookupWord.getAdapter().getItem(position);
      runOnUiThread(() -> {
        listView.setAdapter(new ShowRemindedArrayAdapter(context, R.layout.custom_layout, List.of(selectedWord)));
        lookupWord.setText(null);
        lookupWord.setHint("Enter a word to delete.");
      });
      // hide the keyboard
      InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    });

    lookupWord.setOnEditorActionListener((textView, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
              event.getAction() == KeyEvent.ACTION_DOWN)) {
        String enteredText = textView.getText().toString();
        words = Optional.of(enteredText).filter(StringUtils::isNotBlank).map(et -> ImmutableList.<String>builder()
                .addAll(repository.getWords().stream().filter(w -> w.startsWith(et)).toList()).build()
                .reverse().stream().toList()).orElseGet(() -> Lists.reverse(repository.getWords()));
        // hide the keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
        restoreAutocompleteBoxAndListView();
        return true;
      }
      return false;
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
                  restoreAutocompleteBoxAndListView();
                } catch (Exception exc) {
                  Optional.of(exc).ifPresent(e -> runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context,
                          R.layout.custom_layout, List.of(word, ExceptionUtils.getStackTrace(e))))));
                }
              }).setNegativeButton("No", (dialog, which) -> runOnUiThread(() -> Toast.makeText(context, "Fine.",
                      Toast.LENGTH_LONG).show()))
              .show();
    });
  }

  private List<String> setWords(List<String> words) {
    return Optional.of(words).filter(ObjectUtils::isNotEmpty).orElseGet(() -> Lists.reverse(repository.getWords()));
  }

  private void restoreAutocompleteBoxAndListView() {
    runOnUiThread(() -> {
      lookupWord.setAdapter(new ShowRemindedArrayAdapter(this, android.R.layout.select_dialog_item,
              Lists.reverse(repository.getWords())));
      lookupWord.setText(null);
      lookupWord.setHint("Enter a word to delete.");
      listView.setAdapter(new ShowRemindedArrayAdapter(context, R.layout.custom_layout, setWords(words)));
    });
  }

  public void deletedWordsActivity(View view) {
    Intent intent = new Intent(this, OfflineAndDeletedWordsActivity.class);
    intent.putExtra(FILE_NAME, "deletedwords.txt");
    intent.putExtra(WIPEOUT_DATA_BUTTON, true);
    startActivity(intent);
  }

  private void deleteWordFromAutoComplete(String word) {
    MainActivity.AUTO_COMPLETE_WORDS_REMOVE.add(word);
  }

  private void deleteFromDb(String word) {
    repository.delete(word);
    words = words.stream().filter(w -> !w.equals(word)).toList();
    ArrayAdapter<String> adapter = new ShowRemindedArrayAdapter(context, R.layout.custom_layout, words);
    runOnUiThread(() -> {
      listView.setAdapter(adapter);
      Toast.makeText(context, String.format("%s has been deleted.", word), Toast.LENGTH_SHORT).show();
    });
  }

  private void writeToTextFile(String word) {
    fileService.writeFileExternalStorage(true, word);
  }

  private List<String> getAllWords() {
    return ImmutableList.<String>builder().addAll(repository.getWords()).build().reverse();
  }
}
