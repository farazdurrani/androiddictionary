package com.faraz.dictionary;

import static android.app.ProgressDialog.show;
import static android.widget.Toast.LENGTH_LONG;
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
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.reverse;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
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
import java.util.function.Consumer;
import java.util.stream.IntStream;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

@SuppressLint("DefaultLocale")
public class MainActivity2 extends AppCompatActivity {
  private static final String MAIL_KEY_DEPRECATED = "mailjet.apiKey";
  private static final String MAIL_SECRET_DEPRECATED = "mailjet.apiSecret";
  private static final String MAIL_FROM_DEPRECATED = "mailjet.from";
  private static final String MAIL_TO_DEPRECATED = "mailjet.to";
  private static final String JAVAMAIL_USER = "javamail.user";
  private static final String JAVAMAIL_PASS = "javamail.pass";
  private static final String JAVAMAIL_FROM = "javamail.from";
  private static final String JAVAMAIL_TO = "javamail.to";

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

  @NonNull
  private String addDivStyling(List<String> words) {
    return "<div style=\"font-size:20px\">" + join("<br>", words) + "</div>";
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main2);
    setRequestQueue();
  }

  private void setRequestQueue() {
    if (this.requestQueue == null) {
      this.requestQueue = Volley.newRequestQueue(this);
    }
  }

  @Deprecated
  public void mailjetClient() {
    if (mailjetClient == null) {
      String mailKey = loadProperty(MAIL_KEY_DEPRECATED);
      String mailSecret = loadProperty(MAIL_SECRET_DEPRECATED);
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

  /**
   * Mailjet api just goes into this bounce/restart/soft bounce thing.
   */
  @Deprecated
  private int sendEmailUsingMailJetClient(String subject, String body) throws MailjetSocketTimeoutException, MailjetException, JSONException {
    String from = loadProperty(MAIL_FROM_DEPRECATED);
    String to = loadProperty(MAIL_TO_DEPRECATED);
    body = "<div style=\"font-size:20px\">" + body + "</div>";
    MailjetRequest request = new MailjetRequest(resource).property(MESSAGES, new JSONArray().put(new JSONObject()
        .put(FROM, new JSONObject().put("Email", from).put("Name", "Personal Dictionary")).put(TO, new JSONArray()
            .put(new JSONObject().put("Email", to).put("Name", "Personal Dictionary"))).put(SUBJECT, subject)
        .put(HTMLPART, body)));
    MailjetResponse response = mailjetClient.post(request);
    return response.getStatus();
  }

  private int sendEmail(String subject, String body) {
    SendEmailAsyncTask email = new SendEmailAsyncTask();
    email.activity = this;
    email.mail = new Mail(loadProperty(JAVAMAIL_USER), loadProperty(JAVAMAIL_PASS));
    email.mail.set_from(format(loadProperty(JAVAMAIL_FROM), currentTimeMillis()));
    email.mail.setBody(body);
    email.mail.set_to(new String[]{loadProperty(JAVAMAIL_TO)});
    email.mail.set_subject(subject);
    email.execute();
    return 200;
  }

  private List<String> getWordsFromMongo(String operation) {
    RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
    JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI), MONGO_ACTION_FIND_ALL),
        requestFuture, requestFuture, operation, loadProperty(MONGODB_API_KEY));
    requestQueue.add(request);
    try {
      JSONArray ans = requestFuture.get().getJSONArray("documents");
      return IntStream.range(0, ans.length()).mapToObj(i -> getItem(i, ans)).collect(toList());
    }
    catch (Exception e) {
      runOnUiThread(() -> Toast.makeText(this, "Mongo's gone belly up!", LENGTH_LONG).show());
    }
    throw new RuntimeException();
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
    }
    catch (Exception e) {
      runOnUiThread(() -> Toast.makeText(this, "Mongo's belly up!", LENGTH_LONG).show());
    }
  }

  public void displayMessage(String message) {
    runOnUiThread(() -> Toast.makeText(this, message, LENGTH_LONG).show());
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
      try {
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
      }
      catch (Exception e) {
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Not sure what went wrong.", LENGTH_LONG).show());
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

    private boolean sendRandomWords(List<String> randomWords) {
      String subject = "Random Words";
      try {
        if (sendEmail(subject, addDivStyling(randomWords)) == 200) {
          publishProgress(format("'%d' random words sent.", randomWords.size()));
          return true;
        }
        else {
          publishProgress("Error occurred while sending random words.");
        }
      }
      catch (Exception e) {
        publishProgress("Error occurred while sending random words.");
      }
      return false;
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
      publishProgress("Backing up words..."); // Calls onProgressUpdate()
      List<String> words = new ArrayList<>();
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
          List<String> list = IntStream.range(0, ans.length()).mapToObj(i -> getItem(i, ans)).distinct().collect(toList());
          words.addAll(list);
          previousSkip = list.size() == 0 ? -1 : previousSkip + 1;
          publishProgress(format("Loaded '%s' words...", words.size()));
        } while (previousSkip != -1);
        publishProgress(format("Sending '%s' words...", words.size()));
        reverse(words);
        List<List<String>> wordPartitions = Lists.partition(words.stream().distinct().collect(toList()), 3500);
        IntStream.range(0, wordPartitions.size()).forEach(index ->
            ofNullable(wordPartitions.get(index)).filter(wordPartition -> !wordPartition.isEmpty())
                .map(wordPartition -> addCountToFirstLine(wordPartition, words.size()))
                .ifPresent(wordPartition -> sendEmailsInStep(index, wordPartition)));
      }
      catch (Exception e) {
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Something unknown happened!",
            LENGTH_LONG).show());
      }
      return null;
    }

    private List<String> addCountToFirstLine(List<String> partWords, int totalWordCount) {
      String firstLine = format("Total Count: '%d'. (%d in this part-backup).", totalWordCount, partWords.size());
      return ImmutableList.<String>builder().add(firstLine).addAll(partWords).build();
    }

    private void sendEmailsInStep(int index, List<String> backup_words) {
      String subject = format("Words Backup Part %d:", index + 1);
      if (sendEmail(subject, addDivStyling(backup_words)) == 200) {
        publishProgress(format("'%d' words sent for backup.", backup_words.size()));
      }
      else {
        publishProgress("Error occurred while backing up words.");
      }
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

  private class SendEmailAsyncTask extends AsyncTask<Void, Void, Boolean> {
    Mail mail;
    MainActivity2 activity;

    public SendEmailAsyncTask() {}

    @Override
    protected Boolean doInBackground(Void... params) {
      try {
        if (mail.send()) {
          activity.displayMessage("Email sent.");
        }
        else {
          activity.displayMessage("Failed to send an email!");
        }

        return true;
      }
      catch (AuthenticationFailedException e) {
        Log.e(SendEmailAsyncTask.class.getName(), "Bad account details");
        e.printStackTrace();
        activity.displayMessage("Authentication failed.");
        return false;
      }
      catch (MessagingException e) {
        Log.e(SendEmailAsyncTask.class.getName(), "Email failed");
        e.printStackTrace();
        activity.displayMessage("Failed to send an email!");
        return false;
      }
      catch (Exception e) {
        e.printStackTrace();
        activity.displayMessage("Unexpected error occurred.");
        return false;
      }
    }
  }
}
