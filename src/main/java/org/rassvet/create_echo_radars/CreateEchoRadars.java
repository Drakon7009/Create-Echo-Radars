package org.rassvet.create_echo_radars;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.rassvet.create_echo_radars.content.sonar.SonarBlock;
import org.rassvet.create_echo_radars.content.sonar.SonarBlockEntity;
import org.rassvet.create_echo_radars.content.sonar.SonarMenu;
import org.rassvet.create_echo_radars.content.sonar.SonarType;
import org.slf4j.Logger;

@Mod(CreateEchoRadars.MOD_ID)
public final class CreateEchoRadars {
    public static final String MOD_ID = "create_echo_radars";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, SonarBlock> SONAR =
            BLOCKS.register("sonar", () -> new SonarBlock(SonarType.FORWARD_LOOKING_F));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, SonarBlock> ECHO_SOUNDER =
            BLOCKS.register("echo_sounder", () -> new SonarBlock(SonarType.ECHO_SOUNDER_A));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, SonarBlock> MECHANICAL_SCANNING_SONAR =
            BLOCKS.register("mechanical_scanning_sonar", () -> new SonarBlock(SonarType.MECHANICAL_IMAGING_C));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, SonarBlock> SIDE_SCAN_SONAR =
            BLOCKS.register("side_scan_sonar", () -> new SonarBlock(SonarType.SIDE_SCAN_D));
    public static final DeferredHolder<Item, BlockItem> SONAR_ITEM =
            ITEMS.register("sonar", () -> new BlockItem(SONAR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> ECHO_SOUNDER_ITEM =
            ITEMS.register("echo_sounder", () -> new BlockItem(ECHO_SOUNDER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> MECHANICAL_SCANNING_SONAR_ITEM =
            ITEMS.register("mechanical_scanning_sonar", () ->
                    new BlockItem(MECHANICAL_SCANNING_SONAR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> SIDE_SCAN_SONAR_ITEM =
            ITEMS.register("side_scan_sonar", () -> new BlockItem(SIDE_SCAN_SONAR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SONAR_DEBUG_TOOL =
            ITEMS.register("sonar_debug_tool", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SonarBlockEntity>> SONAR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("sonar", () ->
                    BlockEntityType.Builder.of(SonarBlockEntity::new, SONAR.get(), ECHO_SOUNDER.get(),
                            MECHANICAL_SCANNING_SONAR.get(), SIDE_SCAN_SONAR.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<SonarMenu>> SONAR_MENU =
            MENUS.register("sonar", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(SonarMenu::new));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_echo_radars"))
                    .icon(() -> SONAR_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(SONAR_ITEM.get());
                        output.accept(ECHO_SOUNDER_ITEM.get());
                        output.accept(MECHANICAL_SCANNING_SONAR_ITEM.get());
                        output.accept(SIDE_SCAN_SONAR_ITEM.get());
                        output.accept(SONAR_DEBUG_TOOL.get());
                    })
                    .build());

    public CreateEchoRadars(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT,
                org.rassvet.create_echo_radars.client.ClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER,
                org.rassvet.create_echo_radars.config.ServerConfig.SPEC);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        CREATIVE_TABS.register(modBus);
        ModNetworking.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.register(modBus, container);
        }
        CommonEvents.register();
    }
}
