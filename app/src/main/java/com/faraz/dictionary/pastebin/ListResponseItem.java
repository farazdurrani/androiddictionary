package com.faraz.dictionary.pastebin;

import com.tickaroo.tikxml.annotation.PropertyElement;
import com.tickaroo.tikxml.annotation.Xml;

@Xml(name = "paste")
public class ListResponseItem {

  @PropertyElement(name = "paste_key")
  private String key;

  @PropertyElement(name = "paste_date")
  private Long date;

  @PropertyElement(name = "paste_title")
  private String title;

  @PropertyElement(name = "paste_size")
  private Long size;

  @PropertyElement(name = "paste_expire_date")
  private Long expireDate;

  @PropertyElement(name = "paste_private")
  private Integer privacy;

  @PropertyElement(name = "paste_format_long")
  private String formatLong;

  @PropertyElement(name = "paste_format_short")
  private String formatShort;

  @PropertyElement(name = "paste_url")
  private String url;

  @PropertyElement(name = "paste_hits")
  private Integer hits;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public Long getDate() {
    return date;
  }

  public void setDate(Long date) {
    this.date = date;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }

  public Long getExpireDate() {
    return expireDate;
  }

  public void setExpireDate(Long expireDate) {
    this.expireDate = expireDate;
  }

  public Integer getPrivacy() {
    return privacy;
  }

  public void setPrivacy(Integer privacy) {
    this.privacy = privacy;
  }

  public String getFormatLong() {
    return formatLong;
  }

  public void setFormatLong(String formatLong) {
    this.formatLong = formatLong;
  }

  public String getFormatShort() {
    return formatShort;
  }

  public void setFormatShort(String formatShort) {
    this.formatShort = formatShort;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Integer getHits() {
    return hits;
  }

  public void setHits(Integer hits) {
    this.hits = hits;
  }
}
