package com.recipedumper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

public class DumpRecipesCommand {

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("dumprecipes")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("modid", StringArgumentType.word())
                .executes(context -> {
                    String modid = StringArgumentType.getString(context, "modid");
                    context.getSource().sendSuccess(() -> Component.literal("Dumping recipes for mod: " + modid + "..."), true);
                    // Recipe dumping logic will be added here
                    return 1;
                })
            )
        );
    }
}