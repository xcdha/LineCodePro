package cn.lineai.tool.builtin;

import java.io.File;
import java.io.IOException;

public final class FileToolPathPolicy {
    private FileToolPathPolicy() {
    }

    public static File resolve(String homePath, String inputPath) throws IOException {
        if (homePath == null || homePath.trim().length() == 0) {
            throw new IOException("工作区路径为空");
        }
        String rawPath = inputPath == null ? "" : inputPath.trim();
        File root = new File(homePath).getCanonicalFile();
        File target = rawPath.length() == 0
                ? root
                : new File(rawPath).isAbsolute() ? new File(rawPath) : new File(root, rawPath);
        File canonical = target.getCanonicalFile();
        if (!isInside(root, canonical)) {
            throw new IOException("路径超出当前工作区: " + rawPath);
        }
        return canonical;
    }

    public static String displayPath(String homePath, File file) throws IOException {
        File root = new File(homePath).getCanonicalFile();
        File target = file.getCanonicalFile();
        String rootPath = root.getPath();
        String targetPath = target.getPath();
        if (targetPath.equals(rootPath)) {
            return ".";
        }
        if (targetPath.startsWith(rootPath + File.separator)) {
            return targetPath.substring(rootPath.length() + 1);
        }
        return targetPath;
    }

    private static boolean isInside(File root, File target) {
        String rootPath = root.getPath();
        String targetPath = target.getPath();
        return targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator);
    }
}
