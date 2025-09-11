package com.faraz.dictionary;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RetryPolicy;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * (enter description here)
 * <p>
 *
 * @author faraz.durrani
 */
public class MongoJsonObjectRequest extends JsonObjectRequest {
  private final String body;
  private final String apiKey;

  public MongoJsonObjectRequest(int post, String uri, RequestFuture handleMongoResponse,
      RequestFuture handleMongoError, String body, String apiKey) {
    super(post, uri, null, handleMongoResponse, handleMongoError);
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

  @Override
  public RetryPolicy getRetryPolicy(){
    return new DefaultRetryPolicy(30000,
        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
  }
}
