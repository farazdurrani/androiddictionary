package com.faraz.dictionary;

import static android.app.ProgressDialog.show;
import static android.widget.Toast.LENGTH_LONG;
import static com.faraz.dictionary.MainActivity.CLOSE_CURLY;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_FIND_ALL;
import static com.faraz.dictionary.MainActivity.MONGO_PARTIAL_BODY;
import static com.mailjet.client.transactional.response.SentMessageStatus.SUCCESS;
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
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.RequestQueue;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import okhttp3.OkHttpClient;

@SuppressLint("DefaultLocale")
public class MainActivity2 extends AppCompatActivity {

    private static final String activity = MainActivity2.class.getSimpleName();
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
    private ApiService apiService; //todo improvement. Use this to make called. Move all the API calling to this class.

    public static String anchor(String word) {
        return "<a href='https://www.google.com/search?q=define: " + word + "' target='_blank'>" + capitalize(word) + "</a>";
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
        apiService = new ApiService(requestQueue, properties);
    }

    private void setRequestQueue() {
        if (this.requestQueue == null) {
            this.requestQueue = Volley.newRequestQueue(this);
        }
    }

    public void mailjetClient() {
        if (mailjetClient == null) {
            ClientOptions options = ClientOptions.builder().apiKey(loadProperty(MAIL_KEY))
                    .apiSecretKey(loadProperty(MAIL_SECRET)).okHttpClient(new OkHttpClient.Builder()
                            .callTimeout(1, TimeUnit.MINUTES).build()).build();
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

    public void randomWordsActivity(View view) {
        Intent intent = new Intent(this, MainActivity3.class);
        startActivity(intent);
    }

    private String loadProperty(String property) {
        if (this.properties == null) {
            this.properties = new Properties();
            try (InputStream is = getBaseContext().getAssets().open("application.properties")) {
                properties.load(is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this.properties.getProperty(property);
    }

    private boolean sendEmail(String subject, String body) throws ExecutionException, InterruptedException {
        return defaultEmailProvider ? sendEmailUsingMailJetClient(subject, body) : sendEmailUsingJavaMailAPI(subject, body);
    }

    /**
     * Mailjet api just goes into this bounce/restart/soft bounce thing.
     * Update: Switched back. Meh...
     */
    private boolean sendEmailUsingMailJetClient(String subject, String body) {
        String from = loadProperty(MAIL_FROM);
        String to = loadProperty(MAIL_TO);
        body = "<div style=\"font-size:20px\">" + body + "</div>";
        TransactionalEmail message1 = TransactionalEmail
                .builder()
                .to(new SendContact(to, "Personal Dictionary"))
                .from(new SendContact(from, "Personal Dictionary"))
                .htmlPart(body)
                .subject(subject)
                .trackOpens(TrackOpens.DISABLED)
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
            Log.e(activity, e.getLocalizedMessage(), e);
        }
        return noErrors(response);
    }

    /**
     * It doesn't return the status.
     * And sometimes the email never gets sent.
     */
    private boolean sendEmailUsingJavaMailAPI(String subject, String body) throws ExecutionException, InterruptedException {
        SendEmailAsyncTask email = new SendEmailAsyncTask();
        email.activity = this;
        email.mail = new Mail(loadProperty(JAVAMAIL_USER), loadProperty(JAVAMAIL_PASS));
        email.mail.set_from(format(loadProperty(JAVAMAIL_FROM), currentTimeMillis()));
        email.mail.setBody(body);
        email.mail.set_to(new String[]{loadProperty(JAVAMAIL_TO)});
        email.mail.set_subject(subject);
        return email.execute().get(); //TODO Cleanup: trying to support legacy code. Don't really need 200 I suppose.
    }

    private static boolean noErrors(SendEmailsResponse response) {
        Predicate<MessageResult[]> allSuccessAndNoErrors =
                mr -> Arrays.stream(mr).map(MessageResult::getStatus).allMatch(SUCCESS::equals) &&
                        Arrays.stream(mr).map(MessageResult::getErrors).allMatch(ObjectUtils::isEmpty);
        return ofNullable(response).map(SendEmailsResponse::getMessages).filter(allSuccessAndNoErrors)
                .isPresent();
    }

    public void displayMessage(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, LENGTH_LONG).show());
    }

    private class BackupDataAsyncTaskRunner extends AsyncTask<String, String, Void> {

        private final List<ProgressDialog> progressDialogs = new ArrayList<>();

        MainActivity2 activity;

        @Override
        protected Void doInBackground(String... params) {
            publishProgress("Backing up words..."); // Calls onProgressUpdate()
            List<String> words = new ArrayList<>();
            try {
                int limitNum = 5000;
                String limit = format(", \"limit\": %d ", limitNum);
                String skip = ", \"skip\": %d";
                int previousSkip = 0;
                do {
                    String _skip = format(skip, previousSkip * limitNum);
                    String query = MONGO_PARTIAL_BODY + _skip + limit + CLOSE_CURLY;
                    List<String> list = apiService.executeQuery(query, MONGO_ACTION_FIND_ALL, "word");
                    words.addAll(list.stream().map(MainActivity2::anchor).collect(toList()));
                    previousSkip = list.size() < limitNum || list.isEmpty() ? -1 : previousSkip + 1;
                    publishProgress(format("Loaded '%s' words...", words.size()));
                } while (previousSkip != -1);
                publishProgress(format("Sending '%s' words...", words.size()));
                reverse(words);
                List<List<String>> wordPartitions = Lists.partition(words.stream().distinct().collect(toList()), 10000);
                IntStream.range(0, wordPartitions.size()).forEach(index -> ofNullable(wordPartitions.get(index))
                        .filter(wordPartition -> !wordPartition.isEmpty()).map(wordPartition ->
                                addCountToFirstLine(wordPartition, words.size())).ifPresent(wordPartition ->
                                sendBackupEmails(index, wordPartition)));
            } catch (Exception e) {
                Log.e(activity.getClass().getSimpleName(), e.getLocalizedMessage(), e);
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
            try {
                if (sendEmail(subject, addDivStyling(backup_words))) {
                    activity.displayMessage(format("'%d' words sent for backup.", Math.max(backup_words.size() - 1, 0)));
                } else {
                    activity.displayMessage("Error occurred while backing up words.");
                }
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
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
        protected void onProgressUpdate(String... text) {
            progressDialogs.add(show(MainActivity2.this, "ProgressDialog", text[0]));
        }
    }

    private class SendEmailAsyncTask extends AsyncTask<Void, Void, Boolean> {
        Mail mail;
        MainActivity2 activity;

        public SendEmailAsyncTask() {
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                return mail.send();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
