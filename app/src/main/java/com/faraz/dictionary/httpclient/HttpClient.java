package com.faraz.dictionary.httpclient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HttpClient {
  public static final OkHttpClient client = new OkHttpClient().newBuilder()
          .connectTimeout(2500, TimeUnit.MILLISECONDS).readTimeout(2500, TimeUnit.MILLISECONDS)
          .writeTimeout(2500, TimeUnit.MILLISECONDS).build();

  public static String getResponseBody(String url) {
    Request request = new Request.Builder().url(url).build();
    try (Response response = client.newCall(request).execute()) {
      return response.body() != null ? response.body().string() : null;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static int getResponseCode(String url) {
    Request request = new Request.Builder().url(url).head().build();
    try (Response response = client.newCall(request).execute()) {
      return response.code();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
