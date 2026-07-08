package com.l.ausm.impl.pipeline.vertex;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
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
    public static final int DH_MATERIAL_ID_ATTRIBUTE = 15;
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

    // at_midBlock: 3 byte local midpoint + 1 byte emission slot.
    public static final VertexFormatElement AT_MID_BLOCK = new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.PADDING, 4);

    public static void initialize() {
        // Create custom block format
        PIPELINE_BLOCK = new VertexFormat();
        addElement(PIPELINE_BLOCK, new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.POSITION, 3));
        addElement(PIPELINE_BLOCK, new VertexFormatElement(0, VertexFormatElement.EnumType.UBYTE, VertexFormatElement.EnumUsage.COLOR, 4));
        addElement(PIPELINE_BLOCK, new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.UV, 2));
        addElement(PIPELINE_BLOCK, new VertexFormatElement(1, VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.UV, 2));
        addElement(PIPELINE_BLOCK, new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.NORMAL, 3));
        addElement(PIPELINE_BLOCK, new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.PADDING, 1));
        addElement(PIPELINE_BLOCK, MC_ENTITY);
        addElement(PIPELINE_BLOCK, MC_MID_TEX_COORD);
        addElement(PIPELINE_BLOCK, AT_TANGENT);
        addElement(PIPELINE_BLOCK, AT_MID_BLOCK);
        PIPELINE_BLOCK_NORMAL_OFFSET = getOffset(PIPELINE_BLOCK, 4);
        PIPELINE_BLOCK_MC_ENTITY_OFFSET = getOffset(PIPELINE_BLOCK, 6);
        PIPELINE_BLOCK_MID_TEX_COORD_OFFSET = getOffset(PIPELINE_BLOCK, 7);
        PIPELINE_BLOCK_TANGENT_OFFSET = getOffset(PIPELINE_BLOCK, 8);
        PIPELINE_BLOCK_MID_BLOCK_OFFSET = getOffset(PIPELINE_BLOCK, 9);

        // Create custom entity format
        PIPELINE_ENTITY = new VertexFormat();
        addElement(PIPELINE_ENTITY, new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.POSITION, 3));
        addElement(PIPELINE_ENTITY, new VertexFormatElement(0, VertexFormatElement.EnumType.UBYTE, VertexFormatElement.EnumUsage.COLOR, 4));
        addElement(PIPELINE_ENTITY, new VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.UV, 2));
        addElement(PIPELINE_ENTITY, new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.NORMAL, 3));
        addElement(PIPELINE_ENTITY, new VertexFormatElement(0, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.PADDING, 1));
        addElement(PIPELINE_ENTITY, MC_ENTITY);
        addElement(PIPELINE_ENTITY, MC_MID_TEX_COORD);
        addElement(PIPELINE_ENTITY, AT_TANGENT);
        PIPELINE_ENTITY_NORMAL_OFFSET = getOffset(PIPELINE_ENTITY, 3);
        PIPELINE_ENTITY_MC_ENTITY_OFFSET = getOffset(PIPELINE_ENTITY, 5);
        PIPELINE_ENTITY_MID_TEX_COORD_OFFSET = getOffset(PIPELINE_ENTITY, 6);
        PIPELINE_ENTITY_TANGENT_OFFSET = getOffset(PIPELINE_ENTITY, 7);
    }

    public static boolean isPipelineBlock(VertexFormat format) {
        return format != null
                && PIPELINE_BLOCK != null
                && getElementCount(format) == getElementCount(PIPELINE_BLOCK)
                && getSize(format) == getSize(PIPELINE_BLOCK)
                && getOffset(format, getElementCount(format) - 1) == PIPELINE_BLOCK_MID_BLOCK_OFFSET;
    }

    public static boolean isPipelineEntity(VertexFormat format) {
        return format != null
                && PIPELINE_ENTITY != null
                && getElementCount(format) == getElementCount(PIPELINE_ENTITY)
                && getSize(format) == getSize(PIPELINE_ENTITY)
                && getOffset(format, getElementCount(format) - 1) == PIPELINE_ENTITY_TANGENT_OFFSET;
    }

    public static int size(VertexFormat format) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((format), new String[] {"func_177338_f", "getSize"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, -1);
    }

    public static int integerSize(VertexFormat format) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((format), new String[] {"func_181719_f", "getIntegerSize"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, -1);
    }

    public static int elementCount(VertexFormat format) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((format), new String[] {"func_177345_h", "getElementCount"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, -1);
    }

    public static VertexFormatElement element(VertexFormat format, int index) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.call(format, VertexFormatElement.class, null, new String[] {"func_177348_c", "getElement"}, new Class<?>[] {int.class}, index);
    }

    public static int offset(VertexFormat format, int index) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((format), new String[] {"func_181720_d", "getOffset"}, new Class<?>[] {int.class}, -1, (index));
    }

    public static boolean hasColor(VertexFormat format) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((format), new String[] {"func_177346_d", "hasColor"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false);
    }

    public static int colorOffset(VertexFormat format) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((format), new String[] {"func_177340_e", "getColorOffset"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, -1);
    }

    public static boolean hasNormal(VertexFormat format) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((format), new String[] {"func_177350_b", "hasNormal"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false);
    }

    public static boolean hasUvOffset(VertexFormat format, int id) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((format), new String[] {"func_177347_a", "hasUvOffset"}, new Class<?>[] {int.class}, false, (id));
    }

    public static int uvOffsetById(VertexFormat format, int id) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((format), new String[] {"func_177344_b", "getUvOffsetById"}, new Class<?>[] {int.class}, -1, (id));
    }

    private static void addElement(VertexFormat format, VertexFormatElement element) {
        com.l.ausm.impl.util.MinecraftReflectionCompat.addElement(format, element);
    }

    private static int getOffset(VertexFormat format, int index) {
        return offset(format, index);
    }

    private static int getElementCount(VertexFormat format) {
        return elementCount(format);
    }

    private static int getSize(VertexFormat format) {
        return size(format);
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

    public static boolean isAttributeAvailable(int index) {
        return isValidAttribute(index);
    }

    private static int maxVertexAttribs() {
        if (maxVertexAttribs < 0) {
            maxVertexAttribs = Math.max(0, GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS));
        }
        return maxVertexAttribs;
    }
}
