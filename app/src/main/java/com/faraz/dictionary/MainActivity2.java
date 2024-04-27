package com.faraz.dictionary;

import static android.app.ProgressDialog.show;
import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static com.android.volley.Request.Method.POST;
import static com.faraz.dictionary.MainActivity.CHICAGO;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGODB_API_KEY;
import static com.faraz.dictionary.MainActivity.MONGODB_URI;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_AGGREGATE;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_FIND_ALL;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_UPDATE_MANY;
import static com.faraz.dictionary.MainActivity.MONGO_PARTIAL_BODY;
import static com.mailjet.client.transactional.response.SentMessageStatus.SUCCESS;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.Collections.reverse;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.transactional.SendContact;
import com.mailjet.client.transactional.SendEmailsRequest;
import com.mailjet.client.transactional.TrackOpens;
import com.mailjet.client.transactional.TransactionalEmail;
import com.mailjet.client.transactional.response.MessageResult;
import com.mailjet.client.transactional.response.SendEmailsResponse;

import org.apache.commons.lang3.ObjectUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

@SuppressLint("DefaultLocale")
public class MainActivity2 extends AppCompatActivity {
  private static final String MAIL_KEY = "mailjet.apiKey";
  private static final String MAIL_SECRET = "mailjet.apiSecret";
  private static final String MAIL_FROM = "mailjet.from";
  private static final String MAIL_TO = "mailjet.to";
  private static final String JAVAMAIL_USER = "javamail.user";
  private static final String JAVAMAIL_PASS = "javamail.pass";
  private static final String JAVAMAIL_FROM = "javamail.from";
  private static final String JAVAMAIL_TO = "javamail.to";

  private boolean defaultEmailProvider = true; //default email provider is MailJet. Other option is JavaMail.

  private MailjetClient mailjetClient;
  private RequestQueue requestQueue;
  private Properties properties;
  private ApiService apiService;

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
    apiService = new ApiService(requestQueue, properties, getBaseContext());
    Button undoLast5Reminded = findViewById(R.id.undoLast5Reminded);
    undoLast5Reminded.setEnabled(false);
    undoLast5Reminded.postDelayed(() -> undoLast5Reminded.setEnabled(true), 6000l);
  }

  private void setRequestQueue() {
    if (this.requestQueue == null) {
      this.requestQueue = Volley.newRequestQueue(this);
    }
  }

  public void mailjetClient() {
    if (mailjetClient == null) {
      ClientOptions options = ClientOptions.builder().apiKey(loadProperty(MAIL_KEY))
              .apiSecretKey(loadProperty(MAIL_SECRET)).build();
      mailjetClient = new MailjetClient(options);
    }
  }

  public void backupData(View view) {
    BackupDataAsyncTaskRunner runner = new BackupDataAsyncTaskRunner();
    runner.activity = this;
    runner.execute();
  }

  public void switchEmailProvider(View view) {
    defaultEmailProvider = !defaultEmailProvider;
    displayMessage(format("Email Provider has been switched to %s", (defaultEmailProvider ? "MailJet" : "JavaMail")));
  }

  public void send5Activity(View view) {

//    if (true) { //todo delete
//      AsyncTask.execute(() -> {
//        ApiService service = new ApiService(requestQueue, properties, getBaseContext());
//        String markWordsAsRemindedFilterQuery = getFilterInQuery(asList("bifurcation", "facade"));
//        String updateSubQuery = getUpdateQueryToUpdateReminded();
//        String query = MONGO_PARTIAL_BODY + "," + markWordsAsRemindedFilterQuery + ", " + updateSubQuery + CLOSE_CURLY;
//        Consumer<Integer> consumer = documentsModified -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, format("Marked %d words as reminded.", documentsModified), LENGTH_SHORT).show());
//        Consumer<String> exceptionConsumer = message -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, message, LENGTH_SHORT).show());
//        service.updateData(query, consumer, MONGO_ACTION_UPDATE_MANY, exceptionConsumer);
//        return;
//      });
//    }
//    if(true) {
//      return;
//    }
    Intent intent = new Intent(this, MainActivity3.class);
    AsyncTask.execute(() -> {
      Consumer<String> exceptionConsumer = message -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, message, LENGTH_SHORT).show());
      List<String> words = apiService.executeQuery(createQueryForRandomWords(), MONGO_ACTION_FIND_ALL, "word", exceptionConsumer);
      intent.putExtra("words", words.toArray(new String[0]));
      String remindedWordCount = apiService.executeQuery(getRemindedCountQuery(), MONGO_ACTION_AGGREGATE, "reminded", exceptionConsumer).stream().findFirst().get();
      intent.putExtra("remindedWordCount", remindedWordCount);
      startActivity(intent);
    });
