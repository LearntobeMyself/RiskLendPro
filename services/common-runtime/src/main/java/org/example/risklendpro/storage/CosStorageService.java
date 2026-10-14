package org.example.risklendpro.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.catalog.CatalogErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Service
@EnableConfigurationProperties(StorageProperties.class)
public class CosStorageService implements StorageService, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CosStorageService.class);
    private static final Duration ACCESS_EXPIRE = Duration.ofHours(1);
    /** 缓存比签名提前失效，避免把即将过期的 URL 再发给前端。 */
    private static final Duration ACCESS_CACHE_MARGIN = Duration.ofMinutes(10);

    private final StorageProperties properties;
    private final ConcurrentHashMap<String, CachedAccessUrl> accessUrlCache = new ConcurrentHashMap<>();
    private volatile COSClient sharedClient;

    public CosStorageService(StorageProperties properties) {
        this.properties = properties;
        if (!properties.credentialsReady()) {
            log.warn("Tencent COS credentials are empty. Fill COS_SECRET_ID/COS_SECRET_KEY/COS_BUCKET later; upload APIs will return STORAGE_ERROR until then.");
        }
    }

    @Override
    public PresignResult presignPut(String objectKey, String contentType, Duration expire) {
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    properties.getBucket(), objectKey, HttpMethodName.PUT);
            request.setExpiration(new Date(System.currentTimeMillis() + expire.toMillis()));
            request.setContentType(contentType);
            URL url = client().generatePresignedUrl(request);
            return PresignResult.put(url.toString(), contentType, expire);
        } catch (CatalogBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw storageError(e);
        }
    }

    @Override
    public void putObject(String objectKey, InputStream content, String contentType, long contentLength) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            if (contentType != null && !contentType.isBlank()) {
                metadata.setContentType(contentType);
            }
            metadata.setContentLength(contentLength);
            client().putObject(properties.getBucket(), objectKey, content, metadata);
        } catch (CatalogBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw storageError(e);
        }
    }

    @Override
    public String accessUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        if (!properties.isPrivateBucket()) {
            return publicUrl(objectKey);
        }
        long now = System.currentTimeMillis();
        CachedAccessUrl cached = accessUrlCache.get(objectKey);
        if (cached != null && cached.usableUntil > now) {
            return cached.url;
        }
        synchronized (accessUrlCache) {
            cached = accessUrlCache.get(objectKey);
            now = System.currentTimeMillis();
            if (cached != null && cached.usableUntil > now) {
                return cached.url;
            }
            try {
                long expireAt = now + ACCESS_EXPIRE.toMillis();
                GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                        properties.getBucket(), objectKey, HttpMethodName.GET);
                request.setExpiration(new Date(expireAt));
                String url = client().generatePresignedUrl(request).toString();
                accessUrlCache.put(objectKey, new CachedAccessUrl(url, expireAt - ACCESS_CACHE_MARGIN.toMillis()));
                return url;
            } catch (CatalogBusinessException e) {
                throw e;
            } catch (Exception e) {
                throw storageError(e);
            }
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client().getObjectMetadata(properties.getBucket(), objectKey);
            return true;
        } catch (CosServiceException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw storageError(e);
        } catch (CatalogBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw storageError(e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client().deleteObject(properties.getBucket(), objectKey);
            if (objectKey != null) {
                accessUrlCache.remove(objectKey);
            }
        } catch (CatalogBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw storageError(e);
        }
    }

    @Override
    public void destroy() {
        COSClient client = sharedClient;
        sharedClient = null;
        if (client != null) {
            client.shutdown();
        }
    }

    COSClient client() {
        requireConfigured();
        COSClient existing = sharedClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (sharedClient == null) {
                COSCredentials credentials = new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
                ClientConfig config = new ClientConfig(new Region(properties.getRegion()));
                sharedClient = new COSClient(credentials, config);
            }
            return sharedClient;
        }
    }

    private String publicUrl(String objectKey) {
        String cdn = properties.getCdnDomain();
        if (cdn != null && !cdn.isBlank()) {
            String domain = cdn.endsWith("/") ? cdn.substring(0, cdn.length() - 1) : cdn;
            return domain + "/" + objectKey;
        }
        return "https://" + properties.getBucket() + ".cos." + properties.getRegion() + ".myqcloud.com/" + objectKey;
    }

    private record CachedAccessUrl(String url, long usableUntil) {
    }

    void requireConfigured() {
        if (!properties.credentialsReady()) {
            throw new CatalogBusinessException(
                    CatalogErrorCodes.STORAGE_ERROR,
                    "对象存储未配置，请在环境变量或 config/local/application-local.yml 填写 COS_SECRET_ID、COS_SECRET_KEY、COS_BUCKET");
        }
    }

    private static CatalogBusinessException storageError(Exception e) {
        log.error("COS call failed: {}", e.getMessage(), e);
        return new CatalogBusinessException(CatalogErrorCodes.STORAGE_ERROR, "对象存储调用失败");
    }
}
