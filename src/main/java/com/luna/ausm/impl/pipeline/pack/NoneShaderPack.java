package com.luna.ausm.impl.pipeline.pack;

import java.io.InputStream;

public final class NoneShaderPack implements ShaderPack {

    public static final NoneShaderPack INSTANCE = new NoneShaderPack();

    private NoneShaderPack() {
    }

    @Override
    public String getName() {
        return "(internal)";
    }

    @Override
    public InputStream getResourceAsStream(String path) {
        return null;
    }

    @Override
    public boolean hasResource(String path) {
        return false;
    }

    @Override
    public void close() {
        // No-op
    }
}
