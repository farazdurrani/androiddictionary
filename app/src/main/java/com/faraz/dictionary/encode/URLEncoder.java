package com.faraz.dictionary.encode;

import java.io.CharArrayWriter;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Objects;

/**
 * Actual URLEncoder wasn't working due to the min Android API version set in gradle.
 */
public class URLEncoder {

  private static final int caseDiff = ('a' - 'A');
  private static final BitSet dontNeedEncoding;

  static {
    dontNeedEncoding = new BitSet(256);
    int i;
    for (i = 'a'; i <= 'z'; i++) {
      dontNeedEncoding.set(i);
    }
    for (i = 'A'; i <= 'Z'; i++) {
      dontNeedEncoding.set(i);
    }
    for (i = '0'; i <= '9'; i++) {
      dontNeedEncoding.set(i);
    }
    dontNeedEncoding.set(' '); /* encoding a space to a + is done
     * in the encode() method */
    dontNeedEncoding.set('-');
    dontNeedEncoding.set('_');
    dontNeedEncoding.set('.');
    dontNeedEncoding.set('*');
  }

  public static String encode(String s) {
    Objects.requireNonNull(StandardCharsets.UTF_8, "charset");

    boolean needToChange = false;
    StringBuilder out = new StringBuilder(s.length());
    CharArrayWriter charArrayWriter = new CharArrayWriter();

    for (int i = 0; i < s.length(); ) {
      int c = s.charAt(i);
      //System.out.println("Examining character: " + c);
      if (dontNeedEncoding.get(c)) {
        if (c == ' ') {
          c = '+';
          needToChange = true;
        }
        //System.out.println("Storing: " + c);
        out.append((char) c);
        i++;
      } else {
        // convert to external encoding before hex conversion
        do {
          charArrayWriter.write(c);
          /*
           * If this character represents the start of a Unicode
           * surrogate pair, then pass in two characters. It's not
           * clear what should be done if a byte reserved in the
           * surrogate pairs range occurs outside of a legal
           * surrogate pair. For now, just treat it as if it were
           * any other character.
           */
          if (c >= 0xD800 && c <= 0xDBFF) {
                        /*
                          System.out.println(Integer.toHexString(c)
                          + " is high surrogate");
                        */
            if ((i + 1) < s.length()) {
              int d = s.charAt(i + 1);
                            /*
                              System.out.println("\tExamining "
                              + Integer.toHexString(d));
                            */
              if (d >= 0xDC00 && d <= 0xDFFF) {
                                /*
                                  System.out.println("\t"
                                  + Integer.toHexString(d)
                                  + " is low surrogate");
                                */
                charArrayWriter.write(d);
                i++;
              }
            }
          }
          i++;
        } while (i < s.length() && !dontNeedEncoding.get((c = s.charAt(i))));

        charArrayWriter.flush();
        String str = new String(charArrayWriter.toCharArray());
        byte[] ba = str.getBytes(StandardCharsets.UTF_8);
        for (int j = 0; j < ba.length; j++) {
          out.append('%');
          char ch = Character.forDigit((ba[j] >> 4) & 0xF, 16);
          // converting to use uppercase letter as part of
          // the hex value if ch is a letter.
          if (Character.isLetter(ch)) {
            ch -= caseDiff;
          }
          out.append(ch);
          ch = Character.forDigit(ba[j] & 0xF, 16);
          if (Character.isLetter(ch)) {
            ch -= caseDiff;
          }
          out.append(ch);
        }
        charArrayWriter.reset();
        needToChange = true;
      }
    }

    return (needToChange ? out.toString() : s);
  }
}
