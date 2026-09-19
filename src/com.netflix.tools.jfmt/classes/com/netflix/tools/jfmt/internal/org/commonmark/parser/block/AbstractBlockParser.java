package com.netflix.tools.jfmt.internal.org.commonmark.parser.block;

import com.netflix.tools.jfmt.internal.org.commonmark.node.Block;
import com.netflix.tools.jfmt.internal.org.commonmark.node.DefinitionMap;
import com.netflix.tools.jfmt.internal.org.commonmark.node.SourceSpan;
import com.netflix.tools.jfmt.internal.org.commonmark.parser.InlineParser;
import com.netflix.tools.jfmt.internal.org.commonmark.parser.SourceLine;

import java.util.List;

public abstract class AbstractBlockParser implements BlockParser {

    @Override
    public boolean isContainer() {
        return false;
    }

    @Override
    public boolean canHaveLazyContinuationLines() {
        return false;
    }

    @Override
    public boolean canContain(Block childBlock) {
        return false;
    }

    @Override
    public void addLine(SourceLine line) {
    }

    @Override
    public void addSourceSpan(SourceSpan sourceSpan) {
        getBlock().addSourceSpan(sourceSpan);
    }

    @Override
    public List<DefinitionMap<?>> getDefinitions() {
        return List.of();
    }

    @Override
    public void closeBlock() {
    }

    @Override
    public void parseInlines(InlineParser inlineParser) {
    }

}
