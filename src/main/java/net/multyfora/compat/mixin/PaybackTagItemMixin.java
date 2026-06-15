package net.multyfora.compat.mixin;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.PaybackTagItem;
import io.github.mortuusars.envelope.world.item.component.PaybackRequest;
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

@Mixin(PaybackTagItem.class)
public class PaybackTagItemMixin {

    private static final Logger LOGGER = LogUtils.getLogger(); //logs

    @Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true, remap = false)
    private void onOverrideStackedOnOther(ItemStack tagStack, Slot slot, ClickAction action,
                                          Player player, CallbackInfoReturnable<Boolean> cir) {
        if (action != ClickAction.SECONDARY) {
            LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: not secondary action"); //logs
            return;
        }
        if (!slot.allowModification(player)) {
            LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: slot modification not allowed"); //logs
            return;
        }

        ItemStack targetStack = slot.getItem();
        LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: target={}, mailable={}, isCreatePkg={}",
            targetStack.getItem(), targetStack.is(Envelope.Tags.Items.MAILABLE), PackageItem.isPackage(targetStack)); //logs

        if (!targetStack.is(Envelope.Tags.Items.MAILABLE) && !PackageItem.isPackage(targetStack)) {
            LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: target not mailable and not Create package"); //logs
            return;
        }

        PaybackRequest tagContents = tagStack.get(Envelope.DataComponents.PAYBACK_TAG_CONTENTS);
        PaybackRequest existingRequest = targetStack.get(Envelope.DataComponents.MAIL_PAYBACK_REQUEST);

        if (tagContents != null) {
            if (tagStack.getCount() < targetStack.getCount()) {
                LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: not enough tags ({} < {})",
                    tagStack.getCount(), targetStack.getCount()); //logs
                player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 1.0f);
                cir.setReturnValue(true);
                return;
            }
            if (tagContents.equals(existingRequest)) {
                LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: same request already applied"); //logs
                cir.setReturnValue(true);
                return;
            }
            LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: applying payback request to target"); //logs
            targetStack.set(Envelope.DataComponents.MAIL_PAYBACK_REQUEST, tagContents);
        } else {
            if (existingRequest == null) {
                LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: no tag contents and no existing request"); //logs
                cir.setReturnValue(true);
                return;
            }
            LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: removing existing payback request"); //logs
            targetStack.remove(Envelope.DataComponents.MAIL_PAYBACK_REQUEST);
        }

        slot.setChanged();

        if (tagContents != null) {
            tagStack.shrink(targetStack.getCount());
            LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: shrunk tag stack by {}", targetStack.getCount()); //logs
        }

        player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0f, 1.0f);
        LOGGER.debug("//logs PaybackTagItem.overrideStackedOnOther: success"); //logs
        cir.setReturnValue(true);
    }
}
