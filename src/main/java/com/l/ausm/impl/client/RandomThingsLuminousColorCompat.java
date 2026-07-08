package com.l.ausm.impl.client;

import com.l.ausm.impl.Reference;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Map;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = Reference.MODID)
public final class RandomThingsLuminousColorCompat {
    private static final String RANDOM_THINGS = "randomthings";
    private static final int WHITE_TINT = 0xFFFFFF;
    private static final IBlockColor BLOCK_TINT = proxyColorHandler(IBlockColor.class);
    private static final IItemColor ITEM_TINT = proxyColorHandler(IItemColor.class);
    private static final String[] LUMINOUS_BLOCKS = {
            "luminousBlock",
            "luminousblock",
            "translucentLuminousBlock",
            "translucentluminousblock",
            "luminousStainedBrick",
            "luminousstainedbrick"
    };

    private RandomThingsLuminousColorCompat() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockColors(ColorHandlerEvent.Block event) {
        for (String path : LUMINOUS_BLOCKS) {
            Block block = resolveBlock(path);
            if (block != null) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((event.getBlockColors()), new String[] {"func_186722_a", "registerBlockColorHandler"},
                new Class<?>[] {net.minecraft.client.renderer.color.IBlockColor.class, Block[].class}, (BLOCK_TINT), (new Block[] {block}));;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemColors(ColorHandlerEvent.Item event) {
        for (String path : LUMINOUS_BLOCKS) {
            Block block = resolveBlock(path);
            if (block == null) {
                continue;
            }
            Item item = com.l.ausm.impl.util.MinecraftReflectionCompat.call(Item.class, Item.class, null, new String[] {"func_150898_a", "getItemFromBlock"}, new Class<?>[] {Block.class}, block);
            if (item != null) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((event.getItemColors()), new String[] {"func_186730_a", "registerItemColorHandler"},
                new Class<?>[] {net.minecraft.client.renderer.color.IItemColor.class, Item[].class}, (ITEM_TINT), (new Item[] {item}));;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxyColorHandler(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "AUSM white color handler for " + type.getName();
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                            default -> null;
                        };
                    }
                    if (method.getReturnType() != int.class) {
                        return null;
                    }
                    if (type == IBlockColor.class) {
                        IBlockState state = args != null && args.length > 0 && args[0] instanceof IBlockState ? (IBlockState) args[0] : null;
                        IBlockAccess world = args != null && args.length > 1 && args[1] instanceof IBlockAccess ? (IBlockAccess) args[1] : null;
                        BlockPos pos = args != null && args.length > 2 && args[2] instanceof BlockPos ? (BlockPos) args[2] : null;
                        return blockTint(state, world, pos);
                    }
                    if (type == IItemColor.class) {
                        ItemStack stack = args != null && args.length > 0 && args[0] instanceof ItemStack ? (ItemStack) args[0] : null;
                        return itemTint(stack);
                    }
                    return WHITE_TINT;
                }
        );
    }

    private static int blockTint(IBlockState state, IBlockAccess world, BlockPos pos) {
        String color = stateColorName(state);
        if (color != null) {
            return dyeColor(color);
        }
        if (state != null) {
            Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
            int metadata = blockMetadata(block, state);
            return dyeColor(metadata);
        }
        return WHITE_TINT;
    }

    private static int blockMetadata(Block block, IBlockState state) {
        if (block == null || state == null) {
            return 0;
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callInt(block,
                new String[] {"func_176201_c", "getMetaFromState"}, new Class<?>[] {IBlockState.class}, 0, state);
    }

    private static int itemTint(ItemStack stack) {
        if (stack == null || com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
            return WHITE_TINT;
        }
        return dyeColor(com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackMetadata(stack));
    }

    private static String stateColorName(IBlockState state) {
        if (state == null) {
            return null;
        }
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : com.l.ausm.impl.util.MinecraftReflectionCompat.stateProperties(state).entrySet()) {
            IProperty<?> property = entry.getKey();
            if (property == null) {
                continue;
            }
            String propertyName = com.l.ausm.impl.util.MinecraftReflectionCompat.propertyName(property);
            if (!"color".equalsIgnoreCase(propertyName) && !"colour".equalsIgnoreCase(propertyName)) {
                continue;
            }
            String valueName = com.l.ausm.impl.util.MinecraftReflectionCompat.propertyValueName(property, entry.getValue());
            return valueName != null ? valueName : String.valueOf(entry.getValue());
        }
        return null;
    }

    private static int dyeColor(int metadata) {
        return switch (metadata & 15) {
            case 0 -> 0xFFFFFF;
            case 1 -> 0xD87F33;
            case 2 -> 0xB24CD8;
            case 3 -> 0x6699D8;
            case 4 -> 0xE5E533;
            case 5 -> 0x7FCC19;
            case 6 -> 0xF27FA5;
            case 7 -> 0x4C4C4C;
            case 8 -> 0x999999;
            case 9 -> 0x4C7F99;
            case 10 -> 0x7F3FB2;
            case 11 -> 0x334CB2;
            case 12 -> 0x664C33;
            case 13 -> 0x667F33;
            case 14 -> 0x993333;
            case 15 -> 0x191919;
            default -> WHITE_TINT;
        };
    }

    private static int dyeColor(String name) {
        if (name == null || name.isEmpty()) {
            return WHITE_TINT;
        }
        String key = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return switch (key) {
            case "white" -> 0xFFFFFF;
            case "orange" -> 0xD87F33;
            case "magenta" -> 0xB24CD8;
            case "lightblue" -> 0x6699D8;
            case "yellow" -> 0xE5E533;
            case "lime" -> 0x7FCC19;
            case "pink" -> 0xF27FA5;
            case "gray", "grey" -> 0x4C4C4C;
            case "silver", "lightgray", "lightgrey" -> 0x999999;
            case "cyan" -> 0x4C7F99;
            case "purple" -> 0x7F3FB2;
            case "blue" -> 0x334CB2;
            case "brown" -> 0x664C33;
            case "green" -> 0x667F33;
            case "red" -> 0x993333;
            case "black" -> 0x191919;
            default -> WHITE_TINT;
        };
    }

    private static Block resolveBlock(String path) {
        Block block = registryBlock(new ResourceLocation(RANDOM_THINGS, path));
        if (block != null) {
            return block;
        }
        for (ResourceLocation key : com.l.ausm.impl.util.MinecraftReflectionCompat.registryKeys(ForgeRegistries.BLOCKS)) {
            if (RANDOM_THINGS.equalsIgnoreCase(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(key)) && path.equalsIgnoreCase(com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(key))) {
                return registryBlock(key);
            }
        }
        return null;
    }

    private static Block registryBlock(ResourceLocation key) {
        Object value = com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((ForgeRegistries.BLOCKS), new String[] {"func_82594_a", "getObject", "getValue"}, new Class<?>[] {net.minecraft.util.ResourceLocation.class}, (key));
        return value instanceof Block ? (Block) value : null;
    }
}
