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
    private static final Class<?>[] INT_PARAMETER = {int.class};
    private static final String[] SIZE_NAMES = {"func_177338_f", "getSize"};
    private static final String[] INTEGER_SIZE_NAMES = {"func_181719_f", "getIntegerSize"};
    private static final String[] ELEMENT_COUNT_NAMES = {"func_177345_h", "getElementCount"};
    private static final String[] ELEMENT_NAMES = {"func_177348_c", "getElement"};
    private static final String[] OFFSET_NAMES = {"func_181720_d", "getOffset"};
    private static final String[] HAS_COLOR_NAMES = {"func_177346_d", "hasColor"};
    private static final String[] COLOR_OFFSET_NAMES = {"func_177340_e", "getColorOffset"};
    private static final String[] HAS_NORMAL_NAMES = {"func_177350_b", "hasNormal"};
    private static final String[] HAS_UV_OFFSET_NAMES = {"func_177347_a", "hasUvOffset"};
    private static final String[] UV_OFFSET_NAMES = {"func_177344_b", "getUvOffsetById"};

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
    private static final int PIPELINE_BLOCK_SIZE = 56;
    private static final int PIPELINE_ENTITY_SIZE = 48;
    private static final int PIPELINE_BLOCK_ELEMENT_COUNT = 10;
    private static final int PIPELINE_ENTITY_ELEMENT_COUNT = 8;
    private static VertexFormatElement pipelineBlockFirstElement;
    private static VertexFormatElement pipelineEntityFirstElement;

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
        pipelineBlockFirstElement = rawElement(PIPELINE_BLOCK, 0);
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
        pipelineEntityFirstElement = rawElement(PIPELINE_ENTITY, 0);
        PIPELINE_ENTITY_NORMAL_OFFSET = getOffset(PIPELINE_ENTITY, 3);
        PIPELINE_ENTITY_MC_ENTITY_OFFSET = getOffset(PIPELINE_ENTITY, 5);
        PIPELINE_ENTITY_MID_TEX_COORD_OFFSET = getOffset(PIPELINE_ENTITY, 6);
        PIPELINE_ENTITY_TANGENT_OFFSET = getOffset(PIPELINE_ENTITY, 7);
    }

    public static boolean isPipelineBlock(VertexFormat format) {
        return format == PIPELINE_BLOCK
                || format != null
                && PIPELINE_BLOCK != null
                && getElementCount(format) == getElementCount(PIPELINE_BLOCK)
                && getSize(format) == getSize(PIPELINE_BLOCK)
                && getOffset(format, getElementCount(format) - 1) == PIPELINE_BLOCK_MID_BLOCK_OFFSET;
    }

    public static boolean isPipelineEntity(VertexFormat format) {
        return format == PIPELINE_ENTITY
                || format != null
                && PIPELINE_ENTITY != null
                && getElementCount(format) == getElementCount(PIPELINE_ENTITY)
                && getSize(format) == getSize(PIPELINE_ENTITY)
                && getOffset(format, getElementCount(format) - 1) == PIPELINE_ENTITY_TANGENT_OFFSET;
    }

    public static int size(VertexFormat format) {
        if (format == PIPELINE_BLOCK) {
            return PIPELINE_BLOCK_SIZE;
        }
        if (format == PIPELINE_ENTITY) {
            return PIPELINE_ENTITY_SIZE;
        }
        return format != null ? MinecraftReflectionCompat.callInt(format,
                SIZE_NAMES, MinecraftReflectionCompat.NO_PARAMETERS, -1) : -1;
    }

    public static int integerSize(VertexFormat format) {
        if (format == PIPELINE_BLOCK) {
            return PIPELINE_BLOCK_SIZE / Integer.BYTES;
        }
        if (format == PIPELINE_ENTITY) {
            return PIPELINE_ENTITY_SIZE / Integer.BYTES;
        }
        return format != null ? MinecraftReflectionCompat.callInt(format,
                INTEGER_SIZE_NAMES, MinecraftReflectionCompat.NO_PARAMETERS, -1) : -1;
    }

    public static int elementCount(VertexFormat format) {
        if (format == PIPELINE_BLOCK) {
            return PIPELINE_BLOCK_ELEMENT_COUNT;
        }
        if (format == PIPELINE_ENTITY) {
            return PIPELINE_ENTITY_ELEMENT_COUNT;
        }
        return format != null ? MinecraftReflectionCompat.callInt(format,
                ELEMENT_COUNT_NAMES, MinecraftReflectionCompat.NO_PARAMETERS, -1) : -1;
    }

    public static VertexFormatElement element(VertexFormat format, int index) {
        if (index == 0) {
            if (format == PIPELINE_BLOCK) {
                return pipelineBlockFirstElement;
            }
            if (format == PIPELINE_ENTITY) {
                return pipelineEntityFirstElement;
            }
        }
        return rawElement(format, index);
    }

    private static VertexFormatElement rawElement(VertexFormat format, int index) {
        return format != null ? MinecraftReflectionCompat.call(format, VertexFormatElement.class, null,
                ELEMENT_NAMES, INT_PARAMETER, index) : null;
    }

    public static int offset(VertexFormat format, int index) {
        return format != null ? MinecraftReflectionCompat.callInt(format,
                OFFSET_NAMES, INT_PARAMETER, -1, index) : -1;
    }

    public static boolean hasColor(VertexFormat format) {
        if (format == PIPELINE_BLOCK || format == PIPELINE_ENTITY) {
            return true;
        }
        return format != null && MinecraftReflectionCompat.callBoolean(format,
                HAS_COLOR_NAMES, MinecraftReflectionCompat.NO_PARAMETERS, false);
    }

    public static int colorOffset(VertexFormat format) {
        if (format == PIPELINE_BLOCK || format == PIPELINE_ENTITY) {
            return 12;
        }
        return format != null ? MinecraftReflectionCompat.callInt(format,
                COLOR_OFFSET_NAMES, MinecraftReflectionCompat.NO_PARAMETERS, -1) : -1;
    }

    public static boolean hasNormal(VertexFormat format) {
        if (format == PIPELINE_BLOCK || format == PIPELINE_ENTITY) {
            return true;
        }
        return format != null && MinecraftReflectionCompat.callBoolean(format,
                HAS_NORMAL_NAMES, MinecraftReflectionCompat.NO_PARAMETERS, false);
    }

    public static boolean hasUvOffset(VertexFormat format, int id) {
        if (format == PIPELINE_BLOCK) {
            return id == 0 || id == 1;
        }
        if (format == PIPELINE_ENTITY) {
            return id == 0;
        }
        return format != null && MinecraftReflectionCompat.callBoolean(format,
                HAS_UV_OFFSET_NAMES, INT_PARAMETER, false, id);
    }

    public static int uvOffsetById(VertexFormat format, int id) {
        if ((format == PIPELINE_BLOCK || format == PIPELINE_ENTITY) && id == 0) {
            return 16;
        }
        if (format == PIPELINE_BLOCK && id == 1) {
            return 24;
        }
        return format != null ? MinecraftReflectionCompat.callInt(format,
                UV_OFFSET_NAMES, INT_PARAMETER, -1, id) : -1;
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
