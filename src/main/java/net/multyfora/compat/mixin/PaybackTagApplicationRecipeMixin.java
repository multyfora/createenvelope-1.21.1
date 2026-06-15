package net.multyfora.compat.mixin;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.PaybackTagItem;
import io.github.mortuusars.envelope.world.item.component.PaybackRequest;
import io.github.mortuusars.envelope.world.item.crafting.PaybackTagApplicationRecipe;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PaybackTagApplicationRecipe.class)
public class PaybackTagApplicationRecipeMixin {

    private static final Logger LOGGER = LogUtils.getLogger(); //logs

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true, remap = false)
    private void onMatches(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        int mailableCount = 0;
        int paybackTagCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            LOGGER.debug("//logs PaybackTagApplicationRecipe.matches slot {}: item={}, mailable={}, isCreatePkg={}",
                i, stack.getItem(), stack.is(Envelope.Tags.Items.MAILABLE), PackageItem.isPackage(stack)); //logs

            if (stack.is(Envelope.Tags.Items.MAILABLE) || PackageItem.isPackage(stack)) {
                mailableCount++;
            } else if (stack.getItem() instanceof PaybackTagItem && stack.has(Envelope.DataComponents.PAYBACK_TAG_CONTENTS)) {
                paybackTagCount++;
            } else {
                LOGGER.debug("//logs PaybackTagApplicationRecipe.matches: rejecting non-mailable item {}", stack.getItem()); //logs
                cir.setReturnValue(false);
                return;
            }

            if (paybackTagCount > 1 || mailableCount > 1) {
                LOGGER.debug("//logs PaybackTagApplicationRecipe.matches: too many items (mailable={}, paybackTag={})", mailableCount, paybackTagCount); //logs
                cir.setReturnValue(false);
                return;
            }
        }

        boolean result = mailableCount == 1 && paybackTagCount == 1;
        LOGGER.debug("//logs PaybackTagApplicationRecipe.matches result: {}", result); //logs
        cir.setReturnValue(result);
    }

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true, remap = false)
    private void onAssemble(CraftingInput input, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack mailableItem = ItemStack.EMPTY;
        ItemStack paybackTag = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(Envelope.Tags.Items.MAILABLE) || PackageItem.isPackage(stack)) {
                mailableItem = stack;
                LOGGER.debug("//logs PaybackTagApplicationRecipe.assemble: found mailable item {} (CreatePkg={})",
                    stack.getItem(), PackageItem.isPackage(stack)); //logs
            } else if (stack.getItem() instanceof PaybackTagItem) {
                paybackTag = stack;
                LOGGER.debug("//logs PaybackTagApplicationRecipe.assemble: found payback tag"); //logs
            }
        }

        if (mailableItem.isEmpty() || paybackTag.isEmpty()) {
            LOGGER.debug("//logs PaybackTagApplicationRecipe.assemble: missing required item (mailable={}, paybackTag={})",
                mailableItem.isEmpty(), paybackTag.isEmpty()); //logs
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        PaybackRequest request = paybackTag.get(Envelope.DataComponents.PAYBACK_TAG_CONTENTS);
        if (request == null) {
            LOGGER.debug("//logs PaybackTagApplicationRecipe.assemble: payback tag has no PAYBACK_TAG_CONTENTS"); //logs
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        ItemStack result = mailableItem.copyWithCount(1);
        Mail.setPaybackRequest(result, request);
        LOGGER.debug("//logs PaybackTagApplicationRecipe.assemble: created result {} with payback request ({} items requested)",
            result.getItem(), request.items().size()); //logs
        cir.setReturnValue(result);
    }
}
