package com.faraz.dictionary;

import static android.app.ProgressDialog.show;
import static com.android.volley.Request.Method.POST;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGODB_API_KEY;
import static com.faraz.dictionary.MainActivity.MONGODB_URI;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_FIND_ALL;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_UPDATE_MANY;
import static com.faraz.dictionary.MainActivity.MONGO_PARTIAL_BODY;
import static com.mailjet.client.resource.Emailv31.MESSAGES;
import static com.mailjet.client.resource.Emailv31.Message.FROM;
import static com.mailjet.client.resource.Emailv31.Message.HTMLPART;
import static com.mailjet.client.resource.Emailv31.Message.SUBJECT;
import static com.mailjet.client.resource.Emailv31.Message.TO;
import static com.mailjet.client.resource.Emailv31.resource;
import static org.apache.commons.lang3.StringUtils.capitalize;
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
import android.widget.Toast;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class MainActivity2 extends AppCompatActivity {
  private static final String MAIL_KEY = "mailjet.apiKey";
  private static final String MAIL_SECRET = "mailjet.apiSecret";
  private static final String MAIL_FROM = "mailjet.from";
  private static final String MAIL_TO = "mailjet.to";
  private Properties properties;
  private MailjetClient mailjetClient;
  private RequestQueue requestQueue;

  public static String anchor(String word) {
    return "<a href='https://www.google.com/search?q=define: " + word + "' target='_blank'>" + capitalize(word) + "</a>";
  }

  public static String getItem(int index, JSONArray ans) {
    try {
      return ans.getJSONObject(index).getString("word");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

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
    new BackupDataAsyncTaskRunner().execute();
  }

  public void send5(View view) {
    new SendRandomWordsAsyncTaskRunner().execute();
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

  private List<String> getWordsFromMongo(String operation) {
    List<String> definitions = new ArrayList<>();
    RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
    JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI), MONGO_ACTION_FIND_ALL),
        requestFuture, requestFuture, operation, loadProperty(MONGODB_API_KEY));
    requestQueue.add(request);
    try {
      JSONArray ans = requestFuture.get().getJSONArray("documents");
      List<String> list = IntStream.range(0, ans.length()).mapToObj(i -> getItem(i, ans)).collect(toList());
      definitions.addAll(list);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return definitions;
  }

  private List<String> anchor(List<String> definitions) {
    return definitions.stream().map(MainActivity2::anchor).collect(toList());
  }

  private void updateData(String query, Consumer<Integer> consumer) {
    RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
    JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI),
        MONGO_ACTION_UPDATE_MANY), requestFuture, requestFuture, query, loadProperty(MONGODB_API_KEY));
    requestQueue.add(request);
    try {
      JSONObject ans = requestFuture.get();
      int matchedCount = Integer.parseInt(ans.getString("matchedCount"));
      int modifiedCount = Integer.parseInt(ans.getString("modifiedCount"));
      consumer.accept(modifiedCount);
      sleep(2000L);
    }
    catch (Exception e) {
      Toast.makeText(this, "Mongo's belly up!", Toast.LENGTH_LONG).show();
    }
  }

  private class SendRandomWordsAsyncTaskRunner extends AsyncTask<String, String, Void> {
    List<ProgressDialog> progressDialogs = new ArrayList<>();

    @Override
    protected Void doInBackground(String... strings) {
      publishProgress("Sending 5 words...");
      //mongo steps
      //1 get 5 words where reminded = false
      //2 if no words found, set all words to false
      //3 if found, email them, and set reminded = true
      String query = createQueryForRandomWords();
      List<String> words = getWordsFromMongo(query);
      if (words.isEmpty()) { //TODO undo !
        //If all word count and reminded = true count is same,
        //then set all reminded = false;
        //TODO Implement
        publishProgress("No words left to remind of.");
        return null;
      }
      if (sendRandomWords(anchor(words))) {
        markWordsAsReminded_(words);
      }
      return null;
    }

    private void markWordsAsReminded_(List<String> words) {
      String filterSubQuery = getFilterQueryToUpdateReminded(words);
      String updateSubQuery = getUpdateQueryToUpdateReminded();
      String query = MONGO_PARTIAL_BODY + "," + filterSubQuery + ", " + updateSubQuery + CLOSE_CURLY;
      Consumer<Integer> consumer = documentsModified -> publishProgress(format("Marked %d words as reminded.", documentsModified));
      updateData(query, consumer);
    }

    private String getUpdateQueryToUpdateReminded() {
      return format("\"update\": { \"$set\" : { \"reminded\" : %b } }", true);
    }

    private String getFilterQueryToUpdateReminded(List<String> words) {
      String in = "";
      for (String word : words) {
        in = in + format("\"%s\",", word);
      }
      in = in.replaceAll(",$", "");
      return format("\"filter\": { \"word\" : { \"$in\" : [%s] } }", in);
    }

    private boolean sendRandomWords(List<String> definitions) {
      String subject = "Random Words";
      try {
        if (sendEmail(subject, join("<br><br>", definitions)) == 200) {
          publishProgress(format("'%d' random words sent.", definitions.size()));
        }
        else {
          publishProgress("Error occurred while sending random words.");
        }
        sleep(2000L);
      }
      catch (Exception e) {
        publishProgress("Error occurred while sending random words.");
        return false;
      }
      return true;
    }

    private String createQueryForRandomWords() {
      String filter = ",\"filter\": { \"reminded\": false }";
      String limit = ", \"limit\": 5";
      return MONGO_PARTIAL_BODY + filter + limit + CLOSE_CURLY;
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
  }

  private class BackupDataAsyncTaskRunner extends AsyncTask<String, String, Void> {

    private final List<ProgressDialog> progressDialogs = new ArrayList<>();

    @Override
    protected Void doInBackground(String... params) {
      publishProgress("Backing up definitions..."); // Calls onProgressUpdate()
      List<String> definitions = new ArrayList<>();
      try {
        int limitNum = 10000;
        String limit = format(", \"limit\": %d ", limitNum);
        String skip = ", \"skip\": %d";
        int previousSkip = 0;
        do {
          String _skip = format(skip, previousSkip * limitNum);
          String operation = MONGO_PARTIAL_BODY + _skip + limit + CLOSE_CURLY;
          System.out.println("query: " + operation);
          RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
          JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI), MONGO_ACTION_FIND_ALL),
              requestFuture, requestFuture, operation, loadProperty(MONGODB_API_KEY));
          requestQueue.add(request);
          JSONArray ans = requestFuture.get().getJSONArray("documents");
          List<String> list = IntStream.range(0, ans.length()).mapToObj(i -> getItem(i, ans)).distinct()
              .collect(toList());
          definitions.addAll(list);
          previousSkip = list.size() == 0 ? -1 : previousSkip + 1;
          publishProgress(format("Loaded '%s' words...", definitions.size()));
        } while (previousSkip != -1);
        publishProgress(format("Sending '%s' words...", definitions.size()));
        int size = definitions.size();
        reverse(definitions);
        String firstLine = format("Count: '%d'.", size);
        definitions.add(0, firstLine);
        Set<String> defiSet = new LinkedHashSet<>(definitions);
        String subject = "Words Backup";
        if (sendEmail(subject, join("<br>", defiSet)) == 200) {
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
  }
}
