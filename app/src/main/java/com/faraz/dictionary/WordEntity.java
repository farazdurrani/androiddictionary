package com.faraz.dictionary;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class WordEntity {

  private String word;
  private String lookupTime;
  private String remindedTime;

  public WordEntity() {
    //DO NOT DELETE!
    //FOR JACKSON'S OBJECTMAPPER!
  }

  public WordEntity(String word, String lookupTime, String remindedTime) {
    this.word = word;
    this.lookupTime = lookupTime;
    this.remindedTime = remindedTime;
  }

  @JsonProperty(value = "word")
  public String getWord() {
    return word;
  }

  @JsonIgnore
  public void setWord(String word) {
    this.word = word;
  }

  @JsonProperty(value = "lookupTime")
  public String getLookupTime() {
    return lookupTime;
  }

  @JsonIgnore
  public void setLookupTime(String lookupTime) {
    this.lookupTime = lookupTime;
  }

  @JsonProperty(value = "remindedTime")
  public String getRemindedTime() {
    return remindedTime;
  }

  @JsonIgnore
  public void setRemindedTime(String remindedTime) {
    this.remindedTime = remindedTime;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;

    if (object == null || getClass() != object.getClass()) return false;

    WordEntity that = (WordEntity) object;

    return new EqualsBuilder().append(word, that.word)
            .append(lookupTime, that.lookupTime).append(remindedTime, that.remindedTime).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(word).append(lookupTime).append(remindedTime).toHashCode();
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
