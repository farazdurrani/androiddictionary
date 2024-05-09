package com.faraz.dictionary;

import static java.lang.System.lineSeparator;

import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileService {

    private final File externalFilesDir;
    private final String filename;

    public FileService(File externalFilesDir, String filename) {
        this.externalFilesDir = externalFilesDir;
        this.filename = filename;
    }

    public void writeFileExternalStorage(String... words) {

        //Checking the availability state of the External Storage.
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            Log.e("MainActivity", "can't write to the file.");
            return;
        }

        //Create a new file that points to the root directory, with the given name:
        File file = new File(externalFilesDir, filename);

        //This point and below is responsible for the write operation
        FileOutputStream outputStream;
        try {
            file.createNewFile();
            //second argument of FileOutputStream constructor indicates whether
            //to append or create new file if one exists
            outputStream = new FileOutputStream(file, true);

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

    @RequiresApi(api = Build.VERSION_CODES.O)
    public String[] readFile() {
        File file = new File(externalFilesDir, filename);
        try {
            return Files.readAllLines(Paths.get(file.toURI())).toArray(new String[0]);
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), "Error", e);
            return new String[0];
        }
    }

//    public String[] readFile() {
//        String state = Environment.getExternalStorageState();
//        if (!Environment.MEDIA_MOUNTED.equals(state)) {
//            Log.e(this.getClass().getSimpleName(), "can't read from the file.");
//            return new String[0];
//        }
//
//        //Create a new file that points to the root directory, with the given name:
//        File file = new File(externalFilesDir, filename);
//
//        //This point and below is responsible for the write operation
//        FileInputStream input;
//        List<String> words = new ArrayList<>();
//        try {
//            //second argument of FileOutputStream constructor indicates whether
//            //to append or create new file if one exists
//            input = new FileInputStream(file);
//            int i = input.read();
//            while (i != -1) {
//                words.add(String.valueOf((char) i));
//                i = input.read();
//            }
//            input.close();
//        } catch (Exception e) {
//            Log.e("MainActivity4", "Something went wrong", e);
//        }
//        return words.toArray(new String[0]);
//    }


}
