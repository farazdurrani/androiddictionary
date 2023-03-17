package com.faraz.dictionary;

import static com.mailjet.client.resource.Emailv31.MESSAGES;
import static com.mailjet.client.resource.Emailv31.Message.FROM;
import static com.mailjet.client.resource.Emailv31.Message.HTMLPART;
import static com.mailjet.client.resource.Emailv31.Message.SUBJECT;
import static com.mailjet.client.resource.Emailv31.Message.TO;
import static com.mailjet.client.resource.Emailv31.resource;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

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

public class MainActivity2 extends AppCompatActivity {

  private Properties properties;
  private MailjetClient mailjetClient;

  private static final String MAIL_KEY = "mailjet.apiKey";
  private static final String MAIL_SECRET = "mailjet.apiSecret";
  private static final String MAIL_FROM = "mailjet.from";
  private static final String MAIL_TO = "mailjet.to";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main2);
    mailjetClient();
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
      } catch (Exception e) {
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