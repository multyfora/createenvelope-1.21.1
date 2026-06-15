package net.multyfora.compat.mixin;

import com.mojang.logging.LogUtils;
import io.github.mortuusars.envelope.world.inventory.PackingMenu;
import net.minecraft.world.item.ItemStack;
import net.multyfora.compat.PackageConversion;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackingMenu.class)
public class PackingMenuMixin {

    private static final Logger LOGGER = LogUtils.getLogger(); //logs

    @Inject(method = "createPackingResult", at = @At("RETURN"), cancellable = true, remap = false)
    private void onCreatePackingResult(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack original = cir.getReturnValue();
        LOGGER.debug("//logs PackingMenu.createPackingResult: original={}, isEnvPkg={}",
            original.getItem(), PackageConversion.isEnvelopePackage(original)); //logs
        if (PackageConversion.isEnvelopePackage(original)) {
            ItemStack converted = PackageConversion.toCreatePackage(original);
            LOGGER.debug("//logs PackingMenu.createPackingResult: converted to {}", converted.getItem()); //logs
            cir.setReturnValue(converted);
        }
    }
}
