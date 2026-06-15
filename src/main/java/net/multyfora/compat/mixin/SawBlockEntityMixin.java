package net.multyfora.compat.mixin;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SawBlockEntity.class)
public class SawBlockEntityMixin {

    private static final Logger LOGGER = LogUtils.getLogger(); //logs

    @Inject(method = "isSawable", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onIsSawable(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        Block block = state.getBlock();
        LOGGER.debug("//logs SawBlockEntity.isSawable: checking block {}", block); //logs
        if (block instanceof io.github.mortuusars.envelope.world.block.PackageBlock) {
            LOGGER.debug("//logs SawBlockEntity.isSawable: PackageBlock IS sawable!"); //logs
            cir.setReturnValue(true);
        }
    }
}
