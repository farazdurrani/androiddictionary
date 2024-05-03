package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.MainActivity.CHICAGO;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_AGGREGATE;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_FIND_ALL;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_UPDATE_MANY;
import static com.faraz.dictionary.MainActivity.MONGO_PARTIAL_BODY;
import static java.lang.String.format;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.toolbox.Volley;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class MainActivity3 extends AppCompatActivity {

    private String[] words;
    private TextView remindedWordCountView;
    private ListView listView;
    private Context context;
    private ApiService apiService;
    private static final String activity = "MainActivity3";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main3);
        context = getBaseContext();
        apiService = new ApiService(Volley.newRequestQueue(this), properties());
        listView = findViewById(R.id.wordsList);
        remindedWordCountView = findViewById(R.id.remindedWordsCount);
        AsyncTask.execute(() -> {
            toggleButtons(false);
            try {
                fetch5Words(true);
            } catch (Exception e) {
                Log.e(activity, e.getLocalizedMessage(), e);
                runOnUiThread(() -> Toast.makeText(context, "Mongo has gone belly up!", LENGTH_SHORT).show());
            }
            toggleButtons(true);
        });
    }

    private void fetch5Words(boolean setItemClickListener) throws JSONException, ExecutionException, InterruptedException {
        words = apiService.executeQuery(createQueryForRandomWords(), MONGO_ACTION_FIND_ALL, "word").toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.custom_layout, words);
        runOnUiThread(() -> listView.setAdapter(adapter));

        String remindedWordCount = apiService.executeQuery(getRemindedCountQuery(), MONGO_ACTION_AGGREGATE, "reminded")
                .stream().findFirst().orElse("Can't find none.");
        remindedWordCountView.setText(format("'%s' words have been marked as reminded.", remindedWordCount));

        if (setItemClickListener) {
            listView.setOnItemClickListener((parent, view, position, id) -> {
                String word = (String) listView.getAdapter().getItem(position);
                Uri uri = Uri.parse(format("https://www.google.com/search?q=define: %s", word));
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            });
        }
    }

    private void toggleButtons(boolean visible) {
        runOnUiThread(() -> findViewById(R.id.markAsReminded).setEnabled(visible));
        runOnUiThread(() -> findViewById(R.id.undoRemind).setEnabled(visible));
    }

    public void undoRemind(View view) {
        AsyncTask.execute(() -> {
            toggleButtons(false);
            try {
                List<String> words = getWords();
                unsetLookupWords(words);
                clearWords();
                fetch5Words(false);
                toggleButtons(true);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(context, "Not sure what went wrong.", LENGTH_LONG).show());
            }
        });
    }

    public void markWordsAsReminded(View view) {
        AsyncTask.execute(() -> {
            toggleButtons(false);
            try {
                markWordsAsReminded(Arrays.asList(words));
                clearWords();
                fetch5Words(false);
                toggleButtons(true);
            } catch (Exception e) {
                Log.e(activity, e.getLocalizedMessage(), e);
                runOnUiThread(() -> Toast.makeText(context, "Not sure what went wrong.", LENGTH_LONG).show());
            }
        });
    }

    private void clearWords() {
        words = null;
    }

    private void markWordsAsReminded(List<String> words) throws ExecutionException, InterruptedException {
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
        apiService.upsert(query, MONGO_ACTION_UPDATE_MANY);
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

    private Properties properties() {
        Properties properties = new Properties();
        try (InputStream is = getBaseContext().getAssets().open("application.properties")) {
            properties.load(is);
        } catch (IOException e) {
            runOnUiThread(() -> Toast.makeText(context, "Can't load properties.", LENGTH_SHORT).show());
        }
        return properties;
    }

    private String createQueryToPullLast5RemindedWords() {
        String filter = ", \"filter\" : { \"remindedTime\" : { \"$ne\" : null } }";
        String sort = ",\"sort\": { \"remindedTime\": -1 }";
        String limit = ", \"limit\": 5";
        return MONGO_PARTIAL_BODY + filter + sort + limit + CLOSE_CURLY;
    }

    private void unsetLookupWords(List<String> words) throws ExecutionException, InterruptedException {
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
        apiService.upsert(query, MONGO_ACTION_UPDATE_MANY);
    }

    @SuppressLint({"NewApi", "DefaultLocale"})
    private String unsetRemindedTimeQuery() {
        return format("\"update\": { \"$set\" : { \"reminded\" : %b },  \"$unset\" : { \"remindedTime\": \"\" } }", false);
    }

    private List<String> getWords() throws ExecutionException, InterruptedException, JSONException {
        return apiService.executeQuery(createQueryToPullLast5RemindedWords(),
                MONGO_ACTION_FIND_ALL, "word");
    }

    private String getFilterInQuery(List<String> words) {
        String in = "";
        for (String word : words) {
            in = in + format("\"%s\",", word);
        }
        in = in.replaceAll(",$", "");
        return format("\"filter\": { \"word\" : { \"$in\" : [%s] } }", in);
    }

    @SuppressLint({"NewApi", "DefaultLocale"})
    private String getUpdateQueryToUpdateReminded() {
        return format("\"update\": { \"$set\" : { \"reminded\" : %b, \"remindedTime\" : {  \"$date\" : {  \"$numberLong\" : \"%d\"} } } }",
                true, Instant.now(Clock.system(ZoneId.of(CHICAGO))).toEpochMilli());
    }
}