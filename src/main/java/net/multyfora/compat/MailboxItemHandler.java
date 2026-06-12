package net.multyfora.compat;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public class MailboxItemHandler implements IItemHandler {
    private final MailboxBlockEntity mailbox;

    public MailboxItemHandler(MailboxBlockEntity mailbox) {
        this.mailbox = mailbox;
    }

    @Override
    public int getSlots() {
        return MailboxBlockEntity.REGULAR_SLOTS + mailbox.getAllMail().size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return mailbox.getItem(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) return stack;

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
        if (slot < MailboxBlockEntity.INBOX_SLOT) return ItemStack.EMPTY;

        ItemStack existing = mailbox.getItem(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;

        int toExtract = Math.min(amount, existing.getCount());
        ItemStack extracted = existing.copyWithCount(toExtract);

        if (!simulate) {
            mailbox.removeItem(slot, toExtract);
        }
        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot < MailboxBlockEntity.INBOX_SLOT ? 64 : 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (slot == MailboxBlockEntity.SLOT_FOOD) return stack.is(Envelope.Tags.Items.PIGEON_FOOD);
        if (slot == MailboxBlockEntity.SLOT_MAIL) return mailbox.isSendable(stack);
        return false;
    }
}