//    new SendRandomWordsAsyncTaskRunner().execute(); //TODO delete?
  }

  private String createQueryForRandomWords() {
    String filter = ",\"filter\": { \"reminded\": false }";
    String limit = ", \"limit\": 5";
    return MONGO_PARTIAL_BODY + filter + limit + CLOSE_CURLY;
  }

  public void undoLast5Reminded(View view) {
    UndoRemindedAsyncTaskRunner runner = new UndoRemindedAsyncTaskRunner();
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

  private int sendEmail(String subject, String body) {
    return defaultEmailProvider ? sendEmailUsingMailJetClient(subject, body) : sendEmailUsingJavaMailAPI(subject, body);
  }

  /**
   * Mailjet api just goes into this bounce/restart/soft bounce thing.
   * Update: Switched back. Meh...
   */
  private int sendEmailUsingMailJetClient(String subject, String body) {
    String from = loadProperty(MAIL_FROM);
    String to = loadProperty(MAIL_TO);
    body = "<div style=\"font-size:20px\">" + body + "</div>";
    TransactionalEmail message1 = TransactionalEmail
            .builder()
            .to(new SendContact(to, "Personal Dictionary"))
            .from(new SendContact(from, "Personal Dictionary"))
            .htmlPart(body)
            .subject(subject)
            .trackOpens(TrackOpens.ENABLED)
            .build();

    SendEmailsRequest request = SendEmailsRequest
            .builder()
            .message(message1) // you can add up to 50 messages per request
            .build();
    SendEmailsResponse response = null;
    // act
    try {
      response = request.sendWith(mailjetClient);
    } catch (MailjetException e) {
      e.printStackTrace();
    }
    return noErrors(response) ? 200 : -1;
  }

  /**
   * It doesn't return the status.
   * And sometimes the email never gets sent.
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

  private static boolean noErrors(SendEmailsResponse response) {
    Predicate<MessageResult[]> allSuccessAndNoErrors =
            mr -> Arrays.stream(mr).map(MessageResult::getStatus).allMatch(SUCCESS::equals) &&
                    Arrays.stream(mr).map(MessageResult::getErrors).allMatch(ObjectUtils::isEmpty);
    return ofNullable(response).map(SendEmailsResponse::getMessages).filter(allSuccessAndNoErrors)
            .isPresent();
  }

  public static List<String> anchor(List<String> words) {
    return words.stream().map(MainActivity2::anchor).collect(toList());
  }

  //TODO moved to ApiService
//  private boolean updateData(String query, Consumer<Integer> consumer, String action) {
//    System.out.println("Query " + query + ". Action " + MONGO_ACTION_UPDATE_MANY);
//    RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
//    JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI),
//        action), requestFuture, requestFuture, query, loadProperty(MONGODB_API_KEY));
//    requestQueue.add(request);
//    try {
//      JSONObject ans = requestFuture.get();
//      int matchedCount = Integer.parseInt(ans.getString("matchedCount"));
//      int modifiedCount = Integer.parseInt(ans.getString("modifiedCount"));
//      consumer.accept(modifiedCount);
//      return true;
//    }
//    catch (Exception e) {
//      e.printStackTrace();
//      runOnUiThread(() -> Toast.makeText(this, "Mongo's belly up!", LENGTH_LONG).show());
//      return false;
//    }
//  }

  public void displayMessage(String message) {
    runOnUiThread(() -> Toast.makeText(this, message, LENGTH_LONG).show());
  }

  private List<String> addReminderCount(List<String> words) {
    List<String> _words = !words.isEmpty() ? ImmutableList.copyOf(words)
            : ImmutableList.of("Seems like words to remind of has been exhausted?");
    Consumer<String> exceptionConsumer = message -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, message, LENGTH_SHORT).show());
    List<String> remindedWords = apiService.executeQuery(getRemindedCountQuery(), MONGO_ACTION_AGGREGATE, "reminded", exceptionConsumer);
    return ImmutableList.<String>builder().addAll(_words).add("Reminders for '" + remindedWords.stream().findFirst()
        .orElse("(Something's wrong? Check Query!)") + "' words have been sent already.").build();
  }

  public static String getRemindedCountQuery() {
    String pipeline = ", \"pipeline\": [ { \"$match\": { \"reminded\": true } }, { \"$count\": \"reminded\" } ]";
    return MONGO_PARTIAL_BODY + pipeline + CLOSE_CURLY;
  }

  public static String getFilterInQuery(List<String> words) {
    String in = "";
    for (String word : words) {
      in = in + format("\"%s\",", word);
    }
    in = in.replaceAll(",$", "");
    return format("\"filter\": { \"word\" : { \"$in\" : [%s] } }", in);
  }

  private class UndoRemindedAsyncTaskRunner extends AsyncTask<String, String, Void> {

    MainActivity2 activity;

    @Override
    protected Void doInBackground(String... strings) {
//      sendEmailUsingJavaMailAPI("Email server is up!", "Yes."); TODO
//      sendEmailUsingMailJetClient("Email server is up!", "Yes."); TODO
//      activity.displayMessage("Check email...");

      try {
        Consumer<String> exceptionConsumer = message -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, message, LENGTH_SHORT).show());
        List<String> words = apiService.executeQuery(createQueryToPullLast5RemindedWords(), MONGO_ACTION_FIND_ALL, "word", exceptionConsumer);
        unsetLookupWords(words);
      } catch (Exception e) {
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Not sure what went wrong.", LENGTH_LONG).show());
      }
      return null;
    }

    private String createQueryToPullLast5RemindedWords() {
      String filter = ", \"filter\" : { \"remindedTime\" : { \"$ne\" : null } }";
      String sort = ",\"sort\": { \"remindedTime\": -1 }";
      String limit = ", \"limit\": 5";
      return MONGO_PARTIAL_BODY + filter + sort + limit + CLOSE_CURLY;
    }

    private void unsetLookupWords(List<String> words) {
      //must do an empty check!
      if (words.isEmpty()) {
        //If all word count and reminded = true count is same, (we will know this if words.isempty)
        //then set all reminded = false;
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Can't find words to undo reminded!.", LENGTH_SHORT).show());
        return;
      }
      String unsetRemindedFilterInQuery = getFilterInQuery(words);
      String updateSubQuery = unsetRemindedTime();
      String query = MONGO_PARTIAL_BODY + "," + unsetRemindedFilterInQuery + ", " + updateSubQuery + CLOSE_CURLY;
      Consumer<Integer> consumer = documentsModified -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, format("Undid %d words as reminded.", documentsModified), LENGTH_SHORT).show());
      Consumer<String> exceptionConsumer = message -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, message, LENGTH_SHORT).show());
      apiService.updateData(query, consumer, MONGO_ACTION_UPDATE_MANY, exceptionConsumer);
    }

    @SuppressLint({"NewApi", "DefaultLocale"})
    private String unsetRemindedTime() {
      return format("\"update\": { \"$set\" : { \"reminded\" : %b },  \"$unset\" : { \"remindedTime\": \"\" } }", false);
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


  public class SendRandomWordsAsyncTaskRunner extends AsyncTask<String, String, Void> {
    List<ProgressDialog> progressDialogs = new ArrayList<>();

    @Override
    protected Void doInBackground(String... strings) {
      publishProgress("Sending 5 words...");
      //mongo steps
      //1 get 5 words where reminded = false
      //2 if no words found, set all words to false
      //3 if found, email them, and set reminded = true
      try {
        Consumer<String> exceptionConsumer = message -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, message, LENGTH_SHORT).show());
        List<String> words = apiService.executeQuery(createQueryForRandomWords(), MONGO_ACTION_FIND_ALL, "word", exceptionConsumer);
        if (sendRandomWords(addReminderCount(anchor(words)))) {
          markWordsAsReminded(words);
        }
      }
      catch (Exception e) {
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Not sure what went wrong.", LENGTH_LONG).show());
      }
      return null;
    }

    private void markWordsAsReminded(List<String> words) {
      //must do an empty check!
      if (words.isEmpty()) {
        //If all word count and reminded = true count is same, (we will know this if words.isempty)
        //then set all reminded = false;
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, "TODO: set all words to 'reminded=false'.", LENGTH_SHORT).show());
        return;
      }
      String markWordsAsRemindedFilterQuery = getFilterInQuery(words);
      String updateSubQuery = getUpdateQueryToUpdateReminded();
      String query = MONGO_PARTIAL_BODY + "," + markWordsAsRemindedFilterQuery + ", " + updateSubQuery + CLOSE_CURLY;
      Consumer<Integer> consumer = documentsModified -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, format("Marked %d words as reminded.", documentsModified), LENGTH_SHORT).show());
      Consumer<String> exceptionConsumer = message -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, message, LENGTH_SHORT).show());
      apiService.updateData(query, consumer, MONGO_ACTION_UPDATE_MANY, exceptionConsumer);
    }

    private boolean sendRandomWords(List<String> randomWords) {
      String subject = "Random Words";
      try {
        if (sendEmail(subject, addDivStyling(randomWords)) == 200) {
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

  @SuppressLint({"NewApi", "DefaultLocale"})
  public static String getUpdateQueryToUpdateReminded() {
    return format("\"update\": { \"$set\" : { \"reminded\" : %b, \"remindedTime\" : {  \"$date\" : {  \"$numberLong\" : \"%d\"} } } }", true, Instant.now(Clock.system(ZoneId.of(CHICAGO))).toEpochMilli());
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
        if (sendEmail(subject, addDivStyling(backup_words)) == 200) {
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
