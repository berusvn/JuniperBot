/*
 * This file is part of JuniperBot.
 *
 * JuniperBot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * JuniperBot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with JuniperBot. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.juniperbot.module.audio.commands.timing;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import ru.juniperbot.common.worker.command.model.BotContext;
import ru.juniperbot.common.worker.command.model.DiscordCommand;
import ru.juniperbot.module.audio.commands.AudioCommand;

@DiscordCommand(
        key = "discord.command.restart.key",
        description = "discord.command.restart.desc",
        group = "discord.command.group.music",
        priority = 112)
public class RestartCommand extends AudioCommand {
    @Override
    protected boolean doInternal(GuildMessageReceivedEvent message, BotContext context, String content) {
        if (!playerService.get(message.getGuild()).seek(0)) {
            messageManager.onMessage(message.getChannel(), "discord.command.audio.restart.denied");
            return fail(message);
        }
        return ok(message, "discord.command.audio.restart.done");
    }
}
