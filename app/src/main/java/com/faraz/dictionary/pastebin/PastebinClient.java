package com.faraz.dictionary.pastebin;

import com.faraz.dictionary.encode.URLEncoder;
import com.tickaroo.tikxml.TikXml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public class PastebinClient {

  private static final String BASE_API_URL = "https://pastebin.com/api";
  private static final MediaType MEDIA_TYPE = MediaType.get("application/x-www-form-urlencoded");
  private static final OkHttpClient client = new OkHttpClient().newBuilder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .writeTimeout(90, TimeUnit.SECONDS)
          .build();
  private final String developerKey;
  private final String userKey;

  public PastebinClient(String developerKey, String userKey) {
    this.developerKey = developerKey;
    this.userKey = userKey;
  }

  public String paste(PasteRequest request) {
    return post("api_post.php", request.getParameters());
  }

  public List<Paste> list(int limit) {
    if (this.userKey == null) {
      throw new IllegalStateException("Cannot retrieve list of pastes without user key. Please call login " +
              "method first or provide user key to PastebinClient.");
    }

    ListRequest request = new ListRequest(limit);
    final String xml = post("api_post.php", request.getParameters());
    if (xml.toLowerCase(Locale.ROOT).contains("no pastes found")) {
      return new ArrayList<>();
    }

    try (Buffer bufferedSource = new Buffer().writeUtf8("<response>" + xml + "</response>")) {
      return new TikXml.Builder().build().read(bufferedSource, ListResponse.class).getItems().stream()
              .map(Paste::toPaste).toList();

    } catch (Exception e) {
      throw new RuntimeException("Could not parse response from Pastebin API: " + e.getMessage(), e);
    }
  }

  public String getUserPaste(final String pasteKey) {
    if (this.userKey == null) {
      throw new IllegalStateException(
              "Cannot get a user's paste without user key. Please call login method first or provide user key to PastebinClient.");
    }
    return post("api_raw.php", new ShowPasteRequest(pasteKey).getParameters());
  }

  public void delete(final String pasteKey) {
    if (this.userKey == null) {
      throw new IllegalStateException(
              "Cannot delete paste without user key. Please call login method first or provide user key to PastebinClient.");
    }

    final String response = post("api_post.php", new DeleteRequest(pasteKey).getParameters());
    if (!response.toLowerCase(Locale.ROOT).contains("paste removed")) {
      throw new RuntimeException("Could not delete paste: " + response);
    }
  }

  private String post(final String endpoint, final Map<String, String> parameters) {
    StringBuilder postBody = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      if (!first) {
        postBody.append("&");
      }
      postBody.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue()));
      first = false;
    }
    postBody.append("&api_dev_key=").append(developerKey);
    if (this.userKey != null) {
      postBody.append("&api_user_key=").append(this.userKey);
    }
    final String url = BASE_API_URL + "/" + endpoint;
    final okhttp3.Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(postBody.toString(), MEDIA_TYPE))
            .build();

    try (Response response = client.newCall(request).execute()) {
      final ResponseBody responseBody = response.body();
      if (responseBody == null) {
        throw new RuntimeException("Could not get response body from " + url);
      }
      final String body = responseBody.string();
      if (body.toLowerCase(Locale.ROOT).startsWith("bad api request")) {
        if (body.contains(", ")) {
          throw new RuntimeException(body.substring(body.indexOf(", ") + 2));
        }
        throw new RuntimeException(body);
      }
      return body;
    } catch (IOException e) {
      throw new RuntimeException("Unable to make request to to " + url + ": " + e.getMessage(), e);
    }
  }
}
