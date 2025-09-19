package com.faraz.dictionary;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.toList;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.apache.commons.lang3.ObjectUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FileService {


  //Initialize it once when the app is loading up
  public static String externalFilesDir;
  private final String filename;

  @SuppressLint("NewApi")
  public FileService(String filename, String... folder) {
    externalFilesDir = folder.length > 0 ? folder[0] : externalFilesDir;
    this.filename = filename;
    try {
      if (!Files.exists(Paths.get(externalFilesDir))) {
        Files.createDirectories(Paths.get(externalFilesDir));
      }
      new File(externalFilesDir, filename).createNewFile();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @param append -- TRUE IF WANT TO APPEND. FALSE FOR COMPLETE WRITE-OVER.
   */
  public void writeFileExternalStorage(boolean append, String... words) {

    //Checking the availability state of the External Storage.
    String state = Environment.getExternalStorageState();
    if (!Environment.MEDIA_MOUNTED.equals(state)) {
      Log.e("MainActivity", "can't write to the file.");
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
        outputStream.write(lineSeparator().getBytes());
      }
      outputStream.flush();
      outputStream.close();
    } catch (Exception e) {
      Log.e("MainActivity", "Something went wrong", e);
    }
  }

  public void clearFile() {
    try {
      new FileOutputStream(new File(externalFilesDir, filename)).close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public List<String> readFile() {
    try {
      return Files.readAllLines(Paths.get(new File(externalFilesDir, filename).toURI()))
              .stream().distinct().collect(toList());
    } catch (Exception e) {
      Log.e(this.getClass().getSimpleName(), "Error", e);
      return Collections.emptyList();
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.O)
  public byte[] readFileAsByte() {
    try {
      return Files.readAllBytes(Paths.get(new File(externalFilesDir, filename).toURI()));
    } catch (Exception e) {
      Log.e(this.getClass().getSimpleName(), "Error", e);
      return new byte[0];
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  public void delete(String word) {
    String[] words = readFile().stream().filter(w -> !w.equals(word)).distinct().toArray(String[]::new);
    Optional.of(words).filter(ObjectUtils::isEmpty)
            .ifPresentOrElse(ignore -> writeFileExternalStorage(false), () -> writeFileExternalStorage(false,
                    String.join(lineSeparator(), words)));
  }
}
