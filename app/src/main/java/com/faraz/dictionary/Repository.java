package com.faraz.dictionary;

import static com.faraz.dictionary.CollectionOptional.ofEmptyable;
import static com.faraz.dictionary.Completable.runAsync;
import static com.faraz.dictionary.JavaMailRead.readMail;
import static java.lang.System.lineSeparator;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.util.stream.Collectors.toMap;

import android.annotation.SuppressLint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class Repository {
  private static final String CRLF = "\r\n";
  private static final String EMPTY_STRING = "";
  private static final Map<String, WordEntity> inMemoryDb = new HashMap<>();
  private static final String filename = "inmemorydb.json";
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final TypeFactory typeFactory = objectMapper.getTypeFactory();
  private final FileService fileService;
  private final String email;
  private final String password;

  public Repository(String email, String password) {
    this.email = email;
    this.password = password;
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
        String json = new String(fileService.readFileAsByte()).replaceAll(CRLF, EMPTY_STRING)
                .replaceAll(lineSeparator(), EMPTY_STRING);
        if (StringUtils.isBlank(json)) {
          json = readMail(email, password).replaceAll(CRLF, EMPTY_STRING).replaceAll(lineSeparator(),
                  EMPTY_STRING);
          ofEmptyable(json).ifPresent(_json -> CompletableFuture.runAsync(() -> fileService.writeString(_json)));
        }
        List<WordEntity> wordEntities = objectMapper.readValue(json,
                typeFactory.constructCollectionType(List.class, WordEntity.class));
        Map<String, WordEntity> wordMap = wordEntities.stream().collect(toMap(WordEntity::getWord,
                Function.identity()));
        inMemoryDb.putAll(wordMap);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  public List<String> getWords() {
    return new ArrayList<>(inMemoryDb.keySet());
  }
}
