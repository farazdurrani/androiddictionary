package com.faraz.dictionary.pastebin;

import java.util.HashMap;
import java.util.Map;

public class PasteRequest implements Request {

  private final String content;
  private final Format format;
  private final Visibility visibility;
  private final String name;
  private final Expiration expiration;
  private final String folderKey;

  public PasteRequest(String content, Format format, Visibility visibility, String name, Expiration expiration,
                       String folderKey) {
    this.content = content;
    this.format = format;
    this.visibility = visibility;
    this.name = name;
    this.expiration = expiration;
    this.folderKey = folderKey;
  }

  @Override
  public Map<String, String> getParameters() {
    Map<String, String> parameters = new HashMap<>();
    parameters.put("api_option", "paste");
    parameters.put("api_paste_code", content);
    if (this.format != null) {
      parameters.put("api_paste_format", this.format.getCode());
    }

    if (this.visibility != null) {
      parameters.put("api_paste_private", String.valueOf(this.visibility.getCode()));
    }

    if (this.name != null) {
      parameters.put("api_paste_name", this.name);
    }

    if (this.expiration != null) {
      parameters.put("api_paste_expire_date", this.expiration.getCode());
    }

    if (this.folderKey != null) {
      parameters.put("api_folder_key", this.folderKey);
    }

    return parameters;
  }
}
