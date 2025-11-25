package com.faraz.dictionary;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Repository {
  public static final ZoneId CHICAGO_ZONE_ID = ZoneId.of(MainActivity.CHICAGO);
  private static final String TAG = Repository.class.getSimpleName();
  private static final String filename = "inmemorydb.json";
  private static final Predicate<WordEntity> REMINDED_TIME_IS_ABSENT_PREDICATE = we -> we.getRemindedTime() == null;
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

    @Override
    public WordEntity get(Object key) {
      String _key = Optional.ofNullable(key).filter(String.class::isInstance).map(String.class::cast).map(String::strip)
              .map(String::toLowerCase).orElseThrow();
      return super.get(_key);
    }

    @Nullable
    @Override
    public WordEntity remove(@Nullable Object key) {
      String _key = Optional.ofNullable(key).filter(String.class::isInstance).map(String.class::cast).map(String::strip)
              .map(String::toLowerCase).orElseThrow();
      return super.remove(_key);
    }
  };
  private static final Comparator<WordEntity> SORT_BY_REMINDED_TIME_COMPARATOR =
          (w1, w2) -> toDateRemindedTime(w2).compareTo(toDateRemindedTime(w1));
  private static final FileService fileService = new FileService(filename);
  private static boolean initialized; // mutable
  private static int lastId; //always increments. DONOT decrement. // mutable

  public Repository() {
    init();
  }

  public String getFilepath() {
    return fileService.getFilepath();
  }

  public List<String> getWords() {
    return inMemoryDb.values().stream().map(WordEntity::getWord).toList();
  }

  public int getLength() {
    return inMemoryDb.size();
  }

  public DBResult upsert(String word) {
    word = Optional.ofNullable(word).map(String::strip).map(String::toLowerCase).orElseThrow();
    String currentTime = DATE_TIME_FORMATTER.format(Instant.now(Clock.system(CHICAGO_ZONE_ID)));
    WordEntity wordEntity = inMemoryDb.get(word);
    if (wordEntity != null) {
      wordEntity.setRemindedTime(currentTime);
    } else {
      wordEntity = new WordEntity(++lastId, word, currentTime, null);
      inMemoryDb.put(word, wordEntity);
    }
    flush();
    return wordEntity.getRemindedTime() == null ? DBResult.INSERT : DBResult.UPDATE;
  }

  /**
   * Dangerous method!
   */
  public void clear() {
    fileService.clearFile();
    inMemoryDb.clear();
    initialized = false;
  }

  public List<String> getWordsForReminder(int limit) {
    return inMemoryDb.values().stream().filter(REMINDED_TIME_IS_ABSENT_PREDICATE).limit(limit).map(WordEntity::getWord)
            .toList();
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
    List<String> list = inMemoryDb.values().stream().filter(we -> we.getRemindedTime() != null)
            .sorted(SORT_BY_REMINDED_TIME_COMPARATOR).map(WordEntity::getWord).toList();
    return list.subList(0, Math.min(limit, list.size()));
  }

  public void delete(String word) {
    inMemoryDb.remove(word);
    flush();
  }

  private void flush() {
    CompletableFuture.runAsync(() -> writeOverFile(getValuesAsString()));
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

  private static Instant toDateRemindedTime(WordEntity w) {
    return toInstant(w.getRemindedTime());
  }

  private WordEntity stripWhiteSpaces(WordEntity we) {
    String word = Optional.of(we).map(WordEntity::getWord).map(String::strip).map(String::toLowerCase).orElseThrow();
    return new WordEntity(we.getId(), StringUtils.strip(word), we.getLookupTime(), we.getRemindedTime());
  }

  public void writeOverFile(String value) {
    fileService.writeFileExternalStorage(false, value);
  }

  private static Instant toInstant(String instant) {
    return Optional.ofNullable(instant).map(StringUtils::strip).filter(StringUtils::isNotBlank).map(Instant::parse)
            .orElseThrow(() -> new RuntimeException("String 'instant' cannot be empty/null"));
  }

  private List<WordEntity> toWordEntities(String json) {
    try {
      if (StringUtils.isBlank(json)) return Collections.emptyList();
      List<WordEntity> wordEntities = objectMapper.readValue(json, typeFactory.constructCollectionType(List.class,
              WordEntity.class));
      return wordEntities.stream().sorted(Comparator.comparing(WordEntity::getId)).toList();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private String getValuesAsString(Collection<WordEntity> values) {
    try {
      return values.isEmpty() ? StringUtils.EMPTY : objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException e) {
      return ExceptionUtils.getStackTrace(e);
    }
  }

  private String getValuesAsString() {
    return getValuesAsString(inMemoryDb.values());
  }

  /**
   * This repo is initialized automatically at startup and is initialized exactly once no matter how many times the
   * constructor is invoked.
   */
  private void init() {
    // halt erroneous attempt to re-init repository;
    if (initialized) {
      Log.i(TAG, "Repository already initialized.");
      return;
    }
    Completable.runAsync(() -> {
      try {
        String json = StringUtils.strip((new String(fileService.readFileAsByte())));
        toWordEntities(json).forEach(we -> inMemoryDb.put(we.getWord(), stripWhiteSpaces(we)));
        initialized = ObjectUtils.isNotEmpty(inMemoryDb);
        lastId = inMemoryDb.values().stream().max(Comparator.comparing(WordEntity::getId)).map(WordEntity::getId)
                .orElse(Integer.MIN_VALUE);
      } catch (Exception e) {
        Log.e(TAG, ExceptionUtils.getStackTrace(e));
      }
    });
  }

  public boolean isReminded(String text) {
    return StringUtils.isNotBlank(Optional.ofNullable(inMemoryDb.get(text)).map(WordEntity::getRemindedTime)
            .orElse(StringUtils.EMPTY));
  }
}
