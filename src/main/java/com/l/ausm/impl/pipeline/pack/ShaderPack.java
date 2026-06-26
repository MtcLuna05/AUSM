package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import java.io.IOException;
import java.io.InputStream;

public interface ShaderPack {
    /**
     * Gets the name of the shader pack.
     */
    String getName();

    /**
     * Retrieves an input stream for a resource inside the pack (e.g., "shaders/gbuffers_terrain.vsh").
     */
    InputStream getResourceAsStream(String path) throws IOException;

    /**
     * Checks if the pack contains a specific resource.
     */
    boolean hasResource(String path);

    /**
     * Closes the pack and releases any native resources (like open Zip files).
     */
    void close() throws IOException;
}
