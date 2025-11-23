package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static com.faraz.dictionary.Completable.logExceptionFunction;
import static com.mailjet.client.transactional.response.SentMessageStatus.SUCCESS;
import static org.apache.commons.text.WordUtils.capitalize;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.Optional.ofNullable;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.transactional.Attachment;
import com.mailjet.client.transactional.SendContact;
import com.mailjet.client.transactional.SendEmailsRequest;
import com.mailjet.client.transactional.TrackOpens;
import com.mailjet.client.transactional.TransactionalEmail;
import com.mailjet.client.transactional.response.MessageResult;
import com.mailjet.client.transactional.response.SendEmailsResponse;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import okhttp3.OkHttpClient;

public class MainActivity2 extends AppCompatActivity {

  public static final String MAIL_KEY = "mailjet.apiKey";
  public static final String MAIL_SECRET = "mailjet.apiSecret";
  public static final String MAIL_FROM = "mailjet.from";
  public static final String MAIL_TO = "mailjet.to";
  public static final String JAVAMAIL_USER = "javamail.user";
  public static final String JAVAMAIL_PASS = "javamail.pass";
  public static final String JAVAMAIL_FROM = "javamail.from";
  public static final String JAVAMAIL_TO = "javamail.to";
  private static final String TAG = MainActivity2.class.getSimpleName();
  private static final int REQUEST_CODE_PICK_FILE = 2;
  public static boolean defaultEmailProvider = true; //default email provider is JavaMail. Other option is MailJet.
  private final Consumer<Throwable> exceptionToast = ex -> runOnUiThread(() -> Toast.makeText(MainActivity2.this,
          ExceptionUtils.getStackTrace(ex), LENGTH_LONG).show());
  private MailjetClient mailjetClient;
  private Properties properties;
  private Repository repository;
  private Context context;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main2);
    context = this;
    mailjetClient();
    repository = new Repository();
  }

  public void seeAll(View view) {
    Intent intent = new Intent(this, MainActivity5.class);
    startActivity(intent);
  }

  public void backupData(View view) {
    new AlertDialog.Builder(context).setTitle("Confirm Action")
            .setMessage("This is an Expensive Action. Are you sure?")
            .setPositiveButton("Yes", (dialog, which) -> {
              dialog.dismiss();
              List<View> buttons = findViewById(R.id.mainactivity2).getTouchables();
              CompletableFuture.runAsync(() -> toggleButtons(buttons, false))
                      .thenRun(() -> runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Backing up data!",
                              LENGTH_LONG).show()))
                      .thenRun(this::sendFullDataInBackupEmail)
                      .thenRun(() -> toggleButtons(buttons, true))
                      .exceptionally(logExceptionFunction(TAG, exceptionToast));
            }).setNegativeButton("No", (dialog, which) -> runOnUiThread(() -> Toast.makeText(context, "Good choice.",
                    LENGTH_LONG).show())).show();
  }

  private void toggleButtons(List<View> buttons, boolean enable) {
    runOnUiThread(() -> buttons.forEach(bt -> bt.setEnabled(enable)));
  }

  public void switchEmailProvider(View view) {
    defaultEmailProvider = !defaultEmailProvider;
    runOnUiThread(() -> Toast.makeText(MainActivity2.this,
            format("Email Provider has been switched to %s", (defaultEmailProvider ? "JavaMail" : "MailJet")),
            LENGTH_SHORT).show());
  }

  public void randomWordsActivity(View view) {
    Intent intent = new Intent(this, MainActivity3.class);
    startActivity(intent);
  }

  public void syncAutocompleteActivity(View view) {
    pickAFileAndReadAndStore();
  }

  private void sendFullDataInBackupEmailBeforeSync() {
    String body = getWordsForEmailCompletable();
    String attachment = repository.getFilepath();
    sendEmailWithSubject(body, "Words Backup with full data before sync.", attachment, String.format(Locale.US,
                    "'%d' words sent for backup before sync.", repository.getLength()),
            "Error occurred while backing-up full data before sync.");
  }

  private void sendFullDataInBackupEmail() {
    String body = getWordsForEmailCompletable();
    String attachment = repository.getFilepath();
    sendEmailWithSubject(body, "Words Backup with full data.", attachment, String.format(Locale.US,
                    "'%d' words sent for backup.", repository.getLength()),
            "Error occurred while backing-up full data.");
  }

  private void mailjetClient() {
    if (mailjetClient == null) {
      ClientOptions options =
              ClientOptions.builder().apiKey(loadProperty(MAIL_KEY)).apiSecretKey(loadProperty(MAIL_SECRET))
                      .okHttpClient(new OkHttpClient.Builder().callTimeout(1, TimeUnit.MINUTES).build()).build();
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

  private boolean sendEmail(String subject, String body, String attachment) throws Exception {
    return defaultEmailProvider ? sendEmailUsingJavaMailAPI(subject, body, attachment) :
            sendEmailUsingMailJetClient(subject, body, attachment);
  }

  /**
   * Mailjet api just goes into this bounce/restart/soft bounce thing.
   * Update: Switched back. Meh...
   * Update: added attachment but haven't tested yet.
   */
  private boolean sendEmailUsingMailJetClient(String subject, String body, String attachment) {
    String from = loadProperty(MAIL_FROM);
    String to = loadProperty(MAIL_TO);
    body = "<div style=\"font-size:20px\">" + body + "</div>";
    TransactionalEmail.TransactionalEmailBuilder teb = TransactionalEmail.builder().to(new SendContact(to,
                    "Personal Dictionary")).from(new SendContact(from, "Personal Dictionary"))
            .htmlPart(body).subject(subject).trackOpens(TrackOpens.DISABLED);
    if (StringUtils.isNotBlank(attachment)) {
      String filecontent = getFileContent(attachment);
      Attachment ab = Attachment.builder().contentType("application/json").base64Content(filecontent)
              .filename(attachment.substring(attachment.lastIndexOf(File.separatorChar) + 1)).build();
      teb.attachment(ab);
    }
    TransactionalEmail message1 = teb.build();
    SendEmailsRequest request = SendEmailsRequest.builder().message(message1).build();
    SendEmailsResponse response = null;
    // act
    try {
      response = request.sendWith(mailjetClient);
    } catch (MailjetException e) {
      Log.e(TAG, ExceptionUtils.getStackTrace(e));
    }
    return noErrors(response);
  }

  private String getFileContent(String attachment) {
    try {
      return com.mailjet.client.Base64.encode(Files.toByteArray(new File(attachment)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * It doesn't return the status.
   * And sometimes the email never gets sent.
   */
  private boolean sendEmailUsingJavaMailAPI(String subject, String body, String attachment) throws Exception {
    JavaMailSend mail = new JavaMailSend(loadProperty(JAVAMAIL_USER), loadProperty(JAVAMAIL_PASS));
    mail.set_from(format(loadProperty(JAVAMAIL_FROM), currentTimeMillis()));
    if (StringUtils.isNotBlank(attachment)) {
      mail.addAttachment(attachment);
    }
    mail.setBody(body);
    mail.set_to(new String[]{loadProperty(JAVAMAIL_TO)});
    mail.set_subject(subject);
    return mail.send();
  }

  private void pickAFileAndReadAndStore() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); // Or ACTION_OPEN_DOCUMENT_TREE
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*"); // To allow selecting any file type
    startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_CODE_PICK_FILE) {
      handleSyncDataFile(resultCode, data);
    }
  }

  private void handleSyncDataFile(int resultCode, @Nullable Intent data) {
    if (resultCode == RESULT_OK && data != null) {
      Supplier<RuntimeException> dataNotFoundException = () -> new RuntimeException("Where's the data!");
      Uri uri = Optional.of(data).map(Intent::getData).orElseThrow(dataNotFoundException);
      try (InputStream inputStream = getContentResolver().openInputStream(uri);
           InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charsets.UTF_8)) {
        String result = CharStreams.toString(inputStreamReader);
        Optional.of(result).filter(StringUtils::isNotBlank).orElseThrow(dataNotFoundException);
        List<View> buttons = findViewById(R.id.mainactivity2).getTouchables();
        CompletableFuture.runAsync(() -> toggleButtons(buttons, false))
                .thenRun(this::sendFullDataInBackupEmailBeforeSync)
                .thenRun(repository::clear)
                .thenRun(() -> repository.writeOverFile(result))
                .thenRun(() -> runOnUiThread(() -> Toast.makeText(MainActivity2.this,
                        "Autocomplete and Database are in sync now.", LENGTH_SHORT).show()))
                .thenRun(() -> toggleButtons(buttons, true))
                .thenRun(this::gotoMainActivity);
      } catch (Exception e) {
        Log.e(TAG, ExceptionUtils.getStackTrace(e));
      }
    } else if (resultCode == RESULT_CANCELED) {
      runOnUiThread(() -> Toast.makeText(MainActivity2.this, "Did not proceed thru with the sync",
              LENGTH_SHORT).show());
    }
  }

  private void gotoMainActivity() {
    Intent intent = new Intent(context, MainActivity.class);
    startActivity(intent);
  }

  private String getWordsForEmailCompletable() {
    return OfflineAndDeletedWordsActivity.addDivStyling(ImmutableList.<String>builder().add(String.format(Locale.US,
            "Total Count: '%d'.", repository.getLength())).addAll(ImmutableList.<String>builder()
            .addAll(repository.getWords().stream().map(this::anchor).toList()).build().reverse()).build());
  }

  private String anchor(String word) {
    return "<a href='https://www.google.com/search?q=define: " + word + "' target='_blank'>" + capitalize(word) +
            "</a>";
  }

  private void sendEmailWithSubject(String body, String subject, String attachment, String successMsg, String failMsg) {
    try {
      if (sendEmail(subject, body, attachment)) {
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, successMsg, LENGTH_SHORT).show());
      } else {
        runOnUiThread(() -> Toast.makeText(MainActivity2.this, failMsg, LENGTH_SHORT).show());
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private boolean noErrors(SendEmailsResponse response) {
    Predicate<MessageResult[]> allSuccessAndNoErrors =
            mr -> Arrays.stream(mr).map(MessageResult::getStatus).allMatch(SUCCESS::equals) &&
                    Arrays.stream(mr).map(MessageResult::getErrors).allMatch(ObjectUtils::isEmpty);
    return ofNullable(response).map(SendEmailsResponse::getMessages).filter(allSuccessAndNoErrors).isPresent();
  }
}
