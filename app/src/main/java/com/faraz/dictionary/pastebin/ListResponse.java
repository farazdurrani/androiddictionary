package com.faraz.dictionary.pastebin;

import java.util.List;

import com.tickaroo.tikxml.annotation.Element;
import com.tickaroo.tikxml.annotation.Xml;

@Xml(name = "response")
public class ListResponse {

    @Element(name = "paste")
    private List<ListResponseItem> items;

    public List<ListResponseItem> getItems() {
        return items;
    }

    public void setItems(List<ListResponseItem> items) {
        this.items = items;
    }
}
