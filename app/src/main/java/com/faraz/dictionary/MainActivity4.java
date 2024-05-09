package com.faraz.dictionary;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.runAsync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Objects;

public class MainActivity4 extends AppCompatActivity {

    public static final String FILE_NAME = "offlinewords.txt";
    private String[] words;
    private ListView listView;
    private Context context;
    private FileService fileService;
    private static final String activity = MainActivity4.class.getSimpleName();

    @SuppressLint("SetTextI18n")
    @Override
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main4);
        context = getBaseContext();
        listView = findViewById(R.id.wordList);
        fileService = new FileService(getExternalFilesDir(null), FILE_NAME);
        fetchWords();
        filepath();
    }

    private void filepath() {
        ((TextView) findViewById(R.id.filepath)).setText(Objects.requireNonNull(getExternalFilesDir(null)).getAbsolutePath());
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void fetchWords() {

        runAsync(() -> {
            words = fileService.readFile();
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.custom_layout, words);
            runOnUiThread(() -> listView.setAdapter(adapter));

            listView.setOnItemClickListener((parent, view, position, id) -> {
                String word = (String) listView.getAdapter().getItem(position);
                Uri uri = Uri.parse(format("https://www.google.com/search?q=define: %s", word));
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            });
        });
    }
}