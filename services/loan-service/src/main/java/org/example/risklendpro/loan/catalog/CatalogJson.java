package org.example.risklendpro.loan.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.common.CatalogBusinessException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class CatalogJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CatalogJson() {
    }

    public static String writeList(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "JSON序列化失败");
        }
    }

    public static String writeMap(Map<String, Object> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? Map.of() : values);
        } catch (Exception e) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "JSON序列化失败");
        }
    }

    public static List<String> readStringList(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> list = MAPPER.readValue(json, new TypeReference<>() {
            });
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception e) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "JSON解析失败");
        }
    }

    public static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json, new TypeReference<>() {
            });
            return map == null ? Map.of() : map;
        } catch (Exception e) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "JSON解析失败");
        }
    }

    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new CatalogBusinessException(CatalogErrorCodes.PARAM_INVALID, "JSON解析失败");
        }
    }

    public static List<String> unique(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .toList()));
    }

    public static List<Long> uniqueLong(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(values.stream().filter(v -> v != null).toList()));
    }
}
