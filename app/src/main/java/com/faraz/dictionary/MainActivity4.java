package com.faraz.dictionary;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.MainActivity.FILE_NAME;
import static com.faraz.dictionary.MainActivity5.WIPEOUT_DATA_BUTTON;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.runAsync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressLint({"SetTextI18n", "NewApi"})
public class MainActivity4 extends AppCompatActivity {

  public static final String LOOKUPTHISWORD = "lookupthisword";
  private String[] words;
  private ListView listView;
  private Context context;
  private FileService fileService;
  private String filename;

  @SuppressLint("SetTextI18n")
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.activity_main4);
    context = getBaseContext();
    listView = findViewById(R.id.wordList);
    ofNullable(getIntent().getExtras()).filter(e -> BooleanUtils.isFalse(e.getBoolean(WIPEOUT_DATA_BUTTON)))
            .ifPresent(ignore -> findViewById(R.id.wipeoutDeletedWords).setVisibility(INVISIBLE));
    ofNullable(getIntent().getExtras()).map(e -> e.getString(FILE_NAME)).ifPresent(this::doInitiation);
    ofNullable(getIntent().getExtras()).filter(e -> StringUtils.isBlank(e.getString(FILE_NAME, EMPTY)))
            .ifPresent(ignore -> ((TextView) findViewById(R.id.filepath)).setText("can't locate the path"));
    ((TextView) findViewById(R.id.offlineWordsCount)).setText(fileService.readFile().length + " total words in file.");
  }

  private void doInitiation(String fn) {
    filename = fn;
    fileService = new FileService(getExternalFilesDir(null), filename);
    setListener();
    fetchWords();
    filepath();
  }

  private void setListener() {
    listView.setOnItemClickListener((parent, view, position, id) -> {
      String word = (String) listView.getAdapter().getItem(position);
      ofNullable(word).filter(StringUtils::isNotBlank).ifPresent(this::spawnActivity);
      ofNullable(word).filter(StringUtils::isBlank).ifPresent(ignore -> runOnUiThread(
              () -> Toast.makeText(context, "cannot send non-existent words.", LENGTH_SHORT).show()));
    });
  }

  private void spawnActivity(String word) {
    Intent intent = new Intent(this, MainActivity.class);
    intent.putExtra(LOOKUPTHISWORD, word);
    startActivity(intent);
  }

  private void filepath() {
    ((TextView) findViewById(R.id.filepath)).setText(
            ofNullable(getExternalFilesDir(null)).map(File::getAbsolutePath).orElse("can't locate the path") +
                    File.separator + filename);
  }

  private void fetchWords() {
    runAsync(() -> {
      List<String> _words = Arrays.asList(fileService.readFile());
      Collections.reverse(_words);
      words = _words.toArray(new String[0]);
      runOnUiThread(() -> listView.setAdapter(new ShowNumbersArrayAdapter(context, R.layout.custom_layout, words)));
    });
  }

  private static class ShowNumbersArrayAdapter extends ArrayAdapter<String> {
    public ShowNumbersArrayAdapter(@androidx.annotation.NonNull Context context, int resource,
                                   @androidx.annotation.NonNull String[] objects) {
      super(context, resource, objects);
    }

    @androidx.annotation.NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @androidx.annotation.NonNull ViewGroup parent) {
      TextView view = (TextView) super.getView(position, convertView, parent);
      view.setText(++position + " " + view.getText());
      return view;
    }
  }

  public void wipeoutDeletedWords(View view) {
    ofNullable(getIntent().getExtras()).map(e -> e.getBoolean(WIPEOUT_DATA_BUTTON)).filter(BooleanUtils::isTrue)
            .ifPresent(this::deleteWordsAndShowNewDisplay);
  }

  private void deleteWordsAndShowNewDisplay(boolean... ignore) {
    fileService.clearFile();
    words = fileService.readFile();
    runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, words)));
  }
}