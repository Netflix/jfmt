package com.netflix.tools.jfmt.internal.org.commonmark.internal.inline;

import com.netflix.tools.jfmt.internal.org.commonmark.node.Node;
import com.netflix.tools.jfmt.internal.org.commonmark.parser.beta.LinkResult;
import com.netflix.tools.jfmt.internal.org.commonmark.parser.beta.Position;

public class LinkResultImpl implements LinkResult {
    @Override
    public LinkResult includeMarker() {
        includeMarker = true;
        return this;
    }

    public enum Type {
        WRAP,
        REPLACE
    }

    private final Type type;
    private final Node node;
    private final Position position;

    private boolean includeMarker = false;

    public LinkResultImpl(Type type, Node node, Position position) {
        this.type = type;
        this.node = node;
        this.position = position;
    }

    public Type getType() {
        return type;
    }

    public Node getNode() {
        return node;
    }

    public Position getPosition() {
        return position;
    }

    public boolean isIncludeMarker() {
        return includeMarker;
    }
}
