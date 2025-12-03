package com.faraz.dictionary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class ShowRemindedArrayAdapter extends ArrayAdapter<String> {

  // todo this class is not loaded when android is doing its thing. If it were to load it during android doing its thing
  // this line 'static and new Repository' this early on would surely have caused mayhem. Keep an eye on this in
  // future in case there's a fallout.
  // if this class is initialized during or after onCreate, good.
  private static final Repository repository = new Repository();

  public ShowRemindedArrayAdapter(@NonNull Context context, int resource,
                                  @NonNull List<String> objects) {
    super(context, resource, objects);
  }

  @SuppressLint("SetTextI18n")
  @androidx.annotation.NonNull
  @Override
  public View getView(int position, @Nullable View convertView, @androidx.annotation.NonNull ViewGroup parent) {
    TextView view = (TextView) super.getView(position, convertView, parent);
    String text = view.getText().toString();
    if (repository.isReminded(text)) {
      text = "**" + text;
    }
    view.setText(text);
    return view;
  }
}