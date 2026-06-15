package com.l.ausm.impl.client;

import com.l.ausm.impl.Reference;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = Reference.MODID)
public final class RandomThingsLuminousColorCompat {
    private static final String RANDOM_THINGS = "randomthings";
    private static final int WHITE_TINT = 0xFFFFFF;
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
        IBlockColor tint = (state, world, pos, tintIndex) -> WHITE_TINT;
        for (String path : LUMINOUS_BLOCKS) {
            Block block = resolveBlock(path);
            if (block != null) {
                event.getBlockColors().registerBlockColorHandler(tint, block);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemColors(ColorHandlerEvent.Item event) {
        IItemColor tint = (stack, tintIndex) -> WHITE_TINT;
        for (String path : LUMINOUS_BLOCKS) {
            Block block = resolveBlock(path);
            if (block == null) {
                continue;
            }
            Item item = Item.getItemFromBlock(block);
            if (item != Items.AIR) {
                event.getItemColors().registerItemColorHandler(tint, item);
            }
        }
    }

    private static Block resolveBlock(String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(RANDOM_THINGS, path));
        if (block != null) {
            return block;
        }
        for (ResourceLocation key : ForgeRegistries.BLOCKS.getKeys()) {
            if (RANDOM_THINGS.equalsIgnoreCase(key.getNamespace()) && path.equalsIgnoreCase(key.getPath())) {
                return ForgeRegistries.BLOCKS.getValue(key);
            }
        }
        return null;
    }
}
