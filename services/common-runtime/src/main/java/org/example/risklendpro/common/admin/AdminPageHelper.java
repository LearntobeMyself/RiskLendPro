package org.example.risklendpro.common.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdminPageHelper {

    private AdminPageHelper() {
    }

    public static <T> Map<String, Object> toListPage(Page<T> page) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", page.getRecords());
        data.put("total", page.getTotal());
        return data;
    }

    public static <T> Map<String, Object> toListPage(List<T> list, long total) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }
}
