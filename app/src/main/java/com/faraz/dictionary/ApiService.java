package com.faraz.dictionary;

import static com.android.volley.Request.Method.POST;
import static com.faraz.dictionary.MainActivity.MONGODB_API_KEY;
import static com.faraz.dictionary.MainActivity.MONGODB_URI;
import static com.faraz.dictionary.MainActivity.MONGO_ACTION_UPDATE_MANY;
import static com.faraz.dictionary.MainActivity2.getItem;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class ApiService {

    private final RequestQueue requestQueue;
    private final Properties properties;

    public ApiService(RequestQueue requestQueue, Properties properties) {
        this.requestQueue = requestQueue;
        this.properties = properties;
    }

    public void updateData(String query, Consumer<Integer> successConsumer, String action, Consumer<String> exceptionConsumer) {
        System.out.println("Query " + query + ". Action " + MONGO_ACTION_UPDATE_MANY);
        RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
        JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI),
                action), requestFuture, requestFuture, query, loadProperty(MONGODB_API_KEY));
        requestQueue.add(request);
        try {
            JSONObject ans = requestFuture.get();
            int matchedCount = Integer.parseInt(ans.getString("matchedCount"));
            int modifiedCount = Integer.parseInt(ans.getString("modifiedCount"));
            successConsumer.accept(modifiedCount);
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
        } catch (Exception e) {
            e.printStackTrace();
            exceptionConsumer.accept("Mongo's gone belly up!");
        }
        return Collections.emptyList();
    }

    private String loadProperty(String property) {
        return this.properties.getProperty(property);
    }
}
