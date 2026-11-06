package com.github.tadaooq123.bdm.mixin;

import com.github.tadaooq123.bdm.BdmMod;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Запрет /msg, /me и т.п. для участников во время игры. */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void bdm$blockCommands(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        if (BdmMod.blockCommand(parse.getContext().getSource(), command)) {
            ci.cancel();
        }
    }
}
