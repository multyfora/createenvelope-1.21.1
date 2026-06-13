package net.multyfora.compat.mixin;

import io.github.mortuusars.envelope.world.inventory.PackingMenu;
import net.minecraft.world.item.ItemStack;
import net.multyfora.compat.PackageConversion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackingMenu.class)
public class PackingMenuMixin {

    @Inject(method = "createPackingResult", at = @At("RETURN"), cancellable = true, remap = false)
    private void onCreatePackingResult(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack original = cir.getReturnValue();
        if (PackageConversion.isEnvelopePackage(original)) {
            cir.setReturnValue(PackageConversion.toCreatePackage(original));
        }
    }
}
