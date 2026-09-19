package com.netflix.tools.jfmt.internal.org.commonmark.internal;

import com.netflix.tools.jfmt.internal.org.commonmark.node.Block;
import com.netflix.tools.jfmt.internal.org.commonmark.node.Document;
import com.netflix.tools.jfmt.internal.org.commonmark.parser.SourceLine;
import com.netflix.tools.jfmt.internal.org.commonmark.parser.block.AbstractBlockParser;
import com.netflix.tools.jfmt.internal.org.commonmark.parser.block.BlockContinue;
import com.netflix.tools.jfmt.internal.org.commonmark.parser.block.ParserState;

public class DocumentBlockParser extends AbstractBlockParser {

    private final Document document = new Document();

    @Override
    public boolean isContainer() {
        return true;
    }

    @Override
    public boolean canContain(Block block) {
        return true;
    }

    @Override
    public Document getBlock() {
        return document;
    }

    @Override
    public BlockContinue tryContinue(ParserState state) {
        return BlockContinue.atIndex(state.getIndex());
    }

    @Override
    public void addLine(SourceLine line) {
    }

}
