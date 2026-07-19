package net.multyfora.compat.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.item.component.Id;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.multyfora.compat.PackageConversion;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PackagerBlockEntity.class)
public class PackagerBlockEntityMixin {

    @Inject(method = "attemptToSend", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;getLinkPos()Lnet/minecraft/core/BlockPos;", shift = At.Shift.BEFORE), remap = false)
    private void afterAddressSet(List<PackagingRequest> queuedRequests, CallbackInfo ci, @Local(name = "createdBox") ItemStack createdBox) {
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null && !level.isClientSide()) {
            if (!PackageConversion.isEnvelopePackage(createdBox)) {
                PackageConversion.syncAddresses(createdBox, level);
            }
        }
    }

    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true, remap = false)
    private void onUnwrapBox(ItemStack box, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        if (self.animationTicks > 0)
            return;

        String address = PackageItem.getAddress(box);
        if (address == null || address.isEmpty())
            return;

        Direction facing = self.getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
        Level level = self.getLevel();
        if (level == null || level.isClientSide())
            return;

        BlockPos targetPos = self.getBlockPos().relative(facing.getOpposite());
        BlockEntity be = level.getBlockEntity(targetPos);
        if (!(be instanceof MailboxBlockEntity mailbox))
            return;

        if (simulate) {
            if (!mailbox.getItem(MailboxBlockEntity.SLOT_MAIL).isEmpty())
                return;
            cir.setReturnValue(true);
            return;
        }

        ItemStack mailStack = box.copyWithCount(1);

        ItemStackHandler crateContents = PackageItem.getContents(box);
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < crateContents.getSlots(); i++) {
            ItemStack slotItem = crateContents.getStackInSlot(i);
            if (!slotItem.isEmpty())
                items.add(slotItem.copy());
        }
        mailStack.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(items));

        PackageConversion.syncAddresses(mailStack, level);
        if (mailbox.getAddress() != null)
            Mail.setSender(mailStack, mailbox.getAddress());
        if (!Mail.hasId(mailStack))
            Mail.setId(mailStack, Id.create(level));

        mailbox.setItem(MailboxBlockEntity.SLOT_MAIL, mailStack);
        mailbox.setChanged();

        cir.setReturnValue(true);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;wakeTheFrogs()V", shift = At.Shift.BEFORE), remap = false)
    private void onBeforeWakeTheFrogs(CallbackInfo ci) {
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        if (self.heldBox.isEmpty())
            return;

        String address = PackageItem.getAddress(self.heldBox);
        if (address == null || address.isEmpty())
            return;

        Level level = self.getLevel();
        if (level == null || level.isClientSide())
            return;

        Direction facing = self.getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
        BlockPos mailboxPos = self.getBlockPos().relative(facing);
        BlockEntity be = level.getBlockEntity(mailboxPos);
        if (!(be instanceof MailboxBlockEntity mailbox))
            return;

        if (!mailbox.getItem(MailboxBlockEntity.SLOT_MAIL).isEmpty())
            return;

        ItemStack mailStack = self.heldBox.copy();

        ItemStackHandler crateContents = PackageItem.getContents(self.heldBox);
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < crateContents.getSlots(); i++) {
            ItemStack slotItem = crateContents.getStackInSlot(i);
            if (!slotItem.isEmpty())
                items.add(slotItem.copy());
        }
        mailStack.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(items));

        PackageConversion.syncAddresses(mailStack, level);
        if (mailbox.getAddress() != null)
            Mail.setSender(mailStack, mailbox.getAddress());
        if (!Mail.hasId(mailStack))
            Mail.setId(mailStack, Id.create(level));

        mailbox.setItem(MailboxBlockEntity.SLOT_MAIL, mailStack);
        mailbox.setChanged();

        self.heldBox = ItemStack.EMPTY;
    }
}
