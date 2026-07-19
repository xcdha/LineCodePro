package cn.lineai.tool.builtin;

import cn.lineai.model.WebSearchConfig;
import java.util.List;
import org.json.JSONObject;

public interface WebSearchProvider {
    String providerId();
    SearchRequest buildRequest(WebSearchConfig config, String query, int limit) throws Exception;
    List<SearchResultItem> normalizeResults(JSONObject response);

    /**
     * 直接解析原始响应体。
     *
     * 默认返回 null，表示走 {@link #normalizeResults(JSONObject)} 的 JSON 路径。
     * 当 provider 返回非 JSON（如 RSS/XML）时，重写此方法返回非 null 结果，
     * 调用方将跳过 JSON 解析直接使用本方法返回值。
     */
    default List<SearchResultItem> parseRawResponse(String body) throws Exception {
        return null;
    }
}
