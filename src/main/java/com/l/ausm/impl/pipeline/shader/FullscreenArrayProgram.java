package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.pack.ShaderProgramDirectives;
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.List;

/** Runtime slot for an Iris indexed fullscreen program such as setup1 or begin2. */
public final class FullscreenArrayProgram {
    private final ProgramArrayId arrayId;
    private final int index;
    private final String name;
    private final RenderPass bindingPass;
    private final ShaderProgramDirectives directives;
    private final List<Attachment> drawBuffers;
    private ShaderProgram shaderProgram;
    private boolean enabled = true;

    public FullscreenArrayProgram(
            ProgramArrayId arrayId,
            int index,
            String name,
            RenderPass bindingPass,
            ShaderProgramDirectives directives
    ) {
        this.arrayId = arrayId;
        this.index = index;
        this.name = name;
        this.bindingPass = bindingPass;
        this.directives = directives == null ? ShaderProgramDirectives.empty(bindingPass.programId()) : directives;
        this.drawBuffers = this.directives.drawBuffers().isEmpty()
                ? defaultDrawBuffers(arrayId)
                : this.directives.drawBuffers();
    }

    private static List<Attachment> defaultDrawBuffers(ProgramArrayId arrayId) {
        return arrayId == ProgramArrayId.SHADOWCOMP ? List.of() : List.of(Attachment.COLOR);
    }

    public ProgramArrayId arrayId() {
        return arrayId;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    public RenderPass bindingPass() {
        return bindingPass;
    }

    public ShaderProgramDirectives directives() {
        return directives;
    }

    public List<Attachment> drawBuffers() {
        return drawBuffers;
    }

    public ShaderProgram shaderProgram() {
        return shaderProgram;
    }

    public void setShaderProgram(ShaderProgram shaderProgram) {
        this.shaderProgram = shaderProgram;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean hasProgram() {
        return enabled && shaderProgram != null;
    }

    public void delete() {
        if (shaderProgram != null) {
            shaderProgram.delete();
            shaderProgram = null;
        }
    }
}
