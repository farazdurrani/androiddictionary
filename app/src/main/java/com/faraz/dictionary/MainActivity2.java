package com.faraz.dictionary;

import static com.android.volley.Request.Method.POST;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGODB_API_KEY;
import static com.faraz.dictionary.MainActivity.MONGODB_URI;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_FIND_ALL;
import static com.faraz.dictionary.MainActivity.MONGO_PARTIAL_BODY;
import static com.mailjet.client.resource.Emailv31.MESSAGES;
import static com.mailjet.client.resource.Emailv31.Message.FROM;
import static com.mailjet.client.resource.Emailv31.Message.HTMLPART;
import static com.mailjet.client.resource.Emailv31.Message.SUBJECT;
import static com.mailjet.client.resource.Emailv31.Message.TO;
import static com.mailjet.client.resource.Emailv31.resource;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import android.app.ProgressDialog;
import android.os.AsyncTask;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

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
    mailjetClient();
    setRequestQueue();
    AsyncTaskRunner runner = new AsyncTaskRunner();
    String sleepTime = "10";
    runner.execute(sleepTime);
  }

  private void setRequestQueue() {
    if (this.requestQueue == null) {
      this.requestQueue = Volley.newRequestQueue(this);
    }
  }

  private void loadAllData() {
    String skip = "\"skip\": %d";
    String operation = MONGO_PARTIAL_BODY + CLOSE_CURLY;
    RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
    JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI), MONGO_ACTION_FIND_ALL),
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
          sendEmail("Bismillah", ans.toString(), loadProperty(MAIL_FROM), loadProperty(MAIL_TO));
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
    loadAllData();
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

  private class AsyncTaskRunner extends AsyncTask<String, String, String> {

    private String resp;
    ProgressDialog progressDialog;

    @Override
    protected String doInBackground(String... params) {
      publishProgress("Sleeping..."); // Calls onProgressUpdate()
      List<String> allWords = new ArrayList<>();
      try {
        String skip = ", \"skip\": %d";
        int previousSkip = 0;
        do {
          String _skip = format(skip, previousSkip * 1000);
          String operation = MONGO_PARTIAL_BODY + _skip + CLOSE_CURLY;
          RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
          JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI), MONGO_ACTION_FIND_ALL),
              requestFuture, requestFuture, operation, loadProperty(MONGODB_API_KEY));
          requestQueue.add(request);
          JSONArray ans = requestFuture.get().getJSONArray("documents");
          List<String> list = IntStream.range(0, ans.length()).mapToObj(i -> getItem(i, ans)).collect(toList());
          allWords.addAll(list);
          if (list.size() == 0) {
            previousSkip = -1;
          }
          else {
            previousSkip++;
          }
        } while (previousSkip != -1);
        Collections.reverse(allWords);
        resp = "Sending " + allWords.size();
        sendEmail("Word Backup", );
      }
      catch (Exception e) {
        e.printStackTrace();
        resp = e.getMessage();
      }
      return resp;
    }

    private String getItem(int index, JSONArray ans) {
      try {
        return ans.getJSONObject(index).getString("word");
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }


    @Override
    protected void onPostExecute(String result) {
      // execution of result of Long time consuming operation
      progressDialog.dismiss();
      System.out.println("onPostExecute " + result);
    }


    @Override
    protected void onPreExecute() {
      progressDialog = ProgressDialog.show(MainActivity2.this,
          "ProgressDialog", "Wait for seconds");
    }


    @Override
    protected void onProgressUpdate(String... text) {
      System.out.println("onProgressUpdate " + text);
    }
  }
}