package cn.lineai.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.R;
import cn.lineai.ai.prompt.SkillPromptBuilder;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.data.service.SkillFileManager;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.SkillRecord;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Skill 仓库，负责 Skill 的 CRUD 与业务编排。
 * 文件系统操作委托给 {@link SkillFileManager}，提示词拼装委托给 {@link SkillPromptBuilder}。
 */
public final class SkillRepository extends BaseRepository {

    private final Context context;
    private final SkillFileManager fileManager;
    private final SkillPromptBuilder promptBuilder;
    private final AgentExtensionRepository agentRepository;
    private final McpExtensionRepository mcpRepository;

    public SkillRepository(Context context, AgentExtensionRepository agentRepository, McpExtensionRepository mcpRepository) {
        super(LineCodeDatabase.getInstance(context.getApplicationContext()));
        this.context = context.getApplicationContext();
        this.fileManager = new SkillFileManager(context);
        this.promptBuilder = new SkillPromptBuilder(fileManager);
        this.agentRepository = agentRepository;
        this.mcpRepository = mcpRepository;
    }

    public synchronized List<SkillRecord> getSkills(String homePath) {
        fileManager.ensureSkillRoots(homePath);
        upsertDiscoveredSkills(fileManager.discoverSkills(homePath));
        return readSkills();
    }

    public synchronized SkillRecord createSkill(String homePath, String location, String name, String description, String content) {
        fileManager.ensureSkillRoots(homePath);
        String safeName = safe(name).trim();
        if (safeName.length() == 0) {
            safeName = "linecode-skill-" + System.currentTimeMillis();
        }
        String normalizedLocation = SkillRecord.normalizeLocation(location);
        File root = fileManager.localSkillRoot(homePath, normalizedLocation);
        File skillDir = fileManager.uniqueChild(root, fileManager.sanitizeFileName(safeName));
        if (!skillDir.exists()) {
            skillDir.mkdirs();
        }
        File skillFile = new File(skillDir, "SKILL.md");
        fileManager.writeUtf8(skillFile, fileManager.buildSkillMarkdown(safeName, description, content));
        SkillRecord record = fileManager.parseSkill(skillDir, skillFile, normalizedLocation);
        upsertDiscoveredSkills(Collections.singletonList(record));
        return record;
    }

    public synchronized SkillRecord installSkill(String homePath, String location, String sourcePath, String name) throws Exception {
        fileManager.ensureSkillRoots(homePath);
        File source = new File(safe(sourcePath).trim()).getCanonicalFile();
        if (!source.exists()) {
            throw new IllegalArgumentException(context.getString(R.string.skill_source_not_found, sourcePath));
        }
        String normalizedLocation = SkillRecord.normalizeLocation(location);
        File root = fileManager.localSkillRoot(homePath, normalizedLocation);
        String baseName = fileManager.sanitizeFileName(safe(name).trim().length() == 0 ? fileManager.stripExtension(source.getName()) : name);
        File target = fileManager.uniqueChild(root, baseName);
        if (source.isDirectory()) {
            fileManager.copyDirectory(source, target);
        } else if (source.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileManager.unzip(source, target);
        } else if ("skill.md".equalsIgnoreCase(source.getName())) {
            target.mkdirs();
            fileManager.copyFile(source, new File(target, "SKILL.md"));
        } else {
            throw new IllegalArgumentException("仅支持目录、SKILL.md 或 .zip 技能包。");
        }
        File skillMd = fileManager.findSkillMd(target, 0);
        if (skillMd == null) {
            throw new IllegalArgumentException("安装完成，但没有找到 SKILL.md。");
        }
        SkillRecord record = fileManager.parseSkill(skillMd.getParentFile(), skillMd, normalizedLocation);
        upsertDiscoveredSkills(Collections.singletonList(record));
        return record;
    }

    public synchronized SkillRecord installSkillFromUri(String homePath, String location, String uri, String displayName) throws Exception {
        fileManager.ensureSkillRoots(homePath);
        String fileName = fileManager.skillImportFileName(displayName);
        File tempRoot = new File(fileManager.getWorkspacePaths().getLinecodeRoot(), "tmp/skills");
        File tempDir = fileManager.uniqueChild(tempRoot, fileManager.stripExtension(fileName));
        File tempFile = new File(tempDir, fileName.toLowerCase(Locale.ROOT).endsWith(".zip") ? fileName : "SKILL.md");
        fileManager.copyUriToFile(uri, tempFile);
        try {
            return installSkill(homePath, location, tempFile.getAbsolutePath(), fileManager.stripExtension(fileName));
        } finally {
            fileManager.deleteRecursive(tempDir);
        }
    }

