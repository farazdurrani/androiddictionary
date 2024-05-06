package com.faraz.dictionary;

import static com.android.volley.Request.Method.POST;
import static com.faraz.dictionary.MainActivity.MONGODB_API_KEY;
import static com.faraz.dictionary.MainActivity.MONGODB_URI;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.github.wnameless.json.flattener.JsonFlattener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

public class ApiService {

    private static final String activity = ApiService.class.getSimpleName();

    private final RequestQueue requestQueue;
    private final Properties properties;

    public ApiService(RequestQueue requestQueue, Properties properties) {
        this.requestQueue = requestQueue;
        this.properties = properties;
    }

    public Map<String, Object> upsert(String query, String action) throws ExecutionException, InterruptedException {
        JSONObject jsonObject = makeApiCall(query, action);
        return JsonFlattener.flattenAsMap(jsonObject.toString());
    }

    public List<String> executeQuery(String query, String action, String extractionTarget) throws ExecutionException, InterruptedException {
        JSONArray ans = makeApiCall(query, action).optJSONArray("documents");
        return IntStream.range(0, ofNullable(ans).map(JSONArray::length).orElse(0)).mapToObj(i -> getItem(i, ans, extractionTarget)).collect(toList());
    }

    @NonNull
    private JSONObject makeApiCall(String query, String action) throws ExecutionException, InterruptedException {
        Log.i(activity, "Query: " + query + ". Action: " + action);
        RequestFuture<JSONObject> requestFuture = RequestFuture.newFuture();
        JsonObjectRequest request = new MongoJsonObjectRequest(POST, format(loadProperty(MONGODB_URI), action),
                requestFuture, requestFuture, query, loadProperty(MONGODB_API_KEY));
        request.setShouldCache(false);
        requestQueue.add(request);
        return requestFuture.get();
    }

    private String getItem(int index, JSONArray ans, String extractionTarget) {
        try {
            return ans.getJSONObject(index).getString(extractionTarget);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String loadProperty(String property) {
        return this.properties.getProperty(property);
    }
}
