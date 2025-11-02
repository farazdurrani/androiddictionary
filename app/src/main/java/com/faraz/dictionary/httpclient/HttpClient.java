package com.faraz.dictionary.httpclient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HttpClient {
  public static final OkHttpClient client = new OkHttpClient().newBuilder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .writeTimeout(90, TimeUnit.SECONDS)
          .build();

  public static String get(String url) {
    Request request = new Request.Builder()
            .url(url)
            .build();
    try (Response response = client.newCall(request).execute()) {
      return response.body() != null ? response.body().string() : null;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
