package com.faraz.dictionary;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import android.util.Log;

import com.pastebin.api.Expiration;
import com.pastebin.api.Format;
import com.pastebin.api.PastebinClient;
import com.pastebin.api.Visibility;
import com.pastebin.api.request.PasteRequest;

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
    this.client = PastebinClient.builder().developerKey(values[0]).userKey(values[1]).build();
    this.series = Instant.now().toEpochMilli();
  }

  public void create(String content) {
    final PasteRequest pasteRequest = PasteRequest
            .content(content)
            .visibility(Visibility.PRIVATE)
            .format(Format.JSON)
            .name(String.format(Locale.US, "Words backup %d %s %s", this.series, SERIES_SPLITTER,
                    Instant.now().toString()))
            .expiration(Expiration.NEVER)
            .folderKey("WordBkup")
            .build();
    try {
      this.client.paste(pasteRequest);
    } catch (Exception e) {
      Log.e(TAG, "error...", e);
      throw new RuntimeException(e);
    }
  }

  public List<String> lastBackupAndCleanup() {
    List<PasteExtension> pastes = this.client.list(100).stream().map(PasteExtension::create)
            .sorted((p1, p2) -> toDate(p2).compareTo(toDate(p1))).toList();
    deleteLastFew(pastes);
    String firstTitleWithSeries = EMPTY;
    List<PasteExtension> pertinentPastes = new ArrayList<>();
    if (!pastes.isEmpty()) {
      firstTitleWithSeries = splitTitleBySeries(pastes.get(0));
      pertinentPastes.add(pastes.get(0));
    }
    for (int i = 1; StringUtils.isNotBlank(firstTitleWithSeries) && i < pastes.size(); i++) {
      if (firstTitleWithSeries.equals(splitTitleBySeries(pastes.get(i)))) {
        pertinentPastes.add(pastes.get(i));
      }
    }
    return pertinentPastes.stream().sorted(Comparator.comparing(PasteExtension::getKey)).map(PasteExtension::getKey)
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

  private String splitTitleBySeries(PasteExtension paste) {
    return paste.getTitle().split(SERIES_SPLITTER)[0];
  }

  private void deleteLastFew(List<PasteExtension> pastes) {
    if (pastes.size() > 80) {
      pastes.stream().skip(80).map(PasteExtension::getKey).forEach(this.client::delete);
    }
  }

  private Instant toDate(PasteExtension paste) {
    return Optional.of(paste).map(PasteExtension::getTitle).map(title -> title.split(SERIES_SPLITTER)[1])
            .map(StringUtils::strip).filter(StringUtils::isNotBlank).map(Repository::toInstant)
            .orElseThrow(() -> new RuntimeException("Paste must have a title and a date after the series splitter"));
  }
}
