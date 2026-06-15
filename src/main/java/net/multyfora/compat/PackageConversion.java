package net.multyfora.compat;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import io.github.mortuusars.envelope.world.mail.address.Address;
import io.github.mortuusars.envelope.world.mail.address.type.CustomAddress;
import io.github.mortuusars.envelope.world.mail.address.type.ServiceAddress;
import io.github.mortuusars.envelope.world.mail.service.ServiceAddressDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;

public class PackageConversion {

    private static final Logger LOGGER = LogUtils.getLogger(); //logs

    public static boolean isEnvelopePackage(ItemStack stack) {
        boolean result = stack.has(Envelope.DataComponents.PACKAGE_CONTENTS);
        LOGGER.debug("//logs isEnvelopePackage({}): {}", stack.getItem(), result); //logs
        return result;
    }

    public static boolean isCreatePackage(ItemStack stack) {
        boolean result = PackageItem.isPackage(stack);
        LOGGER.debug("//logs isCreatePackage({}): {}", stack.getItem(), result); //logs
        return result;
    }

    public static boolean isAnyPackage(ItemStack stack) {
        boolean result = isEnvelopePackage(stack) || isCreatePackage(stack);
        LOGGER.debug("//logs isAnyPackage({}): {}", stack.getItem(), result); //logs
        return result;
    }

    public static ItemStack toCreatePackage(ItemStack envelopePackage) {
        LOGGER.debug("//logs toCreatePackage: input={}, hasPaybackSubj={}, isCreatePkg={}, isEnvPkg={}, hasLoot={}",
            envelopePackage.getItem(),
            envelopePackage.has(Envelope.DataComponents.PAYBACK_SUBJECT),
            isCreatePackage(envelopePackage),
            isEnvelopePackage(envelopePackage),
            envelopePackage.has(DataComponents.CONTAINER_LOOT)); //logs

        if (isCreatePackage(envelopePackage)) {
            LOGGER.debug("//logs toCreatePackage: already Create package, returning as-is"); //logs
            return envelopePackage;
        }
        if (!isEnvelopePackage(envelopePackage) && !envelopePackage.has(DataComponents.CONTAINER_LOOT)) {
            LOGGER.debug("//logs toCreatePackage: not an Envelope package and no loot, returning as-is"); //logs
            return envelopePackage;
        }
        if (envelopePackage.has(Envelope.DataComponents.PAYBACK_SUBJECT)) {
            LOGGER.debug("//logs toCreatePackage: has PAYBACK_SUBJECT, preserving as-is for Mail Service"); //logs
            return envelopePackage;
        }

        ItemStack workingStack = envelopePackage.copy();

        if (workingStack.has(DataComponents.CONTAINER_LOOT)) {
            LOGGER.debug("//logs toCreatePackage: resolving loot table"); //logs
            resolveLootTable(workingStack);
        }

        PackageContents contents = workingStack.get(Envelope.DataComponents.PACKAGE_CONTENTS);
        List<ItemStack> items = contents != null ? contents.copyItems() : List.of();
        LOGGER.debug("//logs toCreatePackage: extracted {} items from package", items.size()); //logs

        ItemStack createPackage = PackageItem.containing(items);

        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.MAIL_RECIPIENT);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.MAIL_SENDER);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.MAIL_ID);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.MAIL_DELIVERY_LOG);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.MAIL_PAYBACK_REQUEST);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.PAYBACK_SUBJECT);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.PACKAGE_CONTENTS);
        copyEnvelopeComponent(workingStack, createPackage, Envelope.DataComponents.PACKAGE_EXPERIENCE);

        if (workingStack.has(Envelope.DataComponents.MAIL_RECIPIENT)) {
            var address = workingStack.get(Envelope.DataComponents.MAIL_RECIPIENT);
            PackageItem.addAddress(createPackage, address.getString());
            LOGGER.debug("//logs toCreatePackage: set Create address to {}", address.getString()); //logs
        }

        LOGGER.debug("//logs toCreatePackage: returning Create package"); //logs
        return createPackage;
    }

    public static void syncAddresses(ItemStack stack, Level level) {
        if (!PackageItem.isPackage(stack)) {
            LOGGER.debug("//logs syncAddresses: not a Create package, skipping"); //logs
            return;
        }

        boolean hasCreateAddr = stack.has(AllDataComponents.PACKAGE_ADDRESS)
            && !stack.getOrDefault(AllDataComponents.PACKAGE_ADDRESS, "").isEmpty();
        boolean hasEnvAddr = stack.has(Envelope.DataComponents.MAIL_RECIPIENT);

        LOGGER.debug("//logs syncAddresses: hasCreateAddr={}, hasEnvAddr={}", hasCreateAddr, hasEnvAddr); //logs

        if (hasCreateAddr && !hasEnvAddr) {
            String addr = stack.get(AllDataComponents.PACKAGE_ADDRESS);
            LOGGER.debug("//logs syncAddresses: resolving Create address '{}' to Envelope address", addr); //logs
            Address resolved = resolveAddress(addr, level);
            LOGGER.debug("//logs syncAddresses: resolved to {}", resolved.getString()); //logs
            stack.set(Envelope.DataComponents.MAIL_RECIPIENT, resolved);
        } else if (hasEnvAddr && !hasCreateAddr) {
            var addr = stack.get(Envelope.DataComponents.MAIL_RECIPIENT);
            LOGGER.debug("//logs syncAddresses: syncing Envelope address '{}' back to Create", addr.getString()); //logs
            PackageItem.addAddress(stack, addr.getString());
        }
    }

    private static Address resolveAddress(String addr, Level level) {
        LOGGER.debug("//logs resolveAddress: resolving '{}'", addr); //logs
        String[] parts = addr.split(",");
        if (parts.length == 3 && level != null) {
            try {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox
                    && mailbox.getAddress() != null) {
                    LOGGER.debug("//logs resolveAddress: found mailbox at {},{}", pos.toShortString(), mailbox.getAddress().getString()); //logs
                    return mailbox.getAddress();
                }
            } catch (NumberFormatException ignored) {
                LOGGER.debug("//logs resolveAddress: not a position string"); //logs
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            ServiceAddress service = resolveService(serverLevel, addr);
            if (service != null) {
                LOGGER.debug("//logs resolveAddress: resolved as service address"); //logs
                return service;
            }
        }

        LOGGER.debug("//logs resolveAddress: falling back to CustomAddress"); //logs
        return new CustomAddress(Component.literal(addr));
    }

    private static ServiceAddress resolveService(ServerLevel level, String name) {
        LOGGER.debug("//logs resolveService: looking up service '{}'", name); //logs
        RegistryAccess registries = level.registryAccess();
        String normalised = name.toLowerCase(Locale.ROOT).replace(" ", "_");

        ResourceLocation loc = Envelope.resource(normalised);
        ResourceKey<ServiceAddressDefinition> key =
            ResourceKey.create(Envelope.Registries.SERVICE_ADDRESS_DEFINITION, loc);
        var result = ServiceAddress.get(registries, key);
        if (result.isPresent()) {
            LOGGER.debug("//logs resolveService: found by direct key lookup: {}", normalised); //logs
            return result.get();
        }

        var registry = registries.registryOrThrow(Envelope.Registries.SERVICE_ADDRESS_DEFINITION);
        for (var entry : registry.entrySet()) {
            ResourceLocation entryId = entry.getKey().location();
            if (entryId.getPath().equals(normalised)) {
                LOGGER.debug("//logs resolveService: found by path match: {}", entryId); //logs
                return new ServiceAddress(registry.getHolderOrThrow(entry.getKey()));
            }
        }

        for (var entry : registry.entrySet()) {
            String defName = entry.getValue().name().getString();
            if (defName.equalsIgnoreCase(name)) {
                LOGGER.debug("//logs resolveService: found by display name match: {}", defName); //logs
                return new ServiceAddress(registry.getHolderOrThrow(entry.getKey()));
            }
            String defKey = defName.toLowerCase(Locale.ROOT).replace(" ", "_");
            if (defKey.equals(normalised)) {
                LOGGER.debug("//logs resolveService: found by normalised display name match: {}", defKey); //logs
                return new ServiceAddress(registry.getHolderOrThrow(entry.getKey()));
            }
        }

        LOGGER.debug("//logs resolveService: no match found for '{}'", name); //logs
        return null;
    }

    private static void resolveLootTable(ItemStack stack) {
        SeededContainerLoot containerLoot = stack.get(DataComponents.CONTAINER_LOOT);
        if (containerLoot == null) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel serverLevel = server.overworld();
        LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(containerLoot.lootTable());
        LootParams params = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
            .create(LootContextParamSets.CHEST);
        SimpleContainer tempContainer = new SimpleContainer(6);
        lootTable.fill(tempContainer, params, containerLoot.seed());
        stack.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(tempContainer));
        stack.remove(DataComponents.CONTAINER_LOOT);
    }

    private static <T> void copyEnvelopeComponent(ItemStack from, ItemStack to,
                                                   net.minecraft.core.component.DataComponentType<T> component) {
        if (from.has(component))
            to.set(component, from.get(component));
    }
}
