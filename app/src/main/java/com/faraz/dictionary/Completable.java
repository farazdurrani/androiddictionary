package com.faraz.dictionary;

import android.util.Log;

import java.util.concurrent.CompletableFuture;

public class Completable<T> {

  private static final String TAG = Completable.class.getSimpleName();
  private final Object result;

  public Completable() {
    this.result = new Completable<>(new Object()).getResult();
  }

  public Completable(Object result) {
    this.result = result;
  }

  public static Completable<Void> runSync(Operation s) {
    s.run();
    return new Completable<>();
  }

  public Completable<Void> thenRunSync(Operation s) {
    return runSync(s);
  }

  //still synchronous
  public static Completable<Void> runAsync(Runnable r) {
    CompletableFuture<Void> cf = CompletableFuture.runAsync(r);
    while (!cf.isDone()) {
      // till I die
    }
    cf.exceptionally(ex -> {
      Log.e(TAG, "error..", ex);
      return null;
    });
    return new Completable<>();
  }

  public Completable<Void> thenRunAsync(Runnable r) {
    return runAsync(r);
  }

  private Object getResult() {
    return result;
  }

  @FunctionalInterface
  public interface Operation {
    void run();
  }
}
