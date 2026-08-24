package com.luna.ausm.impl.pipeline.shader;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.pack.ShaderProgramDirectives;
import com.luna.ausm.api.pipeline.shader.ProgramId;
import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import java.util.List;
import java.util.Map;

/**
 * Runtime slot for an OptiFine-style program.
 * Keeps program metadata separate from the GL program object.
 */
public final class PipelineProgram {

    private final RenderPass pass;
    private final ShaderKey shaderKey;
    private final ShaderProgramDirectives directives;
    private List<Attachment> drawBuffers;
    private ShaderProgram shaderProgram;
    private boolean enabled = true;

    public PipelineProgram(RenderPass pass, ShaderProgramDirectives directives) {
        this.pass = pass;
        this.shaderKey = ShaderKey.fromRenderPass(pass);
        this.directives = directives == null ? ShaderProgramDirectives.empty(pass.programId()) : directives;
        this.drawBuffers = this.directives.drawBuffers().isEmpty()
                ? defaultDrawBuffers(pass.stage())
                : this.directives.drawBuffers();
    }

    private static List<Attachment> defaultDrawBuffers(ProgramStage stage) {
        return switch (stage) {
            case PREPARE, GBUFFERS, DEFERRED, COMPOSITE -> List.of(Attachment.COLOR);
            case SHADOW -> List.of(Attachment.COLOR);
            case FINAL, NONE -> List.of();
        };
    }

    public RenderPass pass() {
        return pass;
    }

    public ShaderKey shaderKey() {
        return shaderKey;
    }

    public ShaderProgramDirectives directives() {
        return directives;
    }

    public ProgramStage stage() {
        return pass.stage();
    }

    public List<Attachment> drawBuffers() {
        return drawBuffers;
    }

    public List<Attachment> effectiveDrawBuffers(Map<RenderPass, PipelineProgram> programs) {
        if (!enabled || shaderProgram != null || pass.programId().fallback() == null) {
            return drawBuffers;
        }

        RenderPass fallback = RenderPass.fromProgramId(pass.programId().fallback());
        while (fallback != null) {
            PipelineProgram fallbackProgram = programs.get(fallback);
            if (fallbackProgram == null || !fallbackProgram.enabled()) {
                return drawBuffers;
            }
            if (fallbackProgram.shaderProgram() != null) {
                return fallbackProgram.drawBuffers();
            }
            ProgramId nextFallback = fallback.programId().fallback();
            fallback = nextFallback == null ? null : RenderPass.fromProgramId(nextFallback);
        }

        return drawBuffers;
    }

    public void setDrawBuffers(List<Attachment> drawBuffers) {
        this.drawBuffers = List.copyOf(drawBuffers);
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

    public boolean hasOwnProgram() {
        return enabled && shaderProgram != null;
    }

    public ShaderProgram effectiveProgram(Map<RenderPass, PipelineProgram> programs) {
        if (!enabled) {
            return null;
        }
        if (shaderProgram != null) {
            return shaderProgram;
        }

        RenderPass fallback = pass.programId().fallback() == null ? null : RenderPass.fromProgramId(pass.programId().fallback());
        while (fallback != null) {
            PipelineProgram fallbackProgram = programs.get(fallback);
            if (fallbackProgram == null || !fallbackProgram.enabled()) {
                return null;
            }
            if (fallbackProgram.shaderProgram() != null) {
                return fallbackProgram.shaderProgram();
            }
            ProgramId nextFallback = fallback.programId().fallback();
            fallback = nextFallback == null ? null : RenderPass.fromProgramId(nextFallback);
        }

        return null;
    }

    public void delete() {
        if (shaderProgram != null) {
            shaderProgram.delete();
            shaderProgram = null;
        }
    }
}
