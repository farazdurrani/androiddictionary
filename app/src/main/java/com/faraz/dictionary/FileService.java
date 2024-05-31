package com.faraz.dictionary;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static java.lang.System.lineSeparator;

import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class FileService {

    private final File externalFilesDir;
    private final String filename;

    @SuppressWarnings("all")
    public FileService(File externalFilesDir, String filename) {
        this.externalFilesDir = externalFilesDir;
        this.filename = filename;
        try {
            new File(externalFilesDir, filename).createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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

    public void clearFile(){
        try {
            new FileOutputStream(new File(externalFilesDir, filename)).close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public String[] readFile() {
        try {
            return Files.readAllLines(Paths.get(new File(externalFilesDir, filename).toURI()))
                    .stream().distinct().toArray(String[]::new);
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "Error", e);
            return new String[]{ExceptionUtils.getStackTrace(e)};
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void delete(String word) {
        String[] words = Arrays.stream(readFile()).sequential().filter(w -> !w.equals(word))
                .distinct().toArray(String[]::new);
        writeFileExternalStorage(false, words);
    }
}
