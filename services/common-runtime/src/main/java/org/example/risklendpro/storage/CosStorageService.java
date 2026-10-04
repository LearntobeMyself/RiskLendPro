package org.example.risklendpro.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.region.Region;
import org.example.risklendpro.common.CatalogBusinessException;
import org.example.risklendpro.loan.catalog.CatalogErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.util.Date;

@Service
@EnableConfigurationProperties(StorageProperties.class)
public class CosStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(CosStorageService.class);
    private static final Duration ACCESS_EXPIRE = Duration.ofHours(1);

    private final StorageProperties properties;

    public CosStorageService(StorageProperties properties) {
        this.properties = properties;
        if (!properties.credentialsReady()) {
            log.warn("Tencent COS credentials are empty. Fill COS_SECRET_ID/COS_SECRET_KEY/COS_BUCKET later; upload APIs will return STORAGE_ERROR until then.");
        }
    }

    @Override
    public PresignResult presignPut(String objectKey, String contentType, Duration expire) {
        COSClient client = client();
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    properties.getBucket(), objectKey, HttpMethodName.PUT);
            request.setExpiration(new Date(System.currentTimeMillis() + expire.toMillis()));
            request.setContentType(contentType);
            URL url = client.generatePresignedUrl(request);
            return PresignResult.put(url.toString(), contentType, expire);
        } catch (Exception e) {
            throw storageError(e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public String accessUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        if (!properties.isPrivateBucket()) {
            String cdn = properties.getCdnDomain();
            if (cdn != null && !cdn.isBlank()) {
                String domain = cdn.endsWith("/") ? cdn.substring(0, cdn.length() - 1) : cdn;
                return domain + "/" + objectKey;
            }
            return "https://" + properties.getBucket() + ".cos." + properties.getRegion() + ".myqcloud.com/" + objectKey;
        }
        COSClient client = client();
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    properties.getBucket(), objectKey, HttpMethodName.GET);
            request.setExpiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRE.toMillis()));
            return client.generatePresignedUrl(request).toString();
        } catch (Exception e) {
            throw storageError(e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public boolean exists(String objectKey) {
        COSClient client = client();
        try {
            client.getObjectMetadata(properties.getBucket(), objectKey);
            return true;
        } catch (CosServiceException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw storageError(e);
        } catch (Exception e) {
            throw storageError(e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void delete(String objectKey) {
        COSClient client = client();
        try {
            client.deleteObject(properties.getBucket(), objectKey);
        } catch (Exception e) {
            throw storageError(e);
        } finally {
            client.shutdown();
        }
    }

    COSClient client() {
        requireConfigured();
        COSCredentials credentials = new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
        ClientConfig config = new ClientConfig(new Region(properties.getRegion()));
        return new COSClient(credentials, config);
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
