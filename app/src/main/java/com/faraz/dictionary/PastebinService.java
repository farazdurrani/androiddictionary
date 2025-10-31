package com.faraz.dictionary;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;

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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Create an instance of this class inside a method and not have it at an object level so as to keep the
 * {@link #ASSOCIATION}
 * intact.
 */
public class PastebinService {
  private static final String SERIES_SPLITTER = "====";
  private static final String TAG = PastebinService.class.getSimpleName();
  public static final Supplier<RuntimeException> TITLE_MISALIGNED_MESSAGE =
          () -> new RuntimeException("Paste must have a title, date, and the series identifier after " +
                  "the series splitter");
  private final PastebinClient client;
  private final long ASSOCIATION;

  public PastebinService(String... values) {
    if (values == null || values.length != 2) {
      throw new RuntimeException("Provide pastebin devkey and userkey");
    }
    this.client = new PastebinClient(values[0], values[1]);
    this.ASSOCIATION = Instant.now().toEpochMilli();
  }

  public List<String> create(List<String> content, int length) {
    try {
      return IntStream.range(0, content.size()).mapToObj(index -> pageRequest(content.get(index), length, index))
              .parallel().map(client::paste).toList();
    } catch (Exception e) {
      Log.e(TAG, "error...", e);
      throw new RuntimeException(e);
    }
  }

  public List<String> getLastBackupKeysAndCleanup() {
    List<Paste> pastes = client.list(40).stream().sorted((p1, p2) -> toDate(p2).compareTo(toDate(p1))).toList();
    deleteAfter30(pastes);
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
    return pertinentPastes.stream().sorted((p1, p2) -> toDigit(p1).compareTo(toDigit(p2))).map(Paste::getKey)
            .toList();
  }

  /**
   * @param pasteKeys - must contain the full pastebin URL
   */
  public List<String> get(List<String> pasteKeys) {
    try {
      return pasteKeys.stream().parallel().map(client::getUserPaste).toList();
    } catch (Exception e) {
      Log.e(TAG, "error...", e);
      throw new RuntimeException(e);
    }
  }

  private String splitTitleBySeries(Paste paste) {
    return paste.getTitle().split(SERIES_SPLITTER)[0];
  }

  private void deleteAfter30(List<Paste> pastes) {
    pastes.stream().skip(30).map(Paste::getKey).parallel().forEach(client::delete);
  }

  private Instant toDate(Paste paste) {
    return Optional.of(paste).map(Paste::getTitle).map(title -> title.split(SERIES_SPLITTER))
            .flatMap(arr -> Optional.of(arr).map(_arr -> _arr[0].split(SPACE))).map(_arr -> _arr[3])
            .map(StringUtils::strip).filter(StringUtils::isNotBlank).map(Long::valueOf).map(Instant::ofEpochMilli)
            .orElseThrow(TITLE_MISALIGNED_MESSAGE);
  }

  private Integer toDigit(Paste paste) {
    return Optional.of(paste).map(Paste::getTitle).map(title -> title.split(SERIES_SPLITTER)[2])
            .map(StringUtils::strip).filter(StringUtils::isNotBlank).map(Integer::valueOf)
            .orElseThrow(TITLE_MISALIGNED_MESSAGE);
  }

  private PasteRequest pageRequest(String content, int length, int index) {
    return new PasteRequest(content, Format.JSON, Visibility.PUBLIC,
            String.format(Locale.US, "%d Words backup %d %s %s %s %d", length, ASSOCIATION, SERIES_SPLITTER,
                    Instant.now().toString(), SERIES_SPLITTER, index), Expiration.NEVER, "WordBkup");
  }
}
