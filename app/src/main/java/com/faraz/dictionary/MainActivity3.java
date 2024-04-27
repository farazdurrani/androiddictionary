package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGO_PARTIAL_BODY;
import static com.faraz.dictionary.MainActivity2.getFilterInQuery;
import static com.faraz.dictionary.MainActivity2.getUpdateQueryToUpdateReminded;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class MainActivity3 extends AppCompatActivity {

    private String[] words;
    private String remindedWordCount;
    private Intent intent;
    private TextView remindedWordCountView;
    private ListView listView;
    private Context context;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main3);
        context = getBaseContext();
        intent = getIntent();
        words = ofNullable(intent.getExtras()).filter(Objects::nonNull)
                .map(extras -> extras.getStringArray("words")).orElse(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, words);
        listView = findViewById(R.id.wordsList);
        listView.setAdapter(adapter);
        remindedWordCount = ofNullable(intent.getExtras()).filter(Objects::nonNull).map(extras -> extras.getString("remindedWordCount")).orElse("No Count found of reminded words.");
        remindedWordCountView = findViewById(R.id.remindedWordsCount);
        remindedWordCountView.setText(format("'%s' words have been marked as reminded.", remindedWordCount));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String item = (String) listView.getAdapter().getItem(position);
            Toast.makeText(context, item + " selected", Toast.LENGTH_LONG).show();
        });
    }

    public void markWordsAsReminded(View view) {

    }

    private void markWordsAsReminded(List<String> words) {
        //must do an empty check!
        if (words.isEmpty()) {
            //If all word count and reminded = true count is same, (we will know this if words.isempty)
            //then set all reminded = false;
            runOnUiThread(() -> Toast.makeText(MainActivity3.this, "TODO: set all words to 'reminded=false'.", LENGTH_SHORT).show());
            return;
        }
        String markWordsAsRemindedFilterQuery = getFilterInQuery(words);
        String updateSubQuery = getUpdateQueryToUpdateReminded();
        String query = MONGO_PARTIAL_BODY + "," + markWordsAsRemindedFilterQuery + ", " + updateSubQuery + CLOSE_CURLY;
        Consumer<Integer> consumer = documentsModified -> runOnUiThread(() -> Toast.makeText(MainActivity3.this, format("Marked %d words as reminded.", documentsModified), LENGTH_SHORT).show());
//        updateData(query, consumer, MONGO_ACTION_UPDATE_MANY);
    }
}