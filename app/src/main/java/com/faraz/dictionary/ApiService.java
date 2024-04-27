package com.faraz.dictionary;

import static android.widget.Toast.LENGTH_LONG;
import static com.android.volley.Request.Method.POST;
import static com.faraz.dictionary.MainActivity.MONGODB_API_KEY;
import static com.faraz.dictionary.MainActivity.MONGODB_URI;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_UPDATE_MANY;
import static com.faraz.dictionary.MainActivity2.getItem;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import android.content.Context;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class ApiService {

    private final RequestQueue requestQueue;
    private final Properties properties;
    private final android.content.Context context;

    public ApiService(RequestQueue requestQueue, Properties properties, Context baseContext) {
        this.requestQueue = requestQueue;
        this.properties = properties;
        this.context = baseContext;
    }

    public void updateData(String query, Consumer<Integer> consumer, String action, Consumer<String> exceptionConsumer) {
        System.out.println("Query " + query + ". Action " + MONGO_ACTION_UPDATE_MANY);
        RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
        JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI),
                action), requestFuture, requestFuture, query, loadProperty(MONGODB_API_KEY));
        requestQueue.add(request);
        try {
            JSONObject ans = requestFuture.get();
            int matchedCount = Integer.parseInt(ans.getString("matchedCount"));
            int modifiedCount = Integer.parseInt(ans.getString("modifiedCount"));
            consumer.accept(modifiedCount);
        } catch (Exception e) {
            e.printStackTrace();
            exceptionConsumer.accept("Mongo has gone belly up!");
        }
    }

    public List<String> executeQuery(String operation, String action, String extractionTarget, Consumer<String> exceptionConsumer) {
        System.out.println("Query " + operation + ". action: " + action);
        RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
        JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI), action),
                requestFuture, requestFuture, operation, loadProperty(MONGODB_API_KEY));
        requestQueue.add(request);
        try {
            JSONArray ans = requestFuture.get().getJSONArray("documents");
            return IntStream.range(0, ans.length()).mapToObj(i -> getItem(i, ans, extractionTarget)).collect(toList());
        }
        catch (Exception e) {
            e.printStackTrace();
            exceptionConsumer.accept("Mongo's gone belly up!");
        }
        throw new RuntimeException();
    }

    private String loadProperty(String property) {
        return this.properties.getProperty(property);
    }
}
