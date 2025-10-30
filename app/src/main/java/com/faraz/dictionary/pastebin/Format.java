
package com.faraz.dictionary.pastebin;

public enum Format {

    NONE("", "None"),
    JSON("json", "JSON"),
    GETTEXT("gettext", "GetText");

    final String code;
    final String name;

    Format(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    static Format find(final String code) {
        for (Format value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return NONE;
    }
}
