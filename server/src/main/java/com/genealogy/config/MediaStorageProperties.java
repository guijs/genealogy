package com.genealogy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "media")
public class MediaStorageProperties {

    private String storage = "local";
    private String uploadSecret;
    private int uploadTokenExpiryMinutes = 15;
    private Local local = new Local();

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getUploadSecret() {
        return uploadSecret;
    }

    public void setUploadSecret(String uploadSecret) {
        this.uploadSecret = uploadSecret;
    }

    public int getUploadTokenExpiryMinutes() {
        return uploadTokenExpiryMinutes;
    }

    public void setUploadTokenExpiryMinutes(int uploadTokenExpiryMinutes) {
        this.uploadTokenExpiryMinutes = uploadTokenExpiryMinutes;
    }

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    public static class Local {
        private String root = "./data/media";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }
    }
}
