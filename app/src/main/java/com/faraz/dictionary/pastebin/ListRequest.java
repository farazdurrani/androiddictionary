package com.faraz.dictionary.pastebin;

import java.util.HashMap;
import java.util.Map;

public class ListRequest implements Request {

  private int limit = -1;

  public ListRequest(Integer limit) {
    this.limit = limit;
  }

  @Override
  public Map<String, String> getParameters() {
    Map<String, String> output = new HashMap<>();
    output.put("api_option", "list");
    if (this.limit != -1) {
      output.put("api_results_limit", String.valueOf(this.limit));
    }
    return output;
  }
}
