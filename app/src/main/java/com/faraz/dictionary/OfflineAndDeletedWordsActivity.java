package com.faraz.dictionary;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.mailjet.client.transactional.response.SentMessageStatus;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import okhttp3.OkHttpClient;

public class OfflineAndDeletedWordsActivity extends AppCompatActivity {

  public static final String TAG = OfflineAndDeletedWordsActivity.class.getSimpleName();

  public static final String LOOKUPTHISWORD = "lookupthisword";
  private String[] words;
  private ListView listView;
  private Context context;
  private Context contextForAlertDialog;
  private FileService fileService;
  private String filename;
  private Properties properties;
  private MailjetClient mailjetClient;

  @SuppressLint("SetTextI18n")
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.offline_and_deleted_words_activity);
    context = getBaseContext();
    contextForAlertDialog = this;
    listView = findViewById(R.id.wordList);
    Optional.ofNullable(getIntent().getExtras())
            .filter(e -> BooleanUtils.isFalse(e.getBoolean(MainActivity5.WIPEOUT_DATA_BUTTON)))
            .ifPresent(ignore -> findViewById(R.id.wipeoutDeletedWords).setVisibility(View.INVISIBLE));
    Optional.ofNullable(getIntent().getExtras())
            .filter(e -> BooleanUtils.isTrue(e.getBoolean(MainActivity5.WIPEOUT_DATA_BUTTON)))
            .ifPresent(ignore -> findViewById(R.id.emailButton).setVisibility(View.INVISIBLE));
    Optional.ofNullable(getIntent().getExtras()).map(e -> e.getString(MainActivity.FILE_NAME))
            .ifPresent(this::doInitiation);
    Optional.ofNullable(getIntent().getExtras()).filter(e -> StringUtils.isBlank(e.getString(MainActivity.FILE_NAME,
                    StringUtils.EMPTY)))
            .ifPresent(ignore -> ((TextView) findViewById(R.id.filepath)).setText("can't locate the path"));
    ((TextView) findViewById(R.id.wordsCount)).setText(Optional.ofNullable(words).map(x -> x.length)
            .orElse(0) + " words in file.");
    Optional.of(fileService.readFile()).filter(ObjectUtils::isEmpty)
            .ifPresent(ignore -> findViewById(R.id.emailButton).setEnabled(false));
    mailjetClient();
  }

  private void doInitiation(String fn) {
    filename = fn;
    fileService = new FileService(filename);
    setListener();
    fetchWords();
    filepath();
  }

  public void mailjetClient() {
    if (mailjetClient == null) {
      ClientOptions options = ClientOptions.builder().apiKey(loadProperty(MainActivity2.MAIL_KEY))
              .apiSecretKey(loadProperty(MainActivity2.MAIL_SECRET)).okHttpClient(new OkHttpClient.Builder()
                      .callTimeout(1, TimeUnit.MINUTES).build()).build();
      mailjetClient = new MailjetClient(options);
    }
  }

  private void setListener() {
    listView.setOnItemClickListener((parent, view, position, id) -> {
      String word = (String) listView.getAdapter().getItem(position);
      Optional.ofNullable(word).filter(StringUtils::isNotBlank).ifPresent(this::spawnActivity);
      Optional.ofNullable(word).filter(StringUtils::isBlank).ifPresent(ignore -> runOnUiThread(
              () -> Toast.makeText(context, "cannot send non-existent words.", Toast.LENGTH_SHORT).show()));
    });
  }

  private void spawnActivity(String word) {
    Intent intent = new Intent(this, MainActivity.class);
    intent.putExtra(LOOKUPTHISWORD, word);
    startActivity(intent);
  }

  @SuppressLint("SetTextI18n")
  private void filepath() {
    runOnUiThread(() -> ((TextView) findViewById(R.id.filepath)).setText(
            Optional.ofNullable(getExternalFilesDir(null)).map(File::getAbsolutePath).orElse("can't locate the path") +
                    File.separator + filename));
  }

  private void fetchWords() {
    Completable.runAsync(() -> {
      words = fileService.readFile().toArray(new String[0]);
      ArrayUtils.reverse(words);
      runOnUiThread(() -> listView.setAdapter(new ShowNumbersArrayAdapter(context, R.layout.custom_layout, words)));
    });
  }

  public void wipeoutDeletedWords(View view) {
    new AlertDialog.Builder(contextForAlertDialog).setTitle("Confirm Action")
            .setMessage("Are you sure you want wipe-out the deleted words completely?")
            .setPositiveButton("Yes", (dialog, which) -> {
              dialog.dismiss();
              Optional.ofNullable(getIntent().getExtras()).map(e -> e.getBoolean(MainActivity5.WIPEOUT_DATA_BUTTON))
                      .filter(BooleanUtils::isTrue)
                      .ifPresent(this::deleteWordsAndShowNewDisplay);
            }).setNegativeButton("No",
                    (dialog, which) -> runOnUiThread(() -> Toast.makeText(contextForAlertDialog, "Fine.",
                            Toast.LENGTH_LONG).show()))
            .show();
  }

  @SuppressLint("SetTextI18n")
  private void deleteWordsAndShowNewDisplay(boolean... ignore) {
    CompletableFuture.runAsync(() -> fileService.clearFile());
    words = new String[0];
    runOnUiThread(() -> listView.setAdapter(new ArrayAdapter<>(context, R.layout.custom_layout, words)));
    runOnUiThread(() -> ((TextView) findViewById(R.id.wordsCount)).setText(words.length + " total words in file."));
  }

  public void emailWords(View view) {
    runOnUiThread(() -> Toast.makeText(OfflineAndDeletedWordsActivity.this, String.format("Using %s to send " +
            "emails.", MainActivity2.defaultEmailProvider ? "JavaMail" : "MailJet"), Toast.LENGTH_SHORT).show());
    runOnUiThread(() -> findViewById(R.id.emailButton).setEnabled(false));
    Completable.runAsync(() -> sendEmails(fileService.readFile()));
    runOnUiThread(() -> findViewById(R.id.emailButton).setEnabled(true));
  }

  private void sendEmails(List<String> words) {
    String subject = "Offline Words.";
    try {
      if (sendEmail(subject, addDivStyling(words))) {
        runOnUiThread(() -> Toast.makeText(OfflineAndDeletedWordsActivity.this, String.format(Locale.US,
                "'%d' offline words sent.", words.size()), Toast.LENGTH_SHORT).show());
      } else {
        runOnUiThread(() -> Toast.makeText(OfflineAndDeletedWordsActivity.this, "Error occurred while sending email.",
                Toast.LENGTH_SHORT).show());
      }
    } catch (Exception e) {
      Log.e(TAG, ExceptionUtils.getStackTrace(e));
      throw new RuntimeException(e);
    }
  }

  /**
   * It doesn't return the status.
   * And sometimes the email never gets sent.
   */
  private boolean sendEmailUsingJavaMailAPI(String subject, String body) throws Exception {
    JavaMailSend mail = new JavaMailSend(loadProperty(MainActivity2.JAVAMAIL_USER),
            loadProperty(MainActivity2.JAVAMAIL_PASS));
    mail.set_from(String.format(loadProperty(MainActivity2.JAVAMAIL_FROM), System.currentTimeMillis()));
    mail.setBody(body);
    mail.set_to(new String[]{loadProperty(MainActivity2.JAVAMAIL_TO)});
    mail.set_subject(subject);
    return mail.send();
  }

  private boolean sendEmail(String subject, String body) throws Exception {
    if (MainActivity2.defaultEmailProvider) {
      return sendEmailUsingJavaMailAPI(subject, body);
    }
    //lets skip this for now while it's abstracted away
    String from = loadProperty(MainActivity2.MAIL_FROM);
    String to = loadProperty(MainActivity2.MAIL_TO);
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
      runOnUiThread(() -> Toast.makeText(OfflineAndDeletedWordsActivity.this, "Error occurred while sending email.",
              Toast.LENGTH_SHORT).show());
      Log.e(TAG, ExceptionUtils.getStackTrace(e));
    }
    return noErrors(response);
  }

  private boolean noErrors(SendEmailsResponse response) {
    Predicate<MessageResult[]> allSuccessAndNoErrors =
            mr -> Arrays.stream(mr).map(MessageResult::getStatus).allMatch(SentMessageStatus.SUCCESS::equals) &&
                    Arrays.stream(mr).map(MessageResult::getErrors).allMatch(ObjectUtils::isEmpty);
    return Optional.ofNullable(response).map(SendEmailsResponse::getMessages).filter(allSuccessAndNoErrors)
            .isPresent();
  }

  private String loadProperty(String property) {
    if (this.properties == null) {
      this.properties = new Properties();
      try (InputStream is = getBaseContext().getAssets().open("application.properties")) {
        properties.load(is);
      } catch (IOException e) {
        Log.e(TAG, ExceptionUtils.getStackTrace(e));
        throw new RuntimeException(e);
      }
    }
    return this.properties.getProperty(property);
  }

  @NonNull
  public static String addDivStyling(List<String> words) {
    return "<div style=\"font-size:20px\">" + String.join("<br>", words) + "</div>";
  }

  private static class ShowNumbersArrayAdapter extends ArrayAdapter<String> {
    public ShowNumbersArrayAdapter(@androidx.annotation.NonNull Context context, int resource,
                                   @androidx.annotation.NonNull String[] objects) {
      super(context, resource, objects);
    }

    @SuppressLint("SetTextI18n")
    @androidx.annotation.NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @androidx.annotation.NonNull ViewGroup parent) {
      TextView view = (TextView) super.getView(position, convertView, parent);
      view.setText(++position + " " + view.getText());
      return view;
    }
  }
}