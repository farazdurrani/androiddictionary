package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_FIND_ALL;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_UPDATE_MANY;
import static com.faraz.dictionary.MainActivity.MONGO_PARTIAL_BODY;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.toolbox.Volley;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

public class MainActivity5 extends AppCompatActivity {

    private ListView listView;
    private Context context;
    private FileService fileService;
    private ApiService apiService;
    private String[] words;

    private static final String FILE_NAME = "deletedwords.txt";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main5);
        context = getBaseContext();
        listView = findViewById(R.id.wordsList);
        setListener();
        fileService = new FileService(getExternalFilesDir(null), FILE_NAME);
        apiService = new ApiService(Volley.newRequestQueue(this), properties());
        supplyAsync(this::getLastFewWords).thenAccept(_words -> {
            words = _words;
            runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, words)));
        });
    }

    private void setListener() {
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String word = (String) listView.getAdapter().getItem(position);
            try {
                writeToTextFile(word);
                deleteFromDb(word);
            } catch (Exception exc) {
                Optional.of(exc).ifPresent(e -> runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context,
                        R.layout.custom_layout, new String[]{word, ExceptionUtils.getStackTrace(e)}))));
            }
        });
    }

    private void deleteFromDb(String word) {
        String filter = format(", \"filter\": { \"word\": \"%s\" }", word);
        String updateQuery = format(", \"update\": { \"$set\" : { \"deleted\" : %b } }", true);
        String query = MONGO_PARTIAL_BODY + filter + updateQuery + CLOSE_CURLY;
        supplyAsync(() -> {
            try {
                apiService.upsert(query, MONGO_ACTION_UPDATE_MANY);
                return word;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).handle((deletedWord, exception) -> {
            ofNullable(exception).ifPresent(e -> runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context,
                    R.layout.custom_layout, new String[]{word, ExceptionUtils.getStackTrace(e)}))));
            ofNullable(deletedWord).ifPresent(_word -> {
                words = Arrays.stream(words).filter(w -> !w.equals(_word)).toArray(String[]::new);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.custom_layout, words);
                runOnUiThread(() -> {
                    listView.setAdapter(adapter);
                    Toast.makeText(context, format("%s has been deleted.", _word), LENGTH_SHORT).show();
                });
            });
            return deletedWord;
        });
    }

    private void writeToTextFile(String word) {
        fileService.writeFileExternalStorage(true, word);
    }

    private String[] getLastFewWords() {
        @SuppressLint("DefaultLocale") String limit = format(", \"limit\": %d", 20);
        String sort = ", \"sort\": { \"lookupTime\": -1 }";
        String query = MONGO_PARTIAL_BODY + limit + sort + CLOSE_CURLY;
        return apiService.executeQuery(query, MONGO_ACTION_FIND_ALL, "word").toArray(new String[0]);
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
