package net.multyfora.compat.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.multyfora.compat.PackageConversion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MailboxBlockEntity.class)
public class MailboxBlockEntityMixin {

    @Inject(method = "isSendable", at = @At("HEAD"), cancellable = true, remap = false)
    private void onIsSendable(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (PackageItem.isPackage(stack)) {
            boolean hasCreateAddr = stack.has(AllDataComponents.PACKAGE_ADDRESS)
                && !stack.getOrDefault(AllDataComponents.PACKAGE_ADDRESS, "").isEmpty();
            boolean hasEnvAddr = stack.get(io.github.mortuusars.envelope.Envelope.DataComponents.MAIL_RECIPIENT) != null;
            cir.setReturnValue(hasCreateAddr || hasEnvAddr);
        }
    }

    @Inject(method = "removeItem", at = @At("RETURN"), cancellable = true, remap = false)
    private void onRemoveItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (slot < MailboxBlockEntity.INBOX_SLOT) return;
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty()) return;
        if (PackageConversion.isEnvelopePackage(result) || result.has(DataComponents.CONTAINER_LOOT))
            cir.setReturnValue(PackageConversion.toCreatePackage(result));
    }


}
