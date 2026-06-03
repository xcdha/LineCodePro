package cn.lineai.data.repository;

import cn.lineai.model.FileTreeNode;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class FileTreeRepository {
    private static final int MAX_CHILDREN_PER_DIR = 300;

    public FileTreeNode buildTree(String rootPath, Set<String> expandedPaths) {
        File root = new File(rootPath == null ? "" : rootPath);
        String displayName = root.getName().length() == 0 ? "home" : root.getName();
        if (!root.exists()) {
            return new FileTreeNode(displayName, root.getAbsolutePath(), true, true, Collections.emptyList());
        }
        return buildNode(root, displayName, true, expandedPaths == null ? Collections.emptySet() : expandedPaths);
    }

    public boolean isDirectory(String path) {
        return path != null && new File(path).isDirectory();
    }

    private FileTreeNode buildNode(File file, String fallbackName, boolean forceExpanded, Set<String> expandedPaths) {
        boolean directory = file.isDirectory();
        boolean expanded = directory && (forceExpanded || expandedPaths.contains(file.getAbsolutePath()));
        ArrayList<FileTreeNode> children = new ArrayList<>();
        if (expanded) {
            children.addAll(readChildren(file, expandedPaths));
        }
        String name = file.getName();
        if (name.length() == 0) {
            name = fallbackName == null || fallbackName.length() == 0 ? file.getAbsolutePath() : fallbackName;
        }
        return new FileTreeNode(name, file.getAbsolutePath(), directory, expanded, children);
    }

    private List<FileTreeNode> readChildren(File dir, Set<String> expandedPaths) {
        File[] rawChildren = dir.listFiles();
        if (rawChildren == null || rawChildren.length == 0) {
            return Collections.emptyList();
        }
        Arrays.sort(rawChildren, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (left.isDirectory() != right.isDirectory()) {
                    return left.isDirectory() ? -1 : 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        int limit = Math.min(rawChildren.length, MAX_CHILDREN_PER_DIR);
        ArrayList<FileTreeNode> children = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            children.add(buildNode(rawChildren[i], rawChildren[i].getName(), false, expandedPaths));
        }
        return children;
    }
}
