package com.faraz.dictionary;

import static com.android.volley.Request.Method.POST;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGODB_API_KEY;
import static com.faraz.dictionary.MainActivity.MONGODB_URI;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_FIND_ONE;
import static com.faraz.dictionary.MainActivity.MONGO_FILTER;
import static com.faraz.dictionary.MainActivity.MONGO_PARTIAL_BODY;
import static com.mailjet.client.resource.Emailv31.MESSAGES;
import static com.mailjet.client.resource.Emailv31.Message.FROM;
import static com.mailjet.client.resource.Emailv31.Message.HTMLPART;
import static com.mailjet.client.resource.Emailv31.Message.SUBJECT;
import static com.mailjet.client.resource.Emailv31.Message.TO;
import static com.mailjet.client.resource.Emailv31.resource;
import static java.lang.String.format;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import com.mailjet.client.MailjetRequest;
import com.mailjet.client.MailjetResponse;
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.errors.MailjetSocketTimeoutException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class MainActivity2 extends AppCompatActivity {

  private Properties properties;
  private MailjetClient mailjetClient;

  private static final String MAIL_KEY = "mailjet.apiKey";
  private static final String MAIL_SECRET = "mailjet.apiSecret";
  private static final String MAIL_FROM = "mailjet.from";
  private static final String MAIL_TO = "mailjet.to";

  private RequestQueue requestQueue;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main2);
    setRequestQueue();
    loadAllData();
//    mailjetClient();
  }

  private void setRequestQueue() {
    if (this.requestQueue == null) {
      this.requestQueue = Volley.newRequestQueue(this);
    }
  }

  private void loadAllData() {
    String operation = MONGO_PARTIAL_BODY + "," + format(MONGO_FILTER, "soyboy") + CLOSE_CURLY;
    RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
    JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI), MONGO_ACTION_FIND_ONE),
        requestFuture, requestFuture, operation, loadProperty(MONGODB_API_KEY));
    this.requestQueue.add(request);
    loadData(requestFuture);
  }

  private void loadData(RequestFuture<JSONObject> request) {
    try {
      new Thread(() -> {
        try {
          JSONObject ans = request.get();
          System.out.println("sir" + ans);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }).start();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void mailjetClient() {
    if (mailjetClient == null) {
      String mailKey = loadProperty(MAIL_KEY);
      String mailSecret = loadProperty(MAIL_SECRET);
      mailjetClient = new MailjetClient(mailKey, mailSecret, new ClientOptions("v3.1"));
    }
  }

  public void backupData(View view) throws MailjetSocketTimeoutException, JSONException, MailjetException {
    System.out.println("Welp backup data now!");
    System.out.println("Sending email!");
    Thread thread = new Thread(() -> {
      try {
        sendEmail("Bismillah", "Bismillah", loadProperty(MAIL_FROM), loadProperty(MAIL_TO));
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    });
    thread.start();
  }

  public int sendEmail(String subject, String body, String from, String to) throws MailjetSocketTimeoutException, MailjetException, JSONException {
    body = "<div style=\"font-size:20px\">" + body + "</div>";
    MailjetRequest request = new MailjetRequest(resource).property(MESSAGES, new JSONArray().put(new JSONObject()
        .put(FROM, new JSONObject().put("Email", from).put("Name", "Personal Dictionary")).put(TO, new JSONArray()
            .put(new JSONObject().put("Email", to).put("Name", "Personal Dictionary"))).put(SUBJECT, subject)
        .put(HTMLPART, body)));
    MailjetResponse response = mailjetClient.post(request);
    return response.getStatus();
  }

  @Deprecated
  private MailjetClient getMailJet() {
    String mailKey = loadProperty(MAIL_KEY);
    String mailSecret = loadProperty(MAIL_SECRET);
    MailjetClient client = new MailjetClient(mailKey, mailSecret, new ClientOptions("v3.1"));
    client.setDebug(MailjetClient.VERBOSE_DEBUG);
    return client;
  }

  private String loadProperty(String property) {
    if (this.properties == null) {
      this.properties = new Properties();
      try (InputStream is = getBaseContext().getAssets().open("application.properties")) {
        properties.load(is);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return this.properties.getProperty(property);
  }
}