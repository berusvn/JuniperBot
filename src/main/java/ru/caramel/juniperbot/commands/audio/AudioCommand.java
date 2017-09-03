package ru.caramel.juniperbot.commands.audio;

import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import ru.caramel.juniperbot.audio.service.PlayerService;
import ru.caramel.juniperbot.audio.service.AudioMessageManager;
import ru.caramel.juniperbot.commands.Command;
import ru.caramel.juniperbot.commands.model.BotContext;
import ru.caramel.juniperbot.commands.model.ValidationException;
import ru.caramel.juniperbot.integration.discord.model.DiscordException;
import ru.caramel.juniperbot.service.MessageService;

public abstract class AudioCommand implements Command {

    @Autowired
    protected PlayerService playerService;

    @Autowired
    protected AudioMessageManager messageManager;

    @Autowired
    protected MessageService messageService;

    protected abstract boolean doInternal(MessageReceivedEvent message, BotContext context, String content) throws DiscordException;

    @Override
    public boolean doCommand(MessageReceivedEvent message, BotContext context, String content) throws DiscordException {
        if (isChannelRestricted() && !playerService.isInChannel(message.getMember())) {
            VoiceChannel channel = playerService.getChannel(message.getMember());
            throw new ValidationException("discord.command.audio.joinChannel", channel.getName());
        }
        doInternal(message, context, content);
        return false;
    }

    protected boolean isChannelRestricted() {
        return true;
    }
}