package com.faraz.dictionary;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Optional;

public class CollectionOptional<T> {
  public static <T> Optional<T> ofEmptyable(T value) {
    return ObjectUtils.isEmpty(value) ? Optional.empty() : Optional.of(value);
  }
}
