package com.faraz.dictionary;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressUtil {

  public static byte[] compress(String str) {
    if (str == null || str.isEmpty()) {
      return null;
    }
    try (ByteArrayOutputStream obj = new ByteArrayOutputStream();
         GZIPOutputStream gzip = new GZIPOutputStream(obj)) {
      gzip.write(str.getBytes(StandardCharsets.UTF_8));
      return obj.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String decompress(byte[] str) {
    if (str == null) {
      return null;
    }
    try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(str));
         BufferedReader bf = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
      StringBuilder outStr = new StringBuilder();
      String line;
      while ((line = bf.readLine()) != null) {
        outStr.append(line);
      }
      return outStr.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }
}

