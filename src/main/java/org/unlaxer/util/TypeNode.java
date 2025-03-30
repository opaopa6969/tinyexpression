package org.unlaxer.util;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class TypeNode {
    public final Type type;
    public final List<TypeNode> children = new ArrayList<>();

    public TypeNode(Type type) {
        this.type = type;
    }

    public void addChild(TypeNode child) {
        children.add(child);
    }

    public String getSimpleName() {
        String name = type.getTypeName();
        if (type instanceof Class<?>) {
            name = ((Class<?>) type).getSimpleName();
        }
        return name;
    }
}
