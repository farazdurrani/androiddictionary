package com.faraz.dictionary;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
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
  private static final String TAG = Repository.class.getSimpleName();
  private static final String filename = "inmemorydb.csv";
  private static final Predicate<WordEntity> REMINDED_TIME_IS_ABSENT_PREDICATE = we -> we.getRemindedTime() == 0;
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss")
          .withZone(ZoneId.systemDefault());
  private static final CsvMapper MAPPER = getCSVMapper();
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
          Comparator.comparingLong(WordEntity::getRemindedTime).reversed();
  private static final FileService fileService = new FileService(filename);
  private static boolean initialized; // mutable

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
    long currentTime = Instant.now().toEpochMilli();
    WordEntity wordEntity = inMemoryDb.get(word);
    if (wordEntity != null) {
      wordEntity.setRemindedTime(currentTime);
    } else {
      wordEntity = new WordEntity(word, currentTime, 0);
      inMemoryDb.put(word, wordEntity);
    }
    flush();
    return wordEntity.getRemindedTime() == 0 ? DBResult.INSERT : DBResult.UPDATE;
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
    List<String> list = inMemoryDb.values().stream().filter(we -> we.getRemindedTime() != 0)
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
    //TODO another stop-gap solution to handle duplicate remind times. Sleep before setting the remindedTime.
    try {
      Thread.sleep(2L);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    wordEntity.setRemindedTime(Instant.now().toEpochMilli());
    return wordEntity;
  }

  private WordEntity unsetRemindedTime(WordEntity wordEntity) {
    wordEntity.setRemindedTime(0);
    return wordEntity;
  }

  @NonNull
  private Supplier<RuntimeException> throwKeyNotFoundException(String w) {
    return () -> new RuntimeException(w + " is absent.");
  }

  private WordEntity stripWhiteSpaces(WordEntity we) {
    String word = Optional.of(we).map(WordEntity::getWord).map(StringUtils::strip).map(String::toLowerCase)
            .orElseThrow();
    return new WordEntity(word, we.getLookupTime(), we.getRemindedTime());
  }

  public void writeOverFile(String value) {
    fileService.writeFileExternalStorage(false, value);
  }

  private String getValuesAsString(Collection<WordEntity> values) {
    try {
      return values.isEmpty() ? StringUtils.EMPTY : MAPPER.writer(getSchema()).writeValueAsString(values);
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
        readCsv().forEach(we -> inMemoryDb.put(we.getWord(), stripWhiteSpaces(we)));
        initialized = ObjectUtils.isNotEmpty(inMemoryDb);
      } catch (Exception e) {
        Log.e(TAG, ExceptionUtils.getStackTrace(e));
      }
    });
  }

  private List<WordEntity> readCsv() {
    try (MappingIterator<WordEntity> iterator = MAPPER.readerFor(WordEntity.class).with(getSchema())
            .readValues(fileService.readFileAsByte())) {
      return iterator.readAll();
    } catch (IOException e) {
      Log.e(TAG, ExceptionUtils.getStackTrace(e));
      return Collections.emptyList();
    }
  }

  private static CsvSchema getSchema() {
    return MAPPER.schemaFor(WordEntity.class).withHeader().withColumnSeparator(';');
  }

  public boolean isReminded(String word) {
    WordEntity we = inMemoryDb.get(word);
    return we != null && we.getRemindedTime() != 0;
  }

  private static CsvMapper getCSVMapper() {
    CsvMapper mapper = new CsvMapper();
    mapper.enable(CsvGenerator.Feature.STRICT_CHECK_FOR_QUOTING);
    // 1. Allows empty text cells to be evaluated as a form of null
    mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
    // 2. Tells Jackson: "When you see a null/empty cell for a primitive,
    //    don't throw an error, just assign the Java primitive default (which is 0)"
    mapper.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    mapper.enable(CsvParser.Feature.SKIP_EMPTY_LINES);
    mapper.enable(CsvParser.Feature.TRIM_SPACES);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    return mapper;
  }

  public String getWordInfo(String word) {
    WordEntity we = Optional.ofNullable(inMemoryDb.get(word)).orElseThrow(throwKeyNotFoundException(word));
    return we.getWord() + System.lineSeparator() + convertMillisToReadableTime(we.getLookupTime()) +
            System.lineSeparator() + convertMillisToReadableTime(we.getRemindedTime());
  }

  private String convertMillisToReadableTime(long time) {
    return time == 0 ? StringUtils.EMPTY : DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(time));
  }
}
