/*
 * This file is part of JuniperBotJ.
 *
 * JuniperBotJ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * JuniperBotJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with JuniperBotJ. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.caramel.juniperbot.module.info.commands;

import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.caramel.juniperbot.core.model.BotContext;
import ru.caramel.juniperbot.core.model.DiscordCommand;
import ru.caramel.juniperbot.core.model.enums.CommandSource;
import ru.caramel.juniperbot.core.persistence.entity.LocalMember;
import ru.caramel.juniperbot.core.service.MemberService;
import ru.caramel.juniperbot.core.utils.CommonUtils;
import ru.caramel.juniperbot.module.info.persistence.entity.MemberBio;
import ru.caramel.juniperbot.module.info.persistence.repository.MemberBioRepository;

@DiscordCommand(key = "discord.command.bio.key",
        description = "discord.command.bio.desc",
        source = CommandSource.GUILD,
        priority = 4)
public class BioCommand extends InfoCommand {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberBioRepository bioRepository;

    @Override
    public boolean doCommand(MessageReceivedEvent message, BotContext context, String query) {
        LocalMember localMember = memberService.getOrCreate(message.getMember());
        MemberBio bio = bioRepository.findByMember(localMember);
        if (bio == null) {
            bio = new MemberBio();
            bio.setMember(localMember);
        }
        bio.setBio(StringUtils.isNotEmpty(query) ? CommonUtils.trimTo(query.trim(), MessageEmbed.TEXT_MAX_LENGTH) : null);
        bioRepository.save(bio);
        return ok(message);
    }
}