    public synchronized void setSkillEnabled(String id, boolean enabled) {
        updateEnabled("skills", id, enabled);
    }

    public synchronized void deleteSkill(String id) {
        SkillRecord target = findSkill(id);
        database.getWritableDatabase().delete("skills", "id = ?", new String[] {safe(id)});
        if (target != null && !SkillRecord.LOCATION_SSH.equals(target.getLocation()) && target.getRootPath().length() > 0) {
            fileManager.deleteRecursive(new File(target.getRootPath()));
        }
    }

    public synchronized String buildExtensionPrompt(String homePath) {
        List<ExtensionAgentConfig> agents = agentRepository.getAgentExtensions();
        List<ExtensionMcpConfig> mcps = mcpRepository.getMcpExtensions();
        List<SkillRecord> skills = getSkills(homePath);
        return promptBuilder.buildExtensionPrompt(agents, mcps, skills);
    }

    public ArrayList<String> skillWriteRoots(String homePath) {
        fileManager.ensureSkillRoots(homePath);
        return fileManager.skillWriteRoots(homePath);
    }

    // ── 数据库操作 ──

    private void upsertDiscoveredSkills(List<SkillRecord> discovered) {
        if (discovered == null || discovered.isEmpty()) {
            return;
        }
        HashMap<String, Boolean> existingEnabled = existingSkillEnabled();
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            for (SkillRecord skill : discovered) {
                ContentValues values = skillValues(skill, existingEnabled.containsKey(skill.getId()) ? existingEnabled.get(skill.getId()) : skill.isEnabled());
                db.insertWithOnConflict("skills", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private List<SkillRecord> readSkills() {
        ArrayList<SkillRecord> skills = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, name, scope, path, description, enabled, updated_at, raw_json FROM skills ORDER BY updated_at DESC",
                new String[0]
        );
        try {
            while (cursor.moveToNext()) {
                String raw = value(cursor, "raw_json");
                JSONObject json = parseJson(raw);
                String rootPath = value(cursor, "path");
                String skillMdPath = json.optString("skillMdPath", rootPath.length() == 0 ? "" : new File(rootPath, "SKILL.md").getAbsolutePath());
                long discoveredAt = json.optLong("discoveredAt", longValue(cursor, "updated_at"));
                skills.add(new SkillRecord(
                        value(cursor, "id"),
                        value(cursor, "name"),
                        value(cursor, "description"),
                        rootPath,
                        skillMdPath,
                        value(cursor, "scope"),
                        intValue(cursor, "enabled") != 0,
                        discoveredAt,
                        longValue(cursor, "updated_at")
                ));
            }
        } finally {
            cursor.close();
        }
        Collections.sort(skills, new Comparator<SkillRecord>() {
            @Override
            public int compare(SkillRecord left, SkillRecord right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return skills;
    }

    private SkillRecord findSkill(String id) {
        for (SkillRecord skill : readSkills()) {
            if (skill.getId().equals(id)) {
                return skill;
            }
        }
        return null;
    }

    private ContentValues skillValues(SkillRecord skill, boolean enabled) {
        JSONObject raw = new JSONObject();
        try {
            raw.put("skillMdPath", skill.getSkillMdPath());
            raw.put("rootPath", skill.getRootPath());
            raw.put("location", skill.getLocation());
            raw.put("discoveredAt", skill.getDiscoveredAt());
        } catch (Exception ignored) {
        }
        ContentValues values = new ContentValues();
        values.put("id", skill.getId());
        values.put("name", skill.getName());
        values.put("scope", skill.getLocation());
        values.put("path", skill.getRootPath());
        values.put("description", skill.getDescription());
        values.put("enabled", enabled ? 1 : 0);
        values.put("updated_at", skill.getUpdatedAt());
        values.put("raw_json", raw.toString());
        return values;
    }

    private HashMap<String, Boolean> existingSkillEnabled() {
        HashMap<String, Boolean> values = new HashMap<>();
        Cursor cursor = database.getReadableDatabase().rawQuery("SELECT id, enabled FROM skills", new String[0]);
        try {
            while (cursor.moveToNext()) {
                values.put(value(cursor, "id"), intValue(cursor, "enabled") != 0);
            }
        } finally {
            cursor.close();
        }
        return values;
    }

    private JSONObject parseJson(String raw) {
        try {
            return new JSONObject(safe(raw).length() == 0 ? "{}" : raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
