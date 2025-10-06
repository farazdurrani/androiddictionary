package com.faraz.dictionary;

import static com.faraz.dictionary.Completable.runAsync;
import static com.faraz.dictionary.JavaMailRead.readMail;
import static com.faraz.dictionary.MainActivity.CHICAGO;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.toList;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@SuppressLint("NewApi")
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class Repository {
  private static final String CRLF = "\r\n";
  private static final String EMPTY_STRING = "";
  private static final Map<String, WordEntity> inMemoryDb = new LinkedHashMap<>() {

    @Nullable
    @Override
    public WordEntity put(String key, WordEntity value) {
      key = Optional.ofNullable(key).map(String::toLowerCase).map(String::strip).orElseThrow();
      if (containsKey(key)) {
        throw new RuntimeException("yeah we don't do no god-damn duplicates: " + value + ". Previous entry: " +
                get(key));
      }
      return super.put(key, value);
    }
  };
  private static final String filename = "inmemorydb.json";
  private static final Predicate<WordEntity> REMINDED_TIME_IS_ABSENT_PREDICATE = we -> we.getRemindedTime() == null;
  private static final ZoneId CHICAGO_ZONE_ID = ZoneId.of(CHICAGO);
  private static boolean initialized = false;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final TypeFactory typeFactory = objectMapper.getTypeFactory();
  private final FileService fileService;
  private final String email;
  private final String password;
  private final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

  public Repository(String... creds) {
    this.email = creds.length > 0 ? creds[0] : EMPTY_STRING;
    this.password = creds.length > 1 ? creds[1] : EMPTY_STRING;
    this.fileService = new FileService(filename);
    init();
  }

  /**
   * This repo is initialized automatically at startup and is initialized exactly once no matter how many times the
   * constructor is invoked.
   */
  private void init() {
    // halt erroneous attempt to re-init repository;
    if (initialized) {
      System.out.println("Yeah we ain't initializing again.");
      return;
    }
    runAsync(() -> {
      try {
        String json = stripLines(new String(fileService.readFileAsByte()));
        if (StringUtils.isBlank(json)) {
          json = stripLines(readMail(email, password));
          if (StringUtils.isNotBlank(json)) {
            json = CompressUtil.decompress(Base64.decode(json, Base64.DEFAULT));
            fileService.writeFileExternalStorage(false, json);
          }
        }
        List<WordEntity> wordEntities = StringUtils.isNotBlank(json) ? objectMapper.readValue(json,
                typeFactory.constructCollectionType(List.class, WordEntity.class)) : Collections.emptyList();
        wordEntities.forEach(we -> inMemoryDb.put(we.getWord(), we));
        Optional.of(inMemoryDb).map(ObjectUtils::isNotEmpty).ifPresent(bool -> initialized = bool);
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    });
  }

  public List<String> getWords() {
    return new ArrayList<>(inMemoryDb.keySet());
  }

  public int getLength() {
    return inMemoryDb.size();
  }

  public DBResult upsert(String word) {
    String currentTime = DATE_TIME_FORMATTER.format(Instant.now(Clock.system(CHICAGO_ZONE_ID)));
    WordEntity wordEntity = inMemoryDb.get(word);
    if (wordEntity != null) {
      wordEntity.setRemindedTime(currentTime);
    } else {
      wordEntity = new WordEntity(word, currentTime, null);
      inMemoryDb.put(word, wordEntity);
    }
    flush();
    return wordEntity.getRemindedTime() == null ? DBResult.INSERT : DBResult.UPDATE;
  }

  public String getValuesAsAString() {
    try {
      return objectMapper.writeValueAsString(inMemoryDb.values());
    } catch (JsonProcessingException e) {
      return ExceptionUtils.getStackTrace(e);
    }
  }

  /**
   * Dangerous method!
   */
  public void reset() {
    inMemoryDb.clear();
    fileService.clearFile();
    initialized = false;
    init();
  }

  public List<String> getWordsForReminder(int limit) {
    return inMemoryDb.values().stream().filter(REMINDED_TIME_IS_ABSENT_PREDICATE).limit(limit).map(WordEntity::getWord)
            .collect(toList());
  }

  public long getRemindedCount() {
    return inMemoryDb.values().stream().filter(REMINDED_TIME_IS_ABSENT_PREDICATE.negate()).count();
  }

  public void unsetRemindedTime(List<String> words) {
    words.forEach(w -> Optional.ofNullable(inMemoryDb.get(w)).map(this::unsetRemindedTime)
            .orElseThrow(throwKeyNotFoundException(w)));
    flush();
  }

  public void markAsReminded(List<String> words) {
    words.forEach(w -> Optional.ofNullable(inMemoryDb.get(w)).map(this::setRemindedTime)
            .orElseThrow(throwKeyNotFoundException(w)));
    flush();
  }

  public List<String> getByRemindedTime(int limit) {
    return inMemoryDb.values().stream().filter(we -> we.getRemindedTime() != null)
            .sorted((w1, w2) -> toDateRemindedTime(w2).compareTo(toDateRemindedTime(w1)))
            .map(WordEntity::getWord).limit(limit).collect(Collectors.toList());
  }

  public void remove(String word) {
    inMemoryDb.remove(word);
    flush();
  }

  private void flush() {
    CompletableFuture.runAsync(() -> {
      try {
        fileService.writeFileExternalStorage(false, objectMapper.writeValueAsString(inMemoryDb.values()));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private WordEntity setRemindedTime(WordEntity wordEntity) {
    String currentTime = DATE_TIME_FORMATTER.format(Instant.now(Clock.system(CHICAGO_ZONE_ID)));
    wordEntity.setRemindedTime(currentTime);
    return wordEntity;
  }

  private WordEntity unsetRemindedTime(WordEntity wordEntity) {
    wordEntity.setRemindedTime(null);
    return wordEntity;
  }

  private String stripLines(String source) {
    return StringUtils.isNotBlank(source) ? source.replaceAll(CRLF, EMPTY_STRING).replaceAll(lineSeparator(),
            EMPTY_STRING) : EMPTY_STRING;
  }

  @NonNull
  private Supplier<RuntimeException> throwKeyNotFoundException(String w) {
    return () -> new RuntimeException(w + " is absent.");
  }

  private Instant toDateRemindedTime(WordEntity w) {
    return Instant.parse(w.getRemindedTime());
  }
}
