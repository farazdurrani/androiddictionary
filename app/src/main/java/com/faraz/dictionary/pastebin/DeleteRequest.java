package com.faraz.dictionary.pastebin;

import java.util.HashMap;
import java.util.Map;

public class DeleteRequest implements Request {

  private final String pasteKey;

  public DeleteRequest(final String pasteKey) {
    this.pasteKey = pasteKey;
  }

  @Override
  public Map<String, String> getParameters() {
    Map<String, String> parameters = new HashMap<>();
    parameters.put("api_option", "delete");
    parameters.put("api_paste_key", pasteKey);
    return parameters;
  }
}
