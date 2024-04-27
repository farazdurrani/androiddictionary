package com.faraz.dictionary;

import static java.util.Optional.ofNullable;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Objects;

//public class MainActivity3 extends AppCompatActivity {
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main3);
//        runOnUiThread(() -> Toast.makeText(this, "I are aggree no", LENGTH_LONG).show());
//    }
//}

public class MainActivity3 extends ListActivity {

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        String[] words = ofNullable(getIntent().getExtras()).filter(Objects::nonNull)
                .map(extras -> extras.getStringArray("words")).orElse(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, words);
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        String item = (String) getListAdapter().getItem(position);
        Toast.makeText(this, item + " selected", Toast.LENGTH_LONG).show();
    }
}