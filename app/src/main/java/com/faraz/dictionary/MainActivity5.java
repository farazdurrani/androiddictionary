package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_FIND_ALL;
import static com.faraz.dictionary.MainActivity.MONGO_PARTIAL_BODY;
import static com.faraz.dictionary.MainActivity4.FILE_NAME;
import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.toolbox.Volley;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class MainActivity5 extends AppCompatActivity {

    private ListView listView;
    private Context context;
    private FileService fileService;
    private Properties properties;
    private ApiService apiService;
    private String[] words;

    private static final String activity = MainActivity5.class.getSimpleName();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main5);
        context = getBaseContext();
        listView = findViewById(R.id.wordsList);
        setListener();
        fileService = new FileService(getExternalFilesDir(null), FILE_NAME);
        properties = properties();
        apiService = new ApiService(Volley.newRequestQueue(this), properties);
        supplyAsync(this::getLastFewWords).thenAccept(_words -> {
            words = _words;
            runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, words)));
        });
    }

    private void setListener() {
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String word = (String) listView.getAdapter().getItem(position);
            Log.i(activity, word);
            words = Arrays.stream(words).filter(w -> !w.equals(word)).toArray(String[]::new);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.custom_layout, words);
            runOnUiThread(() -> listView.setAdapter(adapter));
        });
    }

    private String[] getLastFewWords() {
        String limit = format(", \"limit\": %d", 20);
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
