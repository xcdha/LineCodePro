package cn.lineai.data.service;

import cn.lineai.security.SimpleHttpClient;
import cn.lineai.security.UrlPolicy;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Downloads a SKILL.md file referenced by a public skills.sh page. */
public final class SkillsShInstaller {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_SKILL_CHARS = 512 * 1024;
    private static final Pattern RAW_SKILL_URL = Pattern.compile(
            "https://raw\\.githubusercontent\\.com/[^\\\"'<>\\s]+/SKILL\\.md(?:\\?[^\\\"'<>\\s]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_BLOB_URL = Pattern.compile(
            "https://github\\.com/([^/\\\"'<>\\s]+)/([^/\\\"'<>\\s]+)/blob/([^/\\\"'<>\\s]+)/(?:[^\\\"'<>\\s]+/)*SKILL\\.md",
            Pattern.CASE_INSENSITIVE);

    private SkillsShInstaller() {
    }

    public static String downloadSkill(String sourceUrl) throws Exception {
        URI source = requireSkillsShUrl(sourceUrl);
        String snapshot = downloadSnapshot(source);
        if (snapshot.length() > 0) {
            return snapshot;
        }
        String page = get(source.toString());
        if (isSkillMarkdown(page)) {
            return page;
        }
        String skillUrl = findSkillUrl(page);
        if (skillUrl.length() == 0) {
            throw new IllegalArgumentException("未能从 skills.sh 页面找到可下载的 SKILL.md。请粘贴具体 Skill 的详情页链接。");
        }
        String skill = get(skillUrl);
        if (!isSkillMarkdown(skill)) {
            throw new IllegalArgumentException("skills.sh 返回的内容不是有效的 SKILL.md。");
        }
        return skill;
    }

    private static URI requireSkillsShUrl(String value) {
        String url = UrlPolicy.requireHttpOrLocalCleartextUrl(value, "skills.sh URL");
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || !("skills.sh".equalsIgnoreCase(host) || host.endsWith(".skills.sh"))) {
                throw new IllegalArgumentException("请粘贴 skills.sh 的 Skill 详情页链接。");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("skills.sh URL 无效。", e);
        }
    }

    private static String downloadSnapshot(URI source) {
        String[] parts = source.getPath().split("/");
        if (parts.length < 4 || parts[1].length() == 0 || parts[2].length() == 0 || parts[3].length() == 0) {
            return "";
        }
        String url = "https://skills.sh/api/download/" + parts[1] + "/" + parts[2] + "/" + parts[3];
        try {
            JSONObject snapshot = new JSONObject(get(url));
            JSONArray files = snapshot.optJSONArray("files");
            if (files == null) {
                return "";
            }
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.optJSONObject(i);
                if (file != null && "SKILL.md".equalsIgnoreCase(file.optString("path"))) {
                    String content = file.optString("contents").trim();
                    return isSkillMarkdown(content) ? content : "";
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String get(String url) throws Exception {
        String value = SimpleHttpClient.get(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS,
                java.util.Collections.singletonMap("User-Agent", "LineCodePro Skills Installer"));
        if (value.length() > MAX_SKILL_CHARS) {
            throw new IllegalArgumentException("Skill 文件过大，最大支持 512 KB。");
        }
        return value;
    }

    private static String findSkillUrl(String html) {
        String decoded = html.replace("&amp;", "&").replace("\\/", "/");
        Matcher raw = RAW_SKILL_URL.matcher(decoded);
        if (raw.find()) {
            return raw.group();
        }
        Matcher blob = GITHUB_BLOB_URL.matcher(decoded);
        if (blob.find()) {
            return "https://raw.githubusercontent.com/" + blob.group(1) + "/" + blob.group(2)
                    + "/" + blob.group(3) + "/SKILL.md";
        }
        return "";
    }

    private static boolean isSkillMarkdown(String text) {
        String value = text == null ? "" : text.trim();
        return value.length() > 0
                && !value.startsWith("<!doctype html")
                && !value.startsWith("<html")
                && (value.startsWith("---") || value.startsWith("#") || value.contains("\n#"));
    }
}
