package com.faraz.dictionary;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.runAsync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.O)
@SuppressLint("SetTextI18n")
public class MainActivity4 extends AppCompatActivity {

    public static final String FILE_NAME = "offlinewords.txt";
    public static final String LOOKUPTHISWORD = "lookupthisword";
    private String[] words;
    private ListView listView;
    private Context context;
    private FileService fileService;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main4);
        context = getBaseContext();
        listView = findViewById(R.id.wordList);
        setListener();
        fileService = new FileService(getExternalFilesDir(null), FILE_NAME);
        fetchWords();
        filepath();
    }

    private void setListener() {
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String word = (String) listView.getAdapter().getItem(position);
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(LOOKUPTHISWORD, word);
            startActivity(intent);
        });
    }

    private void filepath() {
        ((TextView) findViewById(R.id.filepath)).setText(ofNullable(getExternalFilesDir(null)).map(File::getAbsolutePath).orElse("can't locate the path") + File.separator + FILE_NAME);
    }

    private void fetchWords() {
        runAsync(() -> {
            List<String> _words = Arrays.asList(fileService.readFile());
            Collections.reverse(_words);
            words = _words.toArray(new String[0]);
            runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, words)));
        });
    }
}