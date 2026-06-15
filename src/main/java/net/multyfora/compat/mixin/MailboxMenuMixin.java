package net.multyfora.compat.mixin;

import com.mojang.logging.LogUtils;
import io.github.mortuusars.envelope.world.inventory.MailboxMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.multyfora.compat.PackageConversion;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MailboxMenu.class)
public class MailboxMenuMixin {

    private static final Logger LOGGER = LogUtils.getLogger(); //logs

    @Inject(method = "extractMail", at = @At("RETURN"), cancellable = true, remap = false)
    private void onExtractMail(ServerLevel level, int index, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty()) return;
        LOGGER.debug("//logs MailboxMenu.extractMail: index={}, result={}, isEnvPkg={}, hasLoot={}",
            index, result.getItem(), PackageConversion.isEnvelopePackage(result), result.has(DataComponents.CONTAINER_LOOT)); //logs
        if (PackageConversion.isEnvelopePackage(result) || result.has(DataComponents.CONTAINER_LOOT)) {
            ItemStack converted = PackageConversion.toCreatePackage(result);
            LOGGER.debug("//logs MailboxMenu.extractMail: converted to {}", converted.getItem()); //logs
            cir.setReturnValue(converted);
        }
    }
}
