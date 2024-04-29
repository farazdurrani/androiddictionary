package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_AGGREGATE;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_FIND_ALL;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_UPDATE_MANY;
import static com.faraz.dictionary.MainActivity.MONGO_PARTIAL_BODY;
import static com.faraz.dictionary.MainActivity2.getFilterInQuery;
import static com.faraz.dictionary.MainActivity2.getUpdateQueryToUpdateReminded;
import static java.lang.String.format;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

public class MainActivity3 extends AppCompatActivity {

    private String[] words;
    private TextView remindedWordCountView;
    private ListView listView;
    private Context context;
    private ApiService apiService;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main3);
        context = getBaseContext();
        apiService = new ApiService(requestQueue(), properties());
        listView = findViewById(R.id.wordsList);
        remindedWordCountView = findViewById(R.id.remindedWordsCount);
        AsyncTask.execute(() -> fetch5Words(true));
    }

    private void fetch5Words(boolean setItemClickListener) {
        runOnUiThread(() -> findViewById(R.id.markAsReminded).setEnabled(false));
        runOnUiThread(() -> findViewById(R.id.undoRemind).setEnabled(false));
        words = apiService.executeQuery(createQueryForRandomWords(), MONGO_ACTION_FIND_ALL, "word",
                exceptionConsumer()).toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.custom_layout, words);
        runOnUiThread(() -> listView.setAdapter(adapter));

        runOnUiThread(() -> findViewById(R.id.markAsReminded).setEnabled(true));
        runOnUiThread(() -> findViewById(R.id.undoRemind).setEnabled(true));

        String remindedWordCount = apiService.executeQuery(getRemindedCountQuery(), MONGO_ACTION_AGGREGATE,
                "reminded", exceptionConsumer()).stream().findFirst().orElse("Can't find none.");
        remindedWordCountView.setText(format("'%s' words have been marked as reminded.", remindedWordCount));

        if (setItemClickListener) {
            listView.setOnItemClickListener((parent, view, position, id) -> {
                String word = (String) listView.getAdapter().getItem(position);
                Uri uri = Uri.parse(format("https://www.google.com/search?q=define: %s", word));
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            });
        }
    }

    public void undoRemind(View view) {
        try {
            AsyncTask.execute(() -> {
                List<String> words = apiService.executeQuery(createQueryToPullLast5RemindedWords(), MONGO_ACTION_FIND_ALL, "word", exceptionConsumer());
                unsetLookupWords(words);
                clearWords();
                fetch5Words(false);
            });
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(context, "Not sure what went wrong.", LENGTH_LONG).show());
        }
    }

    public void markWordsAsReminded(View view) {
        AsyncTask.execute(() -> {
            markWordsAsReminded(Arrays.asList(words));
            clearWords();
            fetch5Words(false);
        });
    }

    private void clearWords() {
        words = null;
    }

    private void markWordsAsReminded(List<String> words) {
        //must do an empty check!
        if (words.isEmpty()) {
            //If all word count and reminded = true count is same, (we will know this if words.isempty)
            //then set all reminded = false;
            runOnUiThread(() -> Toast.makeText(context, "TODO: set all words to 'reminded=false'.", LENGTH_SHORT).show());
            return;
        }
        String markWordsAsRemindedFilterQuery = getFilterInQuery(words);
        String updateSubQuery = getUpdateQueryToUpdateReminded();
        String query = MONGO_PARTIAL_BODY + "," + markWordsAsRemindedFilterQuery + ", " + updateSubQuery + CLOSE_CURLY;
        apiService.updateData(query, successConsumer("Marked %d words as reminded."), MONGO_ACTION_UPDATE_MANY, exceptionConsumer());
    }

    private String createQueryForRandomWords() {
        String filter = ",\"filter\": { \"reminded\": false }";
        String limit = ", \"limit\": 5";
        return MONGO_PARTIAL_BODY + filter + limit + CLOSE_CURLY;
    }

    public static String getRemindedCountQuery() {
        String pipeline = ", \"pipeline\": [ { \"$match\": { \"reminded\": true } }, { \"$count\": \"reminded\" } ]";
        return MONGO_PARTIAL_BODY + pipeline + CLOSE_CURLY;
    }

    private RequestQueue requestQueue() {
        return Volley.newRequestQueue(this);
    }

    private Properties properties() {
        Properties properties = new Properties();
        try (InputStream is = getBaseContext().getAssets().open("application.properties")) {
            properties.load(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties;
    }

    private String createQueryToPullLast5RemindedWords() {
        String filter = ", \"filter\" : { \"remindedTime\" : { \"$ne\" : null } }";
        String sort = ",\"sort\": { \"remindedTime\": -1 }";
        String limit = ", \"limit\": 5";
        return MONGO_PARTIAL_BODY + filter + sort + limit + CLOSE_CURLY;
    }

    private void unsetLookupWords(List<String> words) {
        //must do an empty check!
        if (words.isEmpty()) {
            //If all word count and reminded = true count is same, (we will know this if words.isempty)
            //then set all reminded = false;
            runOnUiThread(() -> Toast.makeText(context, "Can't find words to undo reminded!.", LENGTH_SHORT).show());
            return;
        }
        String unsetRemindedFilterInQuery = getFilterInQuery(words);
        String updateSubQuery = unsetRemindedTimeQuery();
        String query = MONGO_PARTIAL_BODY + "," + unsetRemindedFilterInQuery + ", " + updateSubQuery + CLOSE_CURLY;
        apiService.updateData(query, successConsumer("Undid '%d' words as reminded."), MONGO_ACTION_UPDATE_MANY, exceptionConsumer());
    }

    @SuppressLint({"NewApi", "DefaultLocale"})
    private String unsetRemindedTimeQuery() {
        return format("\"update\": { \"$set\" : { \"reminded\" : %b },  \"$unset\" : { \"remindedTime\": \"\" } }", false);
    }

    @NonNull
    private Consumer<Integer> successConsumer(String format) {
        return message -> runOnUiThread(() -> Toast.makeText(context, format(format, message), LENGTH_SHORT).show());
    }

    @NonNull
    private Consumer<String> exceptionConsumer() {
        return message -> runOnUiThread(() -> Toast.makeText(context, message, LENGTH_SHORT).show());
    }
}