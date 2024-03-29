package com.faraz.dictionary;

import static android.app.ProgressDialog.show;
import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static com.android.volley.Request.Method.POST;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGODB_API_KEY;
import static com.faraz.dictionary.MainActivity.MONGODB_URI;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_AGGREGATE;
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
import com.mailjet.client.transactional.response.SendEmailsResponse;

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

  public static String getItem(int index, JSONArray ans, String extractionTarget) {
    try {
      return ans.getJSONObject(index).getString(extractionTarget);
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
      ClientOptions options = ClientOptions.builder().apiKey(loadProperty(MAIL_KEY_DEPRECATED))
              .apiSecretKey(loadProperty(MAIL_SECRET_DEPRECATED)).build();
      mailjetClient = new MailjetClient(options);
    }
  }

  public void backupData(View view) {
    BackupDataAsyncTaskRunner runner = new BackupDataAsyncTaskRunner();
    runner.activity = this;
    runner.execute();
  }

  public void send5(View view) {
    new SendRandomWordsAsyncTaskRunner().execute();
  }

  public void testEmail(View view) {
    SendTestEmailAsyncTaskRunner runner = new SendTestEmailAsyncTaskRunner();
    runner.activity = this;
    runner.execute();
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
  private int sendEmailUsingMailJetClient(String subject, String body) {
    String from = loadProperty(MAIL_FROM_DEPRECATED);
    String to = loadProperty(MAIL_TO_DEPRECATED);
    body = "<div style=\"font-size:20px\">" + body + "</div>";
      MailjetRequest request = null;
      try {
          request = new MailjetRequest(resource).property(MESSAGES, new JSONArray().put(new JSONObject()
              .put(FROM, new JSONObject().put("Email", from).put("Name", "Personal Dictionary")).put(TO, new JSONArray()
                  .put(new JSONObject().put("Email", to).put("Name", "Personal Dictionary"))).put(SUBJECT, subject)
              .put(HTMLPART, body)));

      MailjetResponse response = mailjetClient.post(request);
      return response.getStatus();
      }
      catch (JSONException | MailjetException e) {
          e.printStackTrace();
      }
      return -1;
  }

  /**
   * It doesn't return the status.
   */
  private int sendEmailUsingJavaMailAPI(String subject, String body) {
    SendEmailAsyncTask email = new SendEmailAsyncTask();
    email.activity = this;
    email.mail = new Mail(loadProperty(JAVAMAIL_USER), loadProperty(JAVAMAIL_PASS));
    email.mail.set_from(format(loadProperty(JAVAMAIL_FROM), currentTimeMillis()));
    email.mail.setBody(body);
    email.mail.set_to(new String[]{loadProperty(JAVAMAIL_TO)});
    email.mail.set_subject(subject);
    email.execute();
    return 200; //TODO Cleanup: trying to support legacy code. Don't really need 200 I suppose.
  }

  private List<String> executeQuery(String operation, String action, String extractionTarget) {
    System.out.println("Query " + operation + ". action: " + action);
    RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
    JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI), action),
        requestFuture, requestFuture, operation, loadProperty(MONGODB_API_KEY));
    requestQueue.add(request);
    try {
      JSONArray ans = requestFuture.get().getJSONArray("documents");
      return IntStream.range(0, ans.length()).mapToObj(i -> getItem(i, ans, extractionTarget)).collect(toList());
    }
    catch (Exception e) {
      e.printStackTrace();
      runOnUiThread(() -> Toast.makeText(this, "Mongo's gone belly up!", LENGTH_LONG).show());
    }
    throw new RuntimeException();
  }

  private List<String> anchor(List<String> words) {
    return words.stream().map(MainActivity2::anchor).collect(toList());
  }

  private void updateData(String query, Consumer<Integer> consumer, String action) {
    System.out.println("Query " + query + ". Action " + MONGO_ACTION_UPDATE_MANY);
    RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
    JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI),
        action), requestFuture, requestFuture, query, loadProperty(MONGODB_API_KEY));
    requestQueue.add(request);
    try {
      JSONObject ans = requestFuture.get();
      int matchedCount = Integer.parseInt(ans.getString("matchedCount"));
      int modifiedCount = Integer.parseInt(ans.getString("modifiedCount"));
      consumer.accept(modifiedCount);
    }
    catch (Exception e) {
      e.printStackTrace();
      runOnUiThread(() -> Toast.makeText(this, "Mongo's belly up!", LENGTH_LONG).show());
    }
  }

  public void displayMessage(String message) {
    runOnUiThread(() -> Toast.makeText(this, message, LENGTH_LONG).show());
  }

  private List<String> addReminderCount(List<String> words) {
    List<String> remindedWords = executeQuery(getRemindedCountQuery(), MONGO_ACTION_AGGREGATE, "reminded");
    words = !words.isEmpty() ? words
        : ImmutableList.of("Seems like words to remind of has been exhausted?");
    return ImmutableList.<String>builder().addAll(words).add("Reminders for '" + remindedWords.stream().findFirst()
        .orElse("(Something's wrong? Check Query!)") + "' words have been sent already.").build();
  }

  private String getRemindedCountQuery() {
    String pipeline = ", \"pipeline\": [ { \"$match\": { \"reminded\": true } }, { \"$count\": \"reminded\" } ]";
    return MONGO_PARTIAL_BODY + pipeline + CLOSE_CURLY;
  }

  private class SendTestEmailAsyncTaskRunner extends AsyncTask<String, String, Void> {

    MainActivity2 activity;

    @Override
    protected Void doInBackground(String... strings) {
//      sendEmailUsingJavaMailAPI("Email server is up!", "Yes.");
      sendEmailUsingMailJetClient("Email server is up!", "Yes.");
      activity.displayMessage("Check email...");
      return null;
    }

    @Override
    protected void onPostExecute(Void v) {
    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected void onProgressUpdate(String... text) {
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
      try {
        List<String> words = executeQuery(createQueryForRandomWords(), MONGO_ACTION_FIND_ALL, "word");
        if (sendRandomWords(addReminderCount(anchor(words)))) {
          markWordsAsReminded_(words);
        }
      }
      catch (Exception e) {
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Not sure what went wrong.", LENGTH_LONG).show());
      }
      return null;
    }

    private void markWordsAsReminded_(List<String> words) {
      //must to an empty check!
      if (words.isEmpty()) {
        //If all word count and reminded = true count is same, (we will know this if words.isempty)
        //then set all reminded = false;
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, "TODO: set all words to 'reminded=false'.", LENGTH_SHORT).show());
        return;
      }
      String filterSubQuery = getFilterQueryToUpdateReminded(words);
      String updateSubQuery = getUpdateQueryToUpdateReminded();
      String query = MONGO_PARTIAL_BODY + "," + filterSubQuery + ", " + updateSubQuery + CLOSE_CURLY;
      Consumer<Integer> consumer = documentsModified -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, format("Marked %d words as reminded.", documentsModified), LENGTH_SHORT).show());
      updateData(query, consumer, MONGO_ACTION_UPDATE_MANY);
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
        if (sendEmailUsingMailJetClient(subject, addDivStyling(randomWords)) == 200) {
          runOnUiThread(() -> Toast.makeText(MainActivity2.this, format("'%d' random words sent.", Math.max(randomWords.size() - 1, 0)), LENGTH_SHORT).show());
          return true;
        }
        else {
          runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Error occurred while sending random words.", LENGTH_SHORT).show());
        }
      }
      catch (Exception e) {
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Error occurred while sending random words.", LENGTH_SHORT).show());
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

    MainActivity2 activity;

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
          List<String> list = IntStream.range(0, ans.length()).mapToObj(i -> getItem(i, ans, "word")).distinct().collect(toList());
          words.addAll(list);
          previousSkip = list.size() == 0 ? -1 : previousSkip + 1;
          publishProgress(format("Loaded '%s' words...", words.size()));
        } while (previousSkip != -1);
        publishProgress(format("Sending '%s' words...", words.size()));
        reverse(words);
        List<List<String>> wordPartitions = Lists.partition(words.stream().distinct().collect(toList()), 10000);
        IntStream.range(0, wordPartitions.size()).forEach(index ->
            ofNullable(wordPartitions.get(index)).filter(wordPartition -> !wordPartition.isEmpty())
                .map(wordPartition -> addCountToFirstLine(wordPartition, words.size()))
                .ifPresent(wordPartition -> sendBackupEmails(index, wordPartition)));
      }
      catch (Exception e) {
        e.printStackTrace();
        activity.displayMessage("Something unknown happened!");
      }
      return null;
    }

    private List<String> addCountToFirstLine(List<String> partWords, int totalWordCount) {
      String firstLine = format("Total Count: '%d'. (%d in this part-backup).", totalWordCount, partWords.size());
      return ImmutableList.<String>builder().add(firstLine).addAll(partWords).build();
    }

    private void sendBackupEmails(int index, List<String> backup_words) {
      String subject = format("Words Backup Part %d:", index + 1);
        if (sendEmailUsingMailJetClient(subject, addDivStyling(backup_words)) == 200) {
          activity.displayMessage(format("'%d' words sent for backup.", Math.max(backup_words.size() - 1, 0)));
        } else {
          activity.displayMessage("Error occurred while backing up words.");
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
          activity.displayMessage("Email(s) sent.");
          return true;
        }
        else {
          activity.displayMessage("Failed to send an email!");
        }
      }
      catch (AuthenticationFailedException e) {
        Log.e(SendEmailAsyncTask.class.getName(), "Bad account details");
        e.printStackTrace();
        activity.displayMessage("Authentication failed.");
      }
      catch (MessagingException e) {
        Log.e(SendEmailAsyncTask.class.getName(), "Email failed");
        e.printStackTrace();
        activity.displayMessage("Failed to send an email!");
      }
      catch (Exception e) {
        e.printStackTrace();
        activity.displayMessage("Unexpected error occurred.");
      }
      return false;
    }
  }
}
