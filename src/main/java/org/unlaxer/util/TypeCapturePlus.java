package org.unlaxer.util;
import java.lang.reflect.*;
import java.util.*;

public abstract class TypeCapturePlus<T> {
    private final Type rootType;

    protected TypeCapturePlus() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType pt) {
            this.rootType = pt.getActualTypeArguments()[0];
        } else {
            throw new IllegalArgumentException("No type parameter found.");
        }
    }

    public Type getCapturedType() {
        return this.rootType;
    }

    // ① ツリー構造を構築
    public TypeNode getTypeTree() {
        return buildTree(rootType);
    }

    private TypeNode buildTree(Type type) {
        TypeNode node = new TypeNode(type);
        List<Type> children = getChildTypes(type);
        for (Type child : children) {
            node.addChild(buildTree(child));
        }
        return node;
    }

    private List<Type> getChildTypes(Type type) {
        List<Type> result = new ArrayList<>();
        if (type instanceof ParameterizedType pt) {
            result.addAll(List.of(pt.getActualTypeArguments()));
        } else if (type instanceof WildcardType wt) {
            result.addAll(List.of(wt.getUpperBounds()));
        } else if (type instanceof GenericArrayType gat) {
            result.add(gat.getGenericComponentType());
        }
        return result;
    }

    // ② 型ツリーの表示
    public void printTypeTree() {
        printTree(getTypeTree(), 0);
    }

    private void printTree(TypeNode node, int depth) {
        String indent = " ".repeat(depth * 4);
        System.out.println(indent + node.getSimpleName());
        for (TypeNode child : node.children) {
            printTree(child, depth + 1);
        }
    }

    // ③ 指定型が含まれているか
    public boolean containsType(Class<?> target) {
        return containsRecursive(getTypeTree(), target);
    }

    private boolean containsRecursive(TypeNode node, Class<?> target) {
        if (node.type instanceof Class<?> cls && cls.equals(target)) return true;
        if (node.type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw && raw.equals(target))
            return true;
        for (TypeNode child : node.children) {
            if (containsRecursive(child, target)) return true;
        }
        return false;
    }

    // ④ 最も深い型（葉）を取得
    public Set<Class<?>> getDeepestTypes() {
        Set<Class<?>> leaves = new HashSet<>();
        collectLeaves(getTypeTree(), leaves);
        return leaves;
    }

    private void collectLeaves(TypeNode node, Set<Class<?>> result) {
        if (node.children.isEmpty()) {
            if (node.type instanceof Class<?> cls) {
                result.add(cls);
            }
        } else {
            for (TypeNode child : node.children) {
                collectLeaves(child, result);
            }
        }
    }

    // ⑤ 指定型までのパスを取得
    public List<List<String>> findPathsTo(Class<?> target) {
        List<List<String>> paths = new ArrayList<>();
        findPaths(getTypeTree(), target, new ArrayList<>(), paths);
        return paths;
    }

    private void findPaths(TypeNode node, Class<?> target, List<String> current, List<List<String>> result) {
        current.add(node.getSimpleName());

        boolean matches = (node.type instanceof Class<?> cls && cls.equals(target)) ||
                          (node.type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw && raw.equals(target));

        if (matches) {
            result.add(new ArrayList<>(current));
        }

        for (TypeNode child : node.children) {
            findPaths(child, target, current, result);
        }

        current.remove(current.size() - 1);
    }
}
