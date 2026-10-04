package org.example.risklendpro.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String provider = "cos";
    private String bucket = "";
    private String region = "ap-guangzhou";
    private String secretId = "";
    private String secretKey = "";
    private String appId = "";
    private String cdnDomain = "";
    private boolean privateBucket = true;
    private int presignExpireMinutes = 15;

    public boolean credentialsReady() {
        return notBlank(secretId) && notBlank(secretKey) && notBlank(bucket) && notBlank(region);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getCdnDomain() {
        return cdnDomain;
    }

    public void setCdnDomain(String cdnDomain) {
        this.cdnDomain = cdnDomain;
    }

    public boolean isPrivateBucket() {
        return privateBucket;
    }

    public void setPrivateBucket(boolean privateBucket) {
        this.privateBucket = privateBucket;
    }

    public int getPresignExpireMinutes() {
        return presignExpireMinutes;
    }

    public void setPresignExpireMinutes(int presignExpireMinutes) {
        this.presignExpireMinutes = presignExpireMinutes;
    }
}
