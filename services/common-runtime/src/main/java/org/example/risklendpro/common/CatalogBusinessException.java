package org.example.risklendpro.common;

public class CatalogBusinessException extends RuntimeException {

    private final int code;
    private final Object data;

    public CatalogBusinessException(int code, String message) {
        this(code, message, null);
    }

    public CatalogBusinessException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
