/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.CreatorUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.stream.Collectors;

public class ModulesCommand extends Command {
    public ModulesCommand() {
        super("modules", "Displays a list of all modules.", "features");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            boolean isCreator = CreatorUtils.isCreator();
            long visibleCount = Modules.get().getAll().stream().filter(module -> !module.creatorOnly || isCreator).count();

            ChatUtils.info("--- Modules ((highlight)%d(default)) ---", visibleCount);

            Modules.loopCategories().forEach(category -> {
                List<Module> visibleModules = Modules.get().getGroup(category).stream()
                    .filter(module -> !module.creatorOnly || isCreator)
                    .collect(Collectors.toList());

                if (visibleModules.isEmpty()) return;

                MutableText categoryMessage = Text.literal("");
                visibleModules.forEach(module -> categoryMessage.append(getModuleText(module, visibleModules)));
                ChatUtils.sendMsg(category.name, categoryMessage);
            });

            return SINGLE_SUCCESS;
        });
    }

    private MutableText getModuleText(Module module, List<Module> visibleModules) {
        // Hover tooltip
        MutableText tooltip = Text.literal("");

        tooltip.append(Text.literal(module.title).formatted(Formatting.BLUE, Formatting.BOLD)).append("\n");
        tooltip.append(Text.literal(module.name).formatted(Formatting.GRAY)).append("\n\n");
        tooltip.append(Text.literal(module.description).formatted(Formatting.WHITE));

        MutableText finalModule = Text.literal(module.title);
        if (!module.isActive()) finalModule.formatted(Formatting.GRAY);
        if (!module.equals(visibleModules.get(visibleModules.size() - 1))) finalModule.append(Text.literal(", ").formatted(Formatting.GRAY));
        finalModule.setStyle(finalModule.getStyle().withHoverEvent(new HoverEvent.ShowText(tooltip)));

        return finalModule;
    }

}
