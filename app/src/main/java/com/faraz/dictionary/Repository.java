package com.faraz.dictionary;

import static com.faraz.dictionary.Completable.runAsync;

import static java.util.stream.Collectors.toMap;

import android.annotation.SuppressLint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Repository {
  private static final Map<String, String> inMemoryDb = new HashMap<>();
  private static final String CRLF = "\r\n";
  private static final String filename = "inmemorydb.txt";
  private final FileService fileService;

  public Repository() {
    this.fileService = new FileService(filename);
    init();
  }

  /**
   * This repo is initialized automatically at startup and is initialized exactly once no matter how many times the
   * constructor is invoked.
   */
  @SuppressLint("NewApi")
  private void init() {
    // erroneous attempt to re-init repository;
    if (!inMemoryDb.isEmpty()) {
      System.out.println("Yeah we ain't initializing again.");
      return;
    }
    //if duplicate key, do throw an error.
    runAsync(() -> inMemoryDb.putAll(fileService.readFile().stream().collect(toMap(x -> x.substring(0, x.indexOf(",")).toLowerCase(),
            Function.identity()))));
  }

  public List<String> getWords() {
    return new ArrayList<>(inMemoryDb.keySet());
  }
}
