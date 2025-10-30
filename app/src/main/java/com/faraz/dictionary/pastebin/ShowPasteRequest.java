package com.faraz.dictionary.pastebin;

import java.util.HashMap;
import java.util.Map;

public class ShowPasteRequest implements Request {

    private final String pasteKey;

    public ShowPasteRequest(String pasteKey) {
        this.pasteKey = pasteKey;
    }

    @Override
    public Map<String, String> getParameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("api_paste_key", pasteKey);
        parameters.put("api_option", "show_paste");
        return parameters;
    }
}
