package com.faraz.dictionary;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({"word", "lookupTime", "remindedTime"})
public class WordEntity {

  private String word;
  private long lookupTime;
  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  private long remindedTime;

  public WordEntity() {
    //DO NOT DELETE!
    //FOR JACKSON'S OBJECTMAPPER!
  }

  public WordEntity(String word, long lookupTime, long remindedTime) {
    this.word = word;
    this.lookupTime = lookupTime == 0 ? Instant.now().toEpochMilli() : lookupTime;
    this.remindedTime = remindedTime;
  }

  public String getWord() {
    return word;
  }

  public void setWord(String word) {
    this.word = word;
  }

  public long getLookupTime() {
    return lookupTime;
  }

  public void setLookupTime(long lookupTime) {
    this.lookupTime = lookupTime;
  }

  public long getRemindedTime() {
    return remindedTime;
  }

  public void setRemindedTime(long remindedTime) {
    this.remindedTime = remindedTime;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;

    WordEntity that = (WordEntity) object;
    return word.equals(that.word);
  }

  @Override
  public int hashCode() {
    return word.hashCode();
  }

  @NonNull
  @Override
  public String toString() {
    return "WordEntity{" +
            "word='" + word + '\'' +
            ", lookupTime='" + lookupTime + '\'' +
            ", remindedTime='" + remindedTime + '\'' +
            '}';
  }
}
