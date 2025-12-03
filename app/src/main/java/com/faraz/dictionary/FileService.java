package com.faraz.dictionary;

import android.os.Environment;
import android.util.Log;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class FileService {

  public static final String TAG = FileService.class.getSimpleName();
  //Initialize it once when the app is loading up
  private static String externalFilesDir;
  private final String filename;

  public FileService(String filename, String... folder) {
    externalFilesDir = folder.length > 0 ? folder[0] : externalFilesDir;
    this.filename = filename;
    try {
      File file = new File(externalFilesDir, filename);
      if (file.createNewFile()) {
        Log.i(TAG, String.format(Locale.US, "Successfully created file at %s", file.getAbsolutePath()));
      } else {
        Log.i(TAG, String.format(Locale.US, "File already exists at %s", file.getAbsolutePath()));
      }
    } catch (IOException e) {
      Log.e(TAG, ExceptionUtils.getStackTrace(e));
      throw new RuntimeException(e);
    }
  }

  public String getFilepath() {
    return new File(externalFilesDir, filename).getAbsolutePath();
  }

  /**
   * @param append -- TRUE IF WANT TO APPEND. FALSE FOR COMPLETE WRITE-OVER.
   */
  public void writeFileExternalStorage(boolean append, String... words) {

    //Checking the availability state of the External Storage.
    String state = Environment.getExternalStorageState();
    if (!Environment.MEDIA_MOUNTED.equals(state)) {
      Log.e(TAG, "can't write to the file.");
      return;
    }

    //Create a new file that points to the root directory, with the given name:

    //This point and below is responsible for the write operation
    FileOutputStream outputStream;
    try {
      //second argument of FileOutputStream constructor indicates whether
      //to append or create new file if one exists
      outputStream = new FileOutputStream(new File(externalFilesDir, filename), append);

      //todo extremely slow if too many words. Just use String.join(lineSeparator(), words). You do this at one place
      // before calling this method.
      for (String word : words) {
        outputStream.write(word.getBytes());
        outputStream.write(System.lineSeparator().getBytes());
      }
      outputStream.flush();
      outputStream.close();
    } catch (Exception e) {
      Log.e(TAG, ExceptionUtils.getStackTrace(e));
    }
  }

  /**
   * Since no data is written between the creation and closing of the stream in this specific snippet,
   * the primary effect is to create an empty file at the specified location if it doesn't already exist,
   * or to truncate an existing file to zero length.
   */
  public void clearFile() {
    try {
      new FileOutputStream(new File(externalFilesDir, filename)).close();
    } catch (IOException e) {
      Log.e(TAG, ExceptionUtils.getStackTrace(e));
      throw new RuntimeException(e);
    }
  }

  public List<String> readFile() {
    try (BufferedReader buffer = new BufferedReader(new FileReader(new File(externalFilesDir, filename)))) {
      List<String> lines = new ArrayList<>();
      String line;
      while ((line = buffer.readLine()) != null) {
        lines.add(line);
      }
      return lines;
    } catch (Exception e) {
      Log.e(TAG, ExceptionUtils.getStackTrace(e));
      return Collections.emptyList();
    }
  }

  public byte[] readFileAsByte() {
    File myFile = new File(externalFilesDir, filename);
    byte[] byteArray = new byte[(int) myFile.length()];
    try (FileInputStream inputStream = new FileInputStream(myFile)) {
      int ignore = inputStream.read(byteArray);
    } catch (IOException e) {
      Log.e(TAG, ExceptionUtils.getStackTrace(e));
      return new byte[0];
    }
    return byteArray;
  }

  public void delete(String word) {
    String[] words = readFile().stream().filter(w -> !w.equals(word)).distinct().toArray(String[]::new);
    Optional.of(words).filter(ObjectUtils::isEmpty)
            .ifPresentOrElse(ignore -> clearFile(), () -> writeFileExternalStorage(false,
                    String.join(System.lineSeparator(), words)));
  }
}
