package com.faraz.dictionary.pastebin;

import com.tickaroo.tikxml.annotation.Element;
import com.tickaroo.tikxml.annotation.Xml;

import java.time.Instant;

@Xml(name = "user")
public class UserResponse {

    @Element(name = "user_name")
    private String name;

    @Element(name = "user_format_short")
    private String formatShort;

    @Element(name = "user_expiration")
    private String expiration;

    @Element(name = "user_avatar_url")
    private String avatarUrl;

    @Element(name = "user_private")
    private Integer privacy;

    @Element(name = "user_website")
    private String website;

    @Element(name = "user_email")
    private String email;

    @Element(name = "user_location")
    private String location;

    @Element(name = "user_account_type")
    private Integer accountType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFormatShort() {
        return formatShort;
    }

    public void setFormatShort(String formatShort) {
        this.formatShort = formatShort;
    }

    public String getExpiration() {
        return expiration;
    }

    public void setExpiration(String expiration) {
        this.expiration = expiration;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Integer getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Integer privacy) {
        this.privacy = privacy;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getAccountType() {
        return accountType;
    }

    public void setAccountType(Integer accountType) {
        this.accountType = accountType;
    }
}
