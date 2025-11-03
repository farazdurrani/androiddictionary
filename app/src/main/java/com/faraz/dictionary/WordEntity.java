package com.faraz.dictionary;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class WordEntity {

  private Integer id;
  private String word;
  private String lookupTime;
  private String remindedTime;

  public WordEntity() {
    //DO NOT DELETE!
    //FOR JACKSON'S OBJECTMAPPER!
  }

  public WordEntity(Integer id, String word, String lookupTime, String remindedTime) {
    this.id = id;
    this.word = word;
    this.lookupTime = lookupTime;
    this.remindedTime = remindedTime;
  }

  @JsonProperty(value = "id")
  public Integer getId() {
    return id;
  }

  @JsonIgnore
  public void setId(Integer id) {
    this.id = id;
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
    return new EqualsBuilder().append(id, that.id).append(word, that.word).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(id).append(word).toHashCode();
  }

  @NonNull
  @Override
  public String toString() {
    return "WordEntity{" +
            "id=" + id +
            ", word='" + word + '\'' +
            ", lookupTime='" + lookupTime + '\'' +
            ", remindedTime='" + remindedTime + '\'' +
            '}';
  }
}
