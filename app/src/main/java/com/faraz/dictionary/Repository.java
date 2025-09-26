package com.faraz.dictionary;

import static com.faraz.dictionary.Completable.runAsync;
import static com.faraz.dictionary.Completable.runSync;
import static com.faraz.dictionary.JavaMailRead.readMail;
import static com.faraz.dictionary.MainActivity.CHICAGO;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.toList;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Base64;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Repository {
  private static final String CRLF = "\r\n";
  private static final String EMPTY_STRING = "";
  private static final Map<String, WordEntity> inMemoryDb = new LinkedHashMap<>() {
    @Nullable
    @Override
    public WordEntity put(String key, WordEntity value) {
      if (containsKey(key)) {
        throw new RuntimeException(
                "yeah we don't allow no god-damn duplicates: " + value + ". Previous entry: " + get(key));
      }
      return super.put(key, value);
    }
  };
  private static final String filename = "inmemorydb.json";
  private static final Predicate<WordEntity> REMINDED_TIME_IS_ABSENT_PREDICATE = we -> we.getRemindedTime() == null;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final TypeFactory typeFactory = objectMapper.getTypeFactory();
  private final FileService fileService;
  private final String email;
  private final String password;
  @SuppressLint("NewApi")
  private final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

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
  @SuppressLint("NewApi")
  private void init() {
    // halt erroneous attempt to re-init repository;
    if (!inMemoryDb.isEmpty()) {
      System.out.println("Yeah we ain't initializing again.");
      return;
    }
    runAsync(() -> {
      try {
        String json = clearNewLines(new String(fileService.readFileAsByte()));
        if (StringUtils.isBlank(json)) {
          json = CompressUtil.decompress(Base64.decode(clearNewLines(readMail(email, password)), Base64.DEFAULT));
          fileService.writeFileExternalStorage(false, json);
        }
        List<WordEntity> wordEntities = objectMapper.readValue(json, typeFactory.constructCollectionType(List.class,
                WordEntity.class));
        wordEntities.forEach(we -> inMemoryDb.put(we.getWord().toLowerCase().strip(), we));
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

  @SuppressLint("NewApi")
  public DBResult upsert(String word) {
    word = word.toLowerCase();
    DBResult dbResult;
    String currentTime = formatter.format(Instant.now(Clock.system(ZoneId.of(CHICAGO))));
    WordEntity wordEntity;
    if (inMemoryDb.containsKey(word)) {
      wordEntity = inMemoryDb.get(word);
      wordEntity.setRemindedTime(currentTime);
      dbResult = DBResult.UPDATE;
    } else {
      wordEntity = new WordEntity(word, currentTime, null);
      dbResult = DBResult.INSERT;
    }
    inMemoryDb.put(word, wordEntity);
    flush();
    return dbResult;
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
    fileService.writeFileExternalStorage(false);
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
    words.stream().map(inMemoryDb::get).map(this::unsetRemindedTime).forEach(we -> inMemoryDb.put(we.getWord(), we));
    flush();
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public void markAsReminded(List<String> words) {
    words.stream().map(inMemoryDb::get).map(this::setRemindedTime).forEach(we -> inMemoryDb.put(we.getWord(), we));
    flush();
  }

  public List<String> getByRemindedTime(int limit) {
    return inMemoryDb.values().stream().filter(we -> we.getRemindedTime() != null)
            .sorted(Comparator.comparing(WordEntity::getRemindedTime).reversed()).limit(limit)
            .map(WordEntity::getWord).collect(toList());
  }

  public void remove(String word) {
    inMemoryDb.remove(word);
    flush();
  }

  private void flush() {
    runSync(() -> {
      try {
        fileService.writeFileExternalStorage(false, objectMapper.writeValueAsString(inMemoryDb.values()));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  private WordEntity setRemindedTime(WordEntity wordEntity) {
    String currentTime = formatter.format(Instant.now(Clock.system(ZoneId.of(CHICAGO))));
    wordEntity.setRemindedTime(currentTime);
    return wordEntity;
  }

  private WordEntity unsetRemindedTime(WordEntity wordEntity) {
    wordEntity.setRemindedTime(null);
    return wordEntity;
  }

  private String clearNewLines(String source) {
    return source.replaceAll(CRLF, EMPTY_STRING).replaceAll(lineSeparator(), EMPTY_STRING);
  }
}
