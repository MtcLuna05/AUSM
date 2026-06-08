package com.l.ausm.impl.pipeline.vertex;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;

/**
 * Registry for our extended vertex formats containing shader-specific attributes.
 */
public class ExtendedVertexFormats {

    public static VertexFormat PIPELINE_BLOCK;
    public static VertexFormat PIPELINE_ENTITY;
    public static final int MC_ENTITY_ATTRIBUTE = 11;
    public static final int MC_MID_TEX_COORD_ATTRIBUTE = 12;
    public static final int AT_TANGENT_ATTRIBUTE = 13;
    public static final int AT_MID_BLOCK_ATTRIBUTE = 14;
    private static int maxVertexAttribs = -1;
    public static int PIPELINE_BLOCK_NORMAL_OFFSET;
    public static int PIPELINE_BLOCK_MC_ENTITY_OFFSET;
    public static int PIPELINE_BLOCK_MID_TEX_COORD_OFFSET;
    public static int PIPELINE_BLOCK_TANGENT_OFFSET;
    public static int PIPELINE_BLOCK_MID_BLOCK_OFFSET;
    public static int PIPELINE_ENTITY_NORMAL_OFFSET;
    public static int PIPELINE_ENTITY_MC_ENTITY_OFFSET;
    public static int PIPELINE_ENTITY_MID_TEX_COORD_OFFSET;
    public static int PIPELINE_ENTITY_TANGENT_OFFSET;

    // These elements mirror Iris' terrain payload order:
    // mc_Entity, mc_midTexCoord, at_tangent, at_midBlock.
    public static final VertexFormatElement MC_ENTITY = new VertexFormatElement(0, VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.PADDING, 4);
    
    // mc_midTexCoord: 2 floats = 8 bytes
    public static final VertexFormatElement MC_MID_TEX_COORD = new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.PADDING, 2);
    
    public static final VertexFormatElement AT_TANGENT = new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.PADDING, 4);

    // at_midBlock: 3 byte local midpoint + 1 byte emission slot. The 1.12
    // backport currently writes zeroes until block-local coordinates are plumbed
    // through the chunk rebuild path.
    public static final VertexFormatElement AT_MID_BLOCK = new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.PADDING, 4);

    public static void initialize() {
        // Create custom block format
        PIPELINE_BLOCK = new VertexFormat();
        PIPELINE_BLOCK.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.POSITION, 3));
        PIPELINE_BLOCK.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.UBYTE, VertexFormatElement.EnumUsage.COLOR, 4));
        PIPELINE_BLOCK.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.UV, 2));
        PIPELINE_BLOCK.addElement(new VertexFormatElement(1, VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.UV, 2));
        PIPELINE_BLOCK.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.NORMAL, 3));
        PIPELINE_BLOCK.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.PADDING, 1));
        PIPELINE_BLOCK.addElement(MC_ENTITY);
        PIPELINE_BLOCK.addElement(MC_MID_TEX_COORD);
        PIPELINE_BLOCK.addElement(AT_TANGENT);
        PIPELINE_BLOCK.addElement(AT_MID_BLOCK);
        PIPELINE_BLOCK_NORMAL_OFFSET = PIPELINE_BLOCK.getOffset(4);
        PIPELINE_BLOCK_MC_ENTITY_OFFSET = PIPELINE_BLOCK.getOffset(6);
        PIPELINE_BLOCK_MID_TEX_COORD_OFFSET = PIPELINE_BLOCK.getOffset(7);
        PIPELINE_BLOCK_TANGENT_OFFSET = PIPELINE_BLOCK.getOffset(8);
        PIPELINE_BLOCK_MID_BLOCK_OFFSET = PIPELINE_BLOCK.getOffset(9);

        // Create custom entity format
        PIPELINE_ENTITY = new VertexFormat();
        PIPELINE_ENTITY.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.POSITION, 3));
        PIPELINE_ENTITY.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.UBYTE, VertexFormatElement.EnumUsage.COLOR, 4));
        PIPELINE_ENTITY.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.UV, 2));
        PIPELINE_ENTITY.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.NORMAL, 3));
        PIPELINE_ENTITY.addElement(new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.PADDING, 1));
        PIPELINE_ENTITY.addElement(MC_ENTITY);
        PIPELINE_ENTITY.addElement(MC_MID_TEX_COORD);
        PIPELINE_ENTITY.addElement(AT_TANGENT);
        PIPELINE_ENTITY_NORMAL_OFFSET = PIPELINE_ENTITY.getOffset(3);
        PIPELINE_ENTITY_MC_ENTITY_OFFSET = PIPELINE_ENTITY.getOffset(5);
        PIPELINE_ENTITY_MID_TEX_COORD_OFFSET = PIPELINE_ENTITY.getOffset(6);
        PIPELINE_ENTITY_TANGENT_OFFSET = PIPELINE_ENTITY.getOffset(7);
    }

    public static boolean isPipelineBlock(VertexFormat format) {
        return format != null
                && PIPELINE_BLOCK != null
                && format.getElementCount() == PIPELINE_BLOCK.getElementCount()
                && format.getSize() == PIPELINE_BLOCK.getSize()
                && format.getOffset(format.getElementCount() - 1) == PIPELINE_BLOCK_MID_BLOCK_OFFSET;
    }

    public static boolean isPipelineEntity(VertexFormat format) {
        return format != null
                && PIPELINE_ENTITY != null
                && format.getElementCount() == PIPELINE_ENTITY.getElementCount()
                && format.getSize() == PIPELINE_ENTITY.getSize()
                && format.getOffset(format.getElementCount() - 1) == PIPELINE_ENTITY_TANGENT_OFFSET;
    }

    public static void enableAttribute(int index) {
        if (isValidAttribute(index)) {
            GL20.glEnableVertexAttribArray(index);
        }
    }

    public static void disableAttribute(int index) {
        if (isValidAttribute(index)) {
            GL20.glDisableVertexAttribArray(index);
        }
    }

    public static void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        if (isValidAttribute(index)) {
            GL20.glVertexAttribPointer(index, size, type, normalized, stride, offset);
        }
    }

    public static void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, ByteBuffer buffer) {
        if (isValidAttribute(index)) {
            GL20.glVertexAttribPointer(index, size, type, normalized, stride, buffer);
        }
    }

    private static boolean isValidAttribute(int index) {
        return index >= 0 && index < maxVertexAttribs();
    }

    private static int maxVertexAttribs() {
        if (maxVertexAttribs < 0) {
            maxVertexAttribs = Math.max(0, GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS));
        }
        return maxVertexAttribs;
    }
}
