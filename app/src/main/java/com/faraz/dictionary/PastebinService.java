package com.faraz.dictionary;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import android.util.Log;

import com.faraz.dictionary.pastebin.Expiration;
import com.faraz.dictionary.pastebin.Format;
import com.faraz.dictionary.pastebin.Paste;
import com.faraz.dictionary.pastebin.PasteRequest;
import com.faraz.dictionary.pastebin.PastebinClient;
import com.faraz.dictionary.pastebin.Visibility;

import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Create an instance of this class inside a method and not have it at an object level so as to keep the series intact.
 */
public class PastebinService {
  private static final String SERIES_SPLITTER = "====";
  private static final String TAG = PastebinService.class.getSimpleName();
  private final PastebinClient client;
  private final long series;

  public PastebinService(String... values) {
    if (values == null || values.length != 2) {
      throw new RuntimeException("Provide pastebin devkey and userkey");
    }
    this.client = new PastebinClient(values[0], values[1]);
    this.series = Instant.now().toEpochMilli();
  }

  public String create(String content) {
    final PasteRequest pasteRequest =new PasteRequest(content, Format.JSON, Visibility.PRIVATE,
            String.format(Locale.US, "Words backup %d %s %s", this.series, SERIES_SPLITTER,
                    Instant.now().toString()), Expiration.NEVER, "WordBkup");
    try {
      return this.client.paste(pasteRequest);
    } catch (Exception e) {
      Log.e(TAG, "error...", e);
      throw new RuntimeException(e);
    }
  }

  public List<String> lastBackupAndCleanup() {
    List<Paste> pastes = this.client.list(100).stream()
            .sorted((p1, p2) -> toDate(p2).compareTo(toDate(p1))).toList();
    deleteLastFew(pastes);
    String firstTitleWithSeries = EMPTY;
    List<Paste> pertinentPastes = new ArrayList<>();
    if (!pastes.isEmpty()) {
      firstTitleWithSeries = splitTitleBySeries(pastes.get(0));
      pertinentPastes.add(pastes.get(0));
    }
    for (int i = 1; StringUtils.isNotBlank(firstTitleWithSeries) && i < pastes.size(); i++) {
      if (firstTitleWithSeries.equals(splitTitleBySeries(pastes.get(i)))) {
        pertinentPastes.add(pastes.get(i));
      }
    }
    return pertinentPastes.stream().sorted(Comparator.comparing(Paste::getKey)).map(Paste::getKey)
            .toList();
  }

  /**
   * @param pasteKey - must contain the full pastebin URL
   */
  public String get(String pasteKey) {
    try {
      return this.client.getUserPaste(pasteKey);
    } catch (Exception e) {
      Log.e(TAG, "error...", e);
      throw new RuntimeException(e);
    }
  }

  private String splitTitleBySeries(Paste paste) {
    return paste.getTitle().split(SERIES_SPLITTER)[0];
  }

  private void deleteLastFew(List<Paste> pastes) {
    if (pastes.size() > 80) {
      pastes.stream().skip(80).map(Paste::getKey).forEach(this.client::delete);
    }
  }

  private Instant toDate(Paste paste) {
    return Optional.of(paste).map(Paste::getTitle).map(title -> title.split(SERIES_SPLITTER)[1])
            .map(StringUtils::strip).filter(StringUtils::isNotBlank).map(Repository::toInstant)
            .orElseThrow(() -> new RuntimeException("Paste must have a title and a date after the series splitter"));
  }
}
