package net.multyfora.compat.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.ItemHelper;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(PackageContents.class)
public class PackageContentsMixin {

    @ModifyReturnValue(method = "of", at = @At("RETURN"))
    private static PackageContents checkForCreate(PackageContents original, @Local(argsOnly = true) ItemStack stack) {
        if (PackageItem.isPackage(stack)) {
            ItemStackHandler contents = PackageItem.getContents(stack);
            List<ItemStack> contentsAsList = ItemHelper.getNonEmptyStacks(contents);
            return new PackageContents(contentsAsList);
        }
        return original;
    }
}
