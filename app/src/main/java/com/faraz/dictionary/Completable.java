package com.faraz.dictionary;

import java.util.function.Supplier;

public class Completable<T> {

  private final Object result;

  public Completable() {
    this.result = new Completable<>(new Object()).getResult();
  }

  public Completable(Object result) {
    this.result = result;
  }

  public static Completable<Void> runSync(Supplier s) {
    s.get();
    return new Completable<>();
  }

  public static Completable<Void> thenRunSync(Supplier s) {
    return runSync(s);
  }

  private Object getResult() {
    return result;
  }
}
