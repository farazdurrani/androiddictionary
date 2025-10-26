package com.faraz.dictionary;

import androidx.annotation.NonNull;

import com.pastebin.api.model.Paste;

/**
 * Just needed the {@link #toString()} method.
 */
public class PasteExtension extends Paste {

  @NonNull
  @Override
  public String toString() {
    return super.getClass().getSimpleName() + "[" + getTitle() + "]";
  }

  public static PasteExtension create(Paste paste) {
    PasteExtension ex = new PasteExtension();
    ex.setKey(paste.getKey());
    ex.setDate(paste.getDate());
    ex.setExpiration(paste.getExpiration());
    ex.setTitle(paste.getTitle());
    ex.setSize(paste.getSize());
    ex.setVisibility(paste.getVisibility());
    ex.setFormat(paste.getFormat());
    ex.setUrl(paste.getUrl());
    ex.setHits(paste.getHits());
    return ex;
  }
}
