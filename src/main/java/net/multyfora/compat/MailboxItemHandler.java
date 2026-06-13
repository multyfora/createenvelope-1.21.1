package net.multyfora.compat;

import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class MailboxItemHandler implements IItemHandler {
    private final MailboxBlockEntity mailbox;

    public MailboxItemHandler(MailboxBlockEntity mailbox) {
        this.mailbox = mailbox;
    }

    @Override
    public int getSlots() {
        int mailSize = mailbox.getAllMail().size();
        int base = MailboxBlockEntity.INBOX_SLOT + mailSize;
        ItemStack mailStack = mailbox.getItem(MailboxBlockEntity.SLOT_MAIL);
        if (PackageItem.isPackage(mailStack)) {
            ItemStackHandler contents = PackageItem.getContents(mailStack);
            if (contents.getSlots() > 0)
                base += contents.getSlots();
        }
        return base;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot == MailboxBlockEntity.SLOT_FOOD || slot == MailboxBlockEntity.SLOT_MAIL)
            return mailbox.getItem(slot);

        if (slot >= MailboxBlockEntity.INBOX_SLOT) {
            int mailSize = mailbox.getAllMail().size();
            int inboxIndex = slot - MailboxBlockEntity.INBOX_SLOT;
            if (inboxIndex < mailSize)
                return mailbox.getAllMail().get(inboxIndex);

            int contentIndex = inboxIndex - mailSize;
            ItemStack mailStack = mailbox.getItem(MailboxBlockEntity.SLOT_MAIL);
            if (PackageItem.isPackage(mailStack)) {
                ItemStackHandler contents = PackageItem.getContents(mailStack);
                if (contentIndex < contents.getSlots())
                    return contents.getStackInSlot(contentIndex).copy();
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) return stack;

        if (slot == MailboxBlockEntity.SLOT_MAIL && PackageItem.isPackage(stack) && !simulate) {
            Level level = mailbox.getLevel();

            ItemStackHandler crateContents = PackageItem.getContents(stack);
            java.util.List<ItemStack> items = new java.util.ArrayList<>();
            for (int i = 0; i < crateContents.getSlots(); i++) {
                ItemStack slotItem = crateContents.getStackInSlot(i);
                if (!slotItem.isEmpty())
                    items.add(slotItem.copy());
            }
            stack.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(items));

            PackageConversion.syncAddresses(stack, level);
        }

        ItemStack existing = mailbox.getItem(slot);
        int maxInsert = Math.min(stack.getMaxStackSize(), getSlotLimit(slot)) - existing.getCount();
        if (maxInsert <= 0) return stack;

        int toInsert = Math.min(stack.getCount(), maxInsert);
        ItemStack remaining = stack.copy();
        remaining.shrink(toInsert);

        if (!simulate) {
            if (existing.isEmpty()) {
                mailbox.setItem(slot, stack.copyWithCount(toInsert));
            } else {
                existing.grow(toInsert);
            }
            mailbox.setChanged();
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int mailSize = mailbox.getAllMail().size();
        int inboxEnd = MailboxBlockEntity.INBOX_SLOT + mailSize;
        if (slot < MailboxBlockEntity.INBOX_SLOT || slot >= inboxEnd)
            return ItemStack.EMPTY;

        ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;

        int toExtract = Math.min(amount, existing.getCount());
        ItemStack extracted = existing.copyWithCount(toExtract);

        if (!simulate) {
            mailbox.removeItem(slot, toExtract);
        }

        if (PackageConversion.isEnvelopePackage(extracted) || extracted.has(DataComponents.CONTAINER_LOOT))
            extracted = PackageConversion.toCreatePackage(extracted);

        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        int mailSize = mailbox.getAllMail().size();
        int inboxEnd = MailboxBlockEntity.INBOX_SLOT + mailSize;
        if (slot >= inboxEnd) return 0;
        return slot < MailboxBlockEntity.INBOX_SLOT ? 64 : 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int mailSize = mailbox.getAllMail().size();
        int inboxEnd = MailboxBlockEntity.INBOX_SLOT + mailSize;
        if (slot >= inboxEnd) return false;
        if (slot == MailboxBlockEntity.SLOT_FOOD) return stack.is(Envelope.Tags.Items.PIGEON_FOOD);
        if (slot == MailboxBlockEntity.SLOT_MAIL) return mailbox.isSendable(stack);
        return slot >= MailboxBlockEntity.INBOX_SLOT;
    }
}
