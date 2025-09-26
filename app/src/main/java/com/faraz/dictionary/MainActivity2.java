package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static com.mailjet.client.transactional.response.SentMessageStatus.SUCCESS;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.lineSeparator;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.runAsync;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

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
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import okhttp3.OkHttpClient;

@SuppressLint({"DefaultLocale", "NewApi"})
@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity2 extends AppCompatActivity {

  public static final String MAIL_KEY = "mailjet.apiKey";
  public static final String MAIL_SECRET = "mailjet.apiSecret";
  public static final String MAIL_FROM = "mailjet.from";
  public static final String MAIL_TO = "mailjet.to";
  public static final String JAVAMAIL_USER = "javamail.user";
  public static final String JAVAMAIL_PASS = "javamail.pass";
  public static final String JAVAMAIL_FROM = "javamail.from";
  public static final String JAVAMAIL_TO = "javamail.to";
  private static final String activity = MainActivity2.class.getSimpleName();
  public static boolean defaultEmailProvider = true; //default email provider is JavaMail. Other option is MailJet.

  private MailjetClient mailjetClient;
  private Properties properties;
  private Repository repository;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main2);
    mailjetClient();
    repository = new Repository();
  }

  public void seeLastFew(View view) {
    Intent intent = new Intent(this, MainActivity5.class);
    startActivity(intent);
  }

  public void backupData(View view) {
    runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Backing up data!", LENGTH_LONG).show());
    List<View> buttons = findViewById(R.id.mainactivity2).getTouchables();
    toggleButtons(buttons, false);
    runAsync(this::backupData).thenAccept(ignore -> toggleButtons(buttons, true));
  }

  private void toggleButtons(List<View> buttons, boolean enable) {
    runOnUiThread(() -> buttons.forEach(v -> v.setEnabled(enable)));
  }

  public void switchEmailProvider(View view) {
    defaultEmailProvider = !defaultEmailProvider;
    runOnUiThread(() -> Toast.makeText(MainActivity2.this, format("Email Provider has been switched to %s",
            (defaultEmailProvider ? "JavaMail" : "MailJet")), LENGTH_SHORT).show());
  }

  public void randomWordsActivity(View view) {
    Intent intent = new Intent(this, MainActivity3.class);
    startActivity(intent);
  }

  public void syncAutocompleteActivity(View view) {
    List<View> buttons = findViewById(R.id.mainactivity2).getTouchables();
    toggleButtons(buttons, false);
    runAsync(repository::reset).thenRun(() -> runOnUiThread(() -> Toast.makeText(MainActivity2.this,
                    "Autocomplete and Database are in sync now.", LENGTH_SHORT).show()))
            .thenRun(() -> toggleButtons(buttons, true)).thenRun(() -> {
              Intent intent = new Intent(this, MainActivity.class);
              startActivity(intent);
            });
  }

  public void mailjetClient() {
    if (mailjetClient == null) {
      ClientOptions options = ClientOptions.builder().apiKey(loadProperty(MAIL_KEY))
              .apiSecretKey(loadProperty(MAIL_SECRET)).okHttpClient(new OkHttpClient.Builder()
                      .callTimeout(1, TimeUnit.MINUTES).build()).build();
      mailjetClient = new MailjetClient(options);
    }
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

  private boolean sendEmail(String subject, String body) throws Exception {
    return defaultEmailProvider ? sendEmailUsingJavaMailAPI(subject, body) : sendEmailUsingMailJetClient(subject, body);
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
  private boolean sendEmailUsingJavaMailAPI(String subject, String body) throws Exception {
    JavaMailSend mail = new JavaMailSend(loadProperty(JAVAMAIL_USER), loadProperty(JAVAMAIL_PASS));
    mail.set_from(format(loadProperty(JAVAMAIL_FROM), currentTimeMillis()));
    mail.setBody(body);
    mail.set_to(new String[]{loadProperty(JAVAMAIL_TO)});
    mail.set_subject(subject);
    return mail.send();
  }

  private void backupData() {
    try {
      String fullData = Base64.encodeToString(CompressUtil.compress(repository.getValuesAsAString()), Base64.DEFAULT);
      String justWords = String.join(lineSeparator(), repository.getWords());
      String fullDataWithCount = format("Total Count: '%d'.", repository.getLength()) + lineSeparator() + justWords;
      Stream.of(fullData, fullDataWithCount).forEach(this::sendBackupEmail);
    } catch (Exception e) {
      Log.e(activity.getClass().getSimpleName(), e.getLocalizedMessage(), e);
      runOnUiThread(() -> Toast.makeText(MainActivity2.this, ExceptionUtils.getStackTrace(e), LENGTH_LONG).show());
    }
  }

  private void sendBackupEmail(String backup_words) {
    String subject = "Words Backup.";
    try {
      if (sendEmail(subject, backup_words)) {
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, format("'%d' words sent for backup.",
                repository.getLength()), LENGTH_SHORT).show());
      } else {
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Error occurred while backing up words.",
                LENGTH_SHORT).show());
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private boolean noErrors(SendEmailsResponse response) {
    Predicate<MessageResult[]> allSuccessAndNoErrors =
            mr -> Arrays.stream(mr).map(MessageResult::getStatus).allMatch(SUCCESS::equals) &&
                    Arrays.stream(mr).map(MessageResult::getErrors).allMatch(ObjectUtils::isEmpty);
    return ofNullable(response).map(SendEmailsResponse::getMessages).filter(allSuccessAndNoErrors)
            .isPresent();
  }
}
