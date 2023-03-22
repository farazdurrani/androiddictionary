package com.faraz.dictionary;

import static android.app.ProgressDialog.show;
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
import static java.lang.String.join;
import static java.lang.Thread.sleep;
import static java.util.Collections.reverse;
import static java.util.stream.Collectors.toList;

import android.app.Dialog;
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
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

public class MainActivity2 extends AppCompatActivity {
  private static final String MAIL_KEY = "mailjet.apiKey";
  private static final String MAIL_SECRET = "mailjet.apiSecret";
  private static final String MAIL_FROM = "mailjet.from";
  private static final String MAIL_TO = "mailjet.to";
  private Properties properties;
  private MailjetClient mailjetClient;
  private RequestQueue requestQueue;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main2);

    mailjetClient();
    setRequestQueue();
  }

  private void setRequestQueue() {
    if (this.requestQueue == null) {
      this.requestQueue = Volley.newRequestQueue(this);
    }
  }

  public void mailjetClient() {
    if (mailjetClient == null) {
      String mailKey = loadProperty(MAIL_KEY);
      String mailSecret = loadProperty(MAIL_SECRET);
      mailjetClient = new MailjetClient(mailKey, mailSecret, new ClientOptions("v3.1"));
    }
  }

  public void backupData(View view) {
    new AsyncTaskRunner().execute();
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

  private class AsyncTaskRunner extends AsyncTask<String, String, Void> {

    List<ProgressDialog> progressDialogs = new ArrayList<>();

    @Override
    protected Void doInBackground(String... params) {
      publishProgress("Backing up definitions..."); // Calls onProgressUpdate()
      List<String> definitions = new ArrayList<>();
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
          definitions.addAll(list);
          if (list.size() == 0) {
            previousSkip = -1;
          }
          else {
            previousSkip++;
          }
          publishProgress(format("Loaded '%s' words...", definitions.size()));
        } while (previousSkip != -1);
        publishProgress(format("Sending '%s' words...", definitions.size()));
        int size = definitions.size();
        reverse(definitions);
        definitions.add(0, "Count: " + size);
        String subject = "Words Backup";
        if (sendEmail(subject, join("<br>", definitions)) == 200) {
          publishProgress(format("'%d' words sent for backup.", size));
          sleep(2000L);
        }
        else {
          publishProgress(format("Error occurred while backing up words."));
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void v) {
      // execution of result of Long time consuming operation
      reverse(progressDialogs);
      progressDialogs.forEach(Dialog::dismiss);
      progressDialogs.clear();
    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected void onProgressUpdate(String... text) {
      progressDialogs.add(show(MainActivity2.this, "ProgressDialog", text[0]));
    }

    private String getItem(int index, JSONArray ans) {
      try {
        return ans.getJSONObject(index).getString("word");
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private int sendEmail(String subject, String body) throws MailjetSocketTimeoutException, MailjetException, JSONException {
      String from = loadProperty(MAIL_FROM);
      String to = loadProperty(MAIL_TO);
      body = "<div style=\"font-size:20px\">" + body + "</div>";
      MailjetRequest request = new MailjetRequest(resource).property(MESSAGES, new JSONArray().put(new JSONObject()
          .put(FROM, new JSONObject().put("Email", from).put("Name", "Personal Dictionary")).put(TO, new JSONArray()
              .put(new JSONObject().put("Email", to).put("Name", "Personal Dictionary"))).put(SUBJECT, subject)
          .put(HTMLPART, body)));
      MailjetResponse response = mailjetClient.post(request);
      return response.getStatus();
    }
  }
}