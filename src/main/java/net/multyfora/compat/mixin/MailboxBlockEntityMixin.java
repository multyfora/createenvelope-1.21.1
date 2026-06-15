package net.multyfora.compat.mixin;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.multyfora.compat.PackageConversion;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MailboxBlockEntity.class)
public class MailboxBlockEntityMixin {

    private static final Logger LOGGER = LogUtils.getLogger(); //logs

    @Inject(method = "isSendable", at = @At("HEAD"), cancellable = true, remap = false)
    private void onIsSendable(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (PackageItem.isPackage(stack)) {
            boolean hasCreateAddr = stack.has(AllDataComponents.PACKAGE_ADDRESS)
                    && !stack.getOrDefault(AllDataComponents.PACKAGE_ADDRESS, "").isEmpty();
            boolean hasEnvAddr = stack.get(io.github.mortuusars.envelope.Envelope.DataComponents.MAIL_RECIPIENT) != null;
            LOGGER.debug("//logs MailboxBlockEntity.isSendable(Create pkg): hasCreateAddr={}, hasEnvAddr={}, result={}",
                hasCreateAddr, hasEnvAddr, hasCreateAddr || hasEnvAddr); //logs
            cir.setReturnValue(hasCreateAddr || hasEnvAddr);
        }
    }

    @Inject(method = "removeItem", at = @At("RETURN"), cancellable = true, remap = false)
    private void onRemoveItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (slot < MailboxBlockEntity.INBOX_SLOT) return;
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty()) return;
        LOGGER.debug("//logs MailboxBlockEntity.removeItem: slot={}, result={}, isEnvPkg={}, hasLoot={}",
            slot, result.getItem(), PackageConversion.isEnvelopePackage(result), result.has(DataComponents.CONTAINER_LOOT)); //logs
        if (PackageConversion.isEnvelopePackage(result) || result.has(DataComponents.CONTAINER_LOOT)) {
            ItemStack converted = PackageConversion.toCreatePackage(result);
            LOGGER.debug("//logs MailboxBlockEntity.removeItem: converted to {}", converted.getItem()); //logs
            cir.setReturnValue(converted);
        }
    }


}
