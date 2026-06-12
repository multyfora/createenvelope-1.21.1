package net.multyfora.compat.mixin;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.foundation.item.ItemHelper;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

@Mixin(SawBlockEntity.class)
public class SawBlockEntityMixin {

    @Shadow(remap = false)
    public ProcessingInventory inventory;

    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "isSawable", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onIsSawable(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        Block block = state.getBlock();
        LOGGER.info("Saw checking block: {}", block);
        if (block instanceof io.github.mortuusars.envelope.world.block.PackageBlock) {
            LOGGER.info("Package block IS sawable!");
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "applyRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void onApplyRecipe(CallbackInfo ci) {
        ItemStack input = inventory.getStackInSlot(0);

        // Resolve loot table if present (handles "lost mail" packages)
        SeededContainerLoot containerLoot = input.get(DataComponents.CONTAINER_LOOT);
        if (containerLoot != null) {
            LOGGER.info("Saw resolving loot table for package");
            ServerLevel serverLevel = ServerLifecycleHooks.getCurrentServer().overworld();
            LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(containerLoot.lootTable());
            LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
            SimpleContainer tempContainer = new SimpleContainer(6);
            lootTable.fill(tempContainer, params, containerLoot.seed());
            input.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(tempContainer));
            input.remove(DataComponents.CONTAINER_LOOT);
        }

        // Extract package contents
        PackageContents contents = input.get(Envelope.DataComponents.PACKAGE_CONTENTS);
        if (contents != null && !contents.isEmpty()) {
            LOGGER.info("Saw opening envelope package with {} contents", contents.size());
            List<ItemStack> items = contents.copyItems();
            inventory.clear();
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack stack : items) {
                if (!stack.isEmpty())
                    ItemHelper.addToList(stack, list);
            }
            for (int slot = 0; slot < list.size() && slot + 1 < inventory.getSlots(); slot++)
                inventory.setStackInSlot(slot + 1, list.get(slot));
            ci.cancel();
        }
    }
}
