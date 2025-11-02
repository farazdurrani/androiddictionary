package com.faraz.dictionary.jsonflattener;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Thanks to the  @author Mark Paluch of Spring Vault
 */
public class JsonMapFlattener {

  private JsonMapFlattener() {
  }

  /**
   * Flatten a hierarchical {@link Map} into a flat {@link Map} with key names using
   * property dot notation.
   *
   * @param inputMap must not be {@literal null}.
   * @return the resulting {@link Map}.
   */
  public static Map<String, Object> flatten(Map<String, ? extends Object> inputMap) {
    Map<String, Object> resultMap = new LinkedHashMap<>();
    doFlatten("", inputMap.entrySet().iterator(), resultMap, UnaryOperator.identity());
    return resultMap;
  }

  public static Map<String, String> flattenToStringMap(Map<String, ? extends Object> inputMap) {
    Map<String, String> resultMap = new LinkedHashMap<>();
    doFlatten("", inputMap.entrySet().iterator(), resultMap, it -> it == null ? null : it.toString());
    return resultMap;
  }

  private static void doFlatten(String propertyPrefix, Iterator<? extends Entry<String, ?>> inputMap,
                                Map<String, ? extends Object> resultMap, Function<Object, Object> valueTransformer) {

    if (hasText(propertyPrefix)) {
      propertyPrefix = propertyPrefix + ".";
    }

    while (inputMap.hasNext()) {

      Entry<String, ? extends Object> entry = inputMap.next();
      flattenElement(propertyPrefix.concat(entry.getKey()), entry.getValue(), resultMap, valueTransformer);
    }
  }

  @SuppressWarnings("unchecked")
  private static void flattenElement(String propertyPrefix, Object source, Map<String, ?> resultMap,
                                     Function<Object, Object> valueTransformer) {

    if (source instanceof Iterable) {
      flattenCollection(propertyPrefix, (Iterable<Object>) source, resultMap, valueTransformer);
      return;
    }

    if (source instanceof Map) {
      doFlatten(propertyPrefix, ((Map<String, ?>) source).entrySet().iterator(), resultMap, valueTransformer);
      return;
    }

    ((Map) resultMap).put(propertyPrefix, valueTransformer.apply(source));
  }

  private static void flattenCollection(String propertyPrefix, Iterable<Object> iterable, Map<String, ?> resultMap,
                                        Function<Object, Object> valueTransformer) {

    int counter = 0;

    for (Object element : iterable) {
      flattenElement(propertyPrefix + "[" + counter + "]", element, resultMap, valueTransformer);
      counter++;
    }
  }

  /**
   *  * Thanks to these several authors:
   *  * @author Rod Johnson
   *  * @author Juergen Hoeller
   *  * @author Keith Donald
   *  * @author Rob Harrop
   *  * @author Rick Evans
   *  * @author Arjen Poutsma
   *  * @author Sam Brannen
   *  * @author Brian Clozel
   *  * @author Sebastien Deleuze
   *  of Spring's StringUtils class.
   */
  public static boolean hasText(CharSequence str) {
    if (str == null) {
      return false;
    }

    int strLen = str.length();
    if (strLen == 0) {
      return false;
    }

    for (int i = 0; i < strLen; i++) {
      if (!Character.isWhitespace(str.charAt(i))) {
        return true;
      }
    }
    return false;
  }
}
