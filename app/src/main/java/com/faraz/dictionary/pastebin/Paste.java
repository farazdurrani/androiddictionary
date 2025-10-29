package com.faraz.dictionary.pastebin;

import static com.faraz.dictionary.Repository.CHICAGO_ZONE_ID;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.time.Instant;
import java.time.LocalDateTime;

public class Paste {

  private String key;
  private LocalDateTime date;
  private LocalDateTime expiration;
  private String title;
  private long size;
  private Visibility visibility;
  private Format format;
  private String url;
  private int hits;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public LocalDateTime getDate() {
    return date;
  }

  public void setDate(LocalDateTime date) {
    this.date = date;
  }

  public LocalDateTime getExpiration() {
    return expiration;
  }

  public void setExpiration(LocalDateTime expiration) {
    this.expiration = expiration;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public Visibility getVisibility() {
    return visibility;
  }

  public void setVisibility(Visibility visibility) {
    this.visibility = visibility;
  }

  public Format getFormat() {
    return format;
  }

  public void setFormat(Format format) {
    this.format = format;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getHits() {
    return hits;
  }

  public void setHits(int hits) {
    this.hits = hits;
  }

  @NonNull
  @Override
  public String toString() {
    return super.getClass().getSimpleName() + "[" + getTitle() + "]";
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;

    if (object == null || getClass() != object.getClass()) return false;

    Paste that = (Paste) object;

    return new EqualsBuilder().append(key, that.key).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(key).toHashCode();
  }

  public static Paste toPaste(ListResponseItem item) {
    Paste paste = new Paste();
    paste.setKey(item.getKey());
    paste.setTitle(item.getTitle());
    paste.setSize(item.getSize());
    paste.setFormat(Format.find(item.getFormatShort()));
    paste.setUrl(item.getUrl());
    paste.setHits(item.getHits());
    if (item.getExpireDate() != 0) {
      paste.setExpiration(getTimeFromEpoch(item.getExpireDate()));
    }
    paste.setVisibility(Visibility.find(item.getPrivacy()));
    paste.setDate(getTimeFromEpoch(item.getDate()));
    return paste;
  }

  private static LocalDateTime getTimeFromEpoch(long epoch) {
    return Instant.ofEpochSecond(epoch).atZone(CHICAGO_ZONE_ID).toLocalDateTime();
  }
}
