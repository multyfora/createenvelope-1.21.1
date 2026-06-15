package net.multyfora.compat.mixin;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.AddressTagItem;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(AddressTagItem.class)
public class AddressTagItemMixin {

    private static final Logger LOGGER = LogUtils.getLogger(); //logs

    @Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true, remap = false)
    private void onOverrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action,
                                          Player player, CallbackInfoReturnable<Boolean> cir) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player))
            return;

        ItemStack target = slot.getItem();
        LOGGER.debug("//logs AddressTagItem.overrideStackedOnOther: target={}, isCreatePkg={}",
            target.getItem(), PackageItem.isPackage(target)); //logs
        if (!PackageItem.isPackage(target))
            return;

        var tagAddress = stack.get(Envelope.DataComponents.ADDRESS);
        var existingRecipient = Mail.getRecipient(target).orElse(null);
        LOGGER.debug("//logs AddressTagItem.overrideStackedOnOther: tagAddress={}, existingRecipient={}",
            tagAddress != null ? tagAddress.getString() : "null",
            existingRecipient != null ? existingRecipient.getString() : "null"); //logs

        if (Objects.equals(tagAddress, existingRecipient)) {
            LOGGER.debug("//logs AddressTagItem.overrideStackedOnOther: address unchanged"); //logs
            cir.setReturnValue(true);
            return;
        }

        if (tagAddress == null) {
            if (stack.getCount() >= target.getCount())
                player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 1.0f);
            LOGGER.debug("//logs AddressTagItem.overrideStackedOnOther: tag has no address"); //logs
            cir.setReturnValue(true);
            return;
        }

        ItemStack copy = target.copy();
        Mail.setRecipient(copy, tagAddress);
        Mail.removePreviousDeliveryData(copy);

        if (slot.mayPlace(copy)) {
            slot.setByPlayer(copy);
            PackageItem.addAddress(copy, tagAddress.getString());
            LOGGER.debug("//logs AddressTagItem.overrideStackedOnOther: applied address {} to package", tagAddress.getString()); //logs
        } else {
            LOGGER.debug("//logs AddressTagItem.overrideStackedOnOther: slot does not accept modified item"); //logs
            player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 1.0f);
            cir.setReturnValue(true);
            return;
        }

        stack.shrink(target.getCount());
        player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0f, 1.0f);
        cir.setReturnValue(true);
    }

    @Inject(method = "overrideStackedOnOther", at = @At("RETURN"), remap = false)
    private void onPostOverrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action,
                                              Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        ItemStack target = slot.getItem();
        if (!PackageItem.isPackage(target)) return;
        if (!target.has(Envelope.DataComponents.MAIL_RECIPIENT)) return;

        var addr = target.get(Envelope.DataComponents.MAIL_RECIPIENT);
        PackageItem.addAddress(target, addr.getString());
        LOGGER.debug("//logs AddressTagItem.onPostOverride: synced Envelope address {} back to Create package", addr.getString()); //logs
    }
}
