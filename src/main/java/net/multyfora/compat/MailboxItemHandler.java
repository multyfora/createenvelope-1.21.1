package net.multyfora.compat;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;

public class MailboxItemHandler implements IItemHandler {
    private static final Logger LOGGER = LogUtils.getLogger(); //logs
    private final MailboxBlockEntity mailbox;

    public MailboxItemHandler(MailboxBlockEntity mailbox) {
        this.mailbox = mailbox;
        LOGGER.debug("//logs MailboxItemHandler created for mailbox at {}", mailbox.getBlockPos()); //logs
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
        LOGGER.debug("//logs MailboxItemHandler.getSlots: mailSize={}, base={}", mailSize, base); //logs
        return base;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        LOGGER.debug("//logs MailboxItemHandler.getStackInSlot: slot={}", slot); //logs
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
        LOGGER.debug("//logs MailboxItemHandler.insertItem: slot={}, item={}, count={}, simulate={}", slot, stack.getItem(), stack.getCount(), simulate); //logs
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) {
            LOGGER.debug("//logs MailboxItemHandler.insertItem: item not valid for slot {}", slot); //logs
            return stack;
        }

        if (slot == MailboxBlockEntity.SLOT_MAIL && PackageItem.isPackage(stack) && !simulate) {
            LOGGER.debug("//logs MailboxItemHandler.insertItem: Create package inserted into SLOT_MAIL, adding envelope components"); //logs
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
        if (maxInsert <= 0) {
            LOGGER.debug("//logs MailboxItemHandler.insertItem: slot full (maxInsert={})", maxInsert); //logs
            return stack;
        }

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
        LOGGER.debug("//logs MailboxItemHandler.insertItem: inserted {}, remaining={}", toInsert, remaining.getCount()); //logs
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        LOGGER.debug("//logs MailboxItemHandler.extractItem: slot={}, amount={}, simulate={}", slot, amount, simulate); //logs
        int mailSize = mailbox.getAllMail().size();
        int inboxEnd = MailboxBlockEntity.INBOX_SLOT + mailSize;
        if (slot < MailboxBlockEntity.INBOX_SLOT || slot >= inboxEnd) {
            LOGGER.debug("//logs MailboxItemHandler.extractItem: cannot extract from slot {} (SLOT_MAIL/SLOT_FOOD blocked, or virtual slot)", slot); //logs
            return ItemStack.EMPTY;
        }

        ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;

        int toExtract = Math.min(amount, existing.getCount());
        ItemStack extracted = existing.copyWithCount(toExtract);

        if (!simulate) {
            mailbox.removeItem(slot, toExtract);
        }

        if (PackageConversion.isEnvelopePackage(extracted) || extracted.has(DataComponents.CONTAINER_LOOT)) {
            LOGGER.debug("//logs MailboxItemHandler.extractItem: converting Envelope package to Create package"); //logs
            extracted = PackageConversion.toCreatePackage(extracted);
        }

        LOGGER.debug("//logs MailboxItemHandler.extractItem: returning {}", extracted.getItem()); //logs
        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        int mailSize = mailbox.getAllMail().size();
        int inboxEnd = MailboxBlockEntity.INBOX_SLOT + mailSize;
        if (slot >= inboxEnd) {
            LOGGER.debug("//logs MailboxItemHandler.getSlotLimit: slot {} (virtual) limit=0", slot); //logs
            return 0;
        }
        int limit = slot < MailboxBlockEntity.INBOX_SLOT ? 64 : 1;
        LOGGER.debug("//logs MailboxItemHandler.getSlotLimit: slot={}, limit={}", slot, limit); //logs
        return limit;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        LOGGER.debug("//logs MailboxItemHandler.isItemValid: slot={}, item={}", slot, stack.getItem()); //logs
        if (stack.isEmpty()) return false;
        int mailSize = mailbox.getAllMail().size();
        int inboxEnd = MailboxBlockEntity.INBOX_SLOT + mailSize;
        if (slot >= inboxEnd) {
            LOGGER.debug("//logs MailboxItemHandler.isItemValid: virtual slot {} not valid for insertion", slot); //logs
            return false;
        }
        if (slot == MailboxBlockEntity.SLOT_FOOD) return stack.is(Envelope.Tags.Items.PIGEON_FOOD);
        if (slot == MailboxBlockEntity.SLOT_MAIL) {
            boolean sendable = mailbox.isSendable(stack);
            LOGGER.debug("//logs MailboxItemHandler.isItemValid: SLOT_MAIL isSendable={}", sendable); //logs
            return sendable;
        }
        return slot >= MailboxBlockEntity.INBOX_SLOT;
    }
}
