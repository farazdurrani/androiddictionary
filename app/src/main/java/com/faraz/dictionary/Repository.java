package com.faraz.dictionary;

import static com.faraz.dictionary.JavaMailRead.readMail;
import static com.faraz.dictionary.MainActivity.CHICAGO;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Repository {
  private static final String TAG = Repository.class.getSimpleName();
  private static final String filename = "inmemorydb.json";
  private static final Predicate<WordEntity> REMINDED_TIME_IS_ABSENT_PREDICATE = we -> we.getRemindedTime() == null;
  private static final ZoneId CHICAGO_ZONE_ID = ZoneId.of(CHICAGO);
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final TypeFactory typeFactory = objectMapper.getTypeFactory();
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;
  private static final Map<String, WordEntity> inMemoryDb = new LinkedHashMap<>() {
    @Nullable
    @Override
    public WordEntity put(String key, WordEntity value) {
      key = Optional.ofNullable(key).map(String::strip).map(String::toLowerCase).orElseThrow();
      if (containsKey(key)) {
        throw new RuntimeException("yeah we don't do no god-damn duplicates: " + value + ". Previous entry: " +
                get(key));
      }
      return super.put(key, value);
    }
  };

  private static boolean initialized;
  private static FileService fileService;
  private static String email;
  private static String password;

  public Repository(String... creds) {
    email = creds.length > 0 ? creds[0] : EMPTY;
    password = creds.length > 1 ? creds[1] : EMPTY;
    fileService = new FileService(filename);
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
    Completable.runAsync(() -> {
      try {
        String json = StringUtils.strip((new String(fileService.readFileAsByte())));
        if (StringUtils.isBlank(json)) {
          json = StringUtils.strip(readMail(email, password));
          if (StringUtils.isNotBlank(json)) {
            json = CompressUtil.decompress(Base64.decode(json, Base64.DEFAULT));
            writeOverFile(json);
          }
        }
        List<WordEntity> wordEntities = StringUtils.isBlank(json) ? Collections.emptyList() :
                objectMapper.readValue(json, typeFactory.constructCollectionType(List.class, WordEntity.class));
        wordEntities.forEach(we -> inMemoryDb.put(we.getWord(), stripWhiteSpaces(we)));
        Optional.of(inMemoryDb).map(ObjectUtils::isNotEmpty).ifPresent(bool -> initialized = bool);
      } catch (Exception e) {
        Log.e(TAG, "error...", e);
      }
    });
  }

  public List<String> getWords() {
    return ImmutableList.copyOf(inMemoryDb.keySet());
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
            .collect(Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf));
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
            .map(WordEntity::getWord).limit(limit)
            .collect(Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf));
  }

  public void delete(String word) {
    inMemoryDb.remove(word);
    flush();
  }

  private void flush() {
    CompletableFuture.runAsync(() -> writeOverFile(getValuesAsAString()));
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

  @NonNull
  private Supplier<RuntimeException> throwKeyNotFoundException(String w) {
    return () -> new RuntimeException(w + " is absent.");
  }

  private Instant toDateRemindedTime(WordEntity w) {
    return Instant.parse(w.getRemindedTime());
  }

  private WordEntity stripWhiteSpaces(WordEntity we) {
    return new WordEntity(StringUtils.strip(we.getWord()), we.getLookupTime(), we.getRemindedTime());
  }

  private void writeOverFile(String value) {
    fileService.writeFileExternalStorage(false, value);
  }
}
