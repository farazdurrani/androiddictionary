package com.faraz.dictionary;

import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * (enter description here)
 * <p>
 *
 * @author faraz.durrani
 * @author Copyright (c) 2023 MountainView Software, Corp.
 */
public class MongoStringRequest extends StringRequest {
  private final String body;
  private final String apiKey;

  public MongoStringRequest(int post, String uri, Response.Listener<String> handleMongoResponse,
      Response.ErrorListener handleMongoError, String body, String apiKey) {
    super(post, uri, handleMongoResponse, handleMongoError);
    this.body = body;
    this.apiKey = apiKey;
  }

  @Override
  public String getBodyContentType() {
    return "application/json; charset=utf-8";
  }

  @Override
  public byte[] getBody() {
    return this.body.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Map<String, String> getHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Access-Control-Request-Headers", "*");
    headers.put("api-key", this.apiKey);
    return headers;
  }
}
