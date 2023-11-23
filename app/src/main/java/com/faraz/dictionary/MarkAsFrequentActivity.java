package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_LONG;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class MarkAsFrequentActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.mark_as_frequent);
    Intent intent = getIntent();
    String action = intent.getAction();
    Uri data = intent.getData();
    Toast.makeText(this, "DEEP LINKING WORKING!", LENGTH_LONG).show();
  }
}
