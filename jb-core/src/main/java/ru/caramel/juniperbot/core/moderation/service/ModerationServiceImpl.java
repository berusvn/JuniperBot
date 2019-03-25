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
package ru.caramel.juniperbot.core.moderation.service;

import lombok.NonNull;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.managers.GuildController;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.caramel.juniperbot.core.audit.model.AuditActionBuilder;
import ru.caramel.juniperbot.core.audit.model.AuditActionType;
import ru.caramel.juniperbot.core.audit.service.ActionsHolderService;
import ru.caramel.juniperbot.core.common.persistence.LocalMember;
import ru.caramel.juniperbot.core.common.service.AbstractDomainServiceImpl;
import ru.caramel.juniperbot.core.common.service.MemberService;
import ru.caramel.juniperbot.core.event.service.ContextService;
import ru.caramel.juniperbot.core.jobs.UnMuteJob;
import ru.caramel.juniperbot.core.message.service.MessageService;
import ru.caramel.juniperbot.core.moderation.persistence.*;
import ru.caramel.juniperbot.core.utils.CommonUtils;
import ru.caramel.juniperbot.core.utils.DiscordUtils;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ru.caramel.juniperbot.core.audit.provider.MemberWarnAuditForwardProvider.*;

@Service
public class ModerationServiceImpl
        extends AbstractDomainServiceImpl<ModerationConfig, ModerationConfigRepository>
        implements ModerationService {

    private final static String MUTED_ROLE_NAME = "JB-MUTED";

    private final static String COLOR_ROLE_NAME = "JB-CLR-";

    @Autowired
    private MemberWarningRepository warningRepository;

    @Autowired
    private MuteStateRepository muteStateRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ContextService contextService;

    @Autowired
    private ActionsHolderService actionsHolderService;

    @Autowired
    private SchedulerFactoryBean schedulerFactoryBean;

    public ModerationServiceImpl(@Autowired ModerationConfigRepository repository) {
        super(repository, true);
    }

    @Override
    protected ModerationConfig createNew(long guildId) {
        ModerationConfig config = new ModerationConfig(guildId);
        config.setCoolDownIgnored(true);
        return config;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isModerator(Member member) {
        if (member == null) {
            return false;
        }
        if (member.hasPermission(Permission.ADMINISTRATOR) || member.isOwner()) {
            return true;
        }
        ModerationConfig config = get(member.getGuild());
        return config != null && CollectionUtils.isNotEmpty(config.getRoles())
                && member.getRoles().stream().anyMatch(e -> config.getRoles().contains(e.getIdLong()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPublicColor(long guildId) {
        ModerationConfig config = getByGuildId(guildId);
        return config != null && config.isPublicColors();
    }

    @Override
    public boolean setColor(Member member, String color) {
        Role role = null;
        Guild guild = member.getGuild();
        Member self = guild.getSelfMember();

        GuildController controller = member.getGuild().getController();

        if (StringUtils.isNotEmpty(color)) {
            String roleName = COLOR_ROLE_NAME + color;
            List<Role> roles = member.getGuild().getRolesByName(roleName, false);
            role = roles.stream().filter(self::canInteract).findFirst().orElse(null);
            if (role == null) {
                role = controller
                        .createRole()
                        .setColor(CommonUtils.hex2Rgb(color))
                        .setMentionable(false)
                        .setName(roleName)
                        .complete();

                Role highestRole = DiscordUtils.getHighestRole(self, Permission.MANAGE_ROLES);
                if (highestRole != null) {
                    controller.modifyRolePositions()
                            .selectPosition(role)
                            .moveTo(highestRole.getPosition() - 1)
                            .complete();
                }
            }

            if (!self.canInteract(role)) {
                return false;
            }
        }

        if (role == null || !member.getRoles().contains(role)) {
            List<Role> roleList = member.getRoles().stream()
                    .filter(e -> e.getName().startsWith(COLOR_ROLE_NAME))
                    .filter(self::canInteract)
                    .collect(Collectors.toList());
            if (role != null) {
                if (CollectionUtils.isEmpty(roleList)) {
                    controller.addRolesToMember(member, role).complete();
                } else {
                    controller.modifyMemberRoles(member, Collections.singleton(role), roleList).complete();
                }
            } else {
                controller.removeRolesFromMember(member, roleList).complete();
            }
        }
        // remove unused color roles
        Set<Role> userRoles = new LinkedHashSet<>();
        if (role != null) {
            userRoles.add(role);
        }
        guild.getMembers().forEach(m -> userRoles.addAll(m.getRoles()));
        guild.getRoles().stream()
                .filter(e -> e.getName().startsWith(COLOR_ROLE_NAME) && !userRoles.contains(e) && self.canInteract(e))
                .forEach(e -> e.delete().queue());
        return true;
    }

    @Override
    @Transactional
    public Role getMutedRole(Guild guild) {
        ModerationConfig moderationConfig = getOrCreate(guild);

        Role role = null;
        if (moderationConfig.getMutedRoleId() != null) {
            role = guild.getRoleById(moderationConfig.getMutedRoleId());
        }

        if (role == null) {
            List<Role> mutedRoles = guild.getRolesByName(MUTED_ROLE_NAME, true);
            role = CollectionUtils.isNotEmpty(mutedRoles) ? mutedRoles.get(0) : null;
        }

        if (role == null || !guild.getSelfMember().canInteract(role)) {
            role = guild.getController()
                    .createRole()
                    .setColor(Color.GRAY)
                    .setMentionable(false)
                    .setName(MUTED_ROLE_NAME)
                    .complete();
        }

        if (!Objects.equals(moderationConfig.getMutedRoleId(), role.getIdLong())) {
            moderationConfig.setMutedRoleId(role.getIdLong());
            save(moderationConfig);
        }

        for (TextChannel channel : guild.getTextChannels()) {
            checkPermission(channel, role, PermissionMode.DENY, Permission.MESSAGE_WRITE);
        }
        for (VoiceChannel channel : guild.getVoiceChannels()) {
            checkPermission(channel, role, PermissionMode.DENY, Permission.VOICE_SPEAK);
        }
        return role;
    }

    private enum PermissionMode {
        DENY, ALLOW, UNCHECKED
    }

    private static void checkPermission(Channel channel, Role role, PermissionMode mode, Permission permission) {
        PermissionOverride override = channel.getPermissionOverride(role);
        if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
            return;
        }
        if (override == null) {
            switch (mode) {
                case DENY:
                    channel.createPermissionOverride(role).setDeny(permission).queue();
                    break;
                case ALLOW:
                    channel.createPermissionOverride(role).setAllow(permission).queue();
                    break;
                case UNCHECKED:
                    // do nothing
                    break;
            }
        } else {
            switch (mode) {
                case DENY:
                    if (!override.getDenied().contains(permission)) {
                        override.getManager().deny(permission).queue();
                    }
                    break;
                case UNCHECKED:
                case ALLOW:
                    if (!override.getAllowed().contains(permission)) {
                        override.getManager().clear(permission).queue();
                    }
                    break;
            }
        }
    }

    @Override
    public boolean mute(Member author, TextChannel channel, Member member, boolean global, Integer duration, String reason) {
        return mute(author, channel, member, global, duration, reason, true, false);
    }

    private boolean mute(Member author, TextChannel channel, Member member, boolean global, Integer duration, String reason, boolean log, boolean stateless) {

        AuditActionBuilder actionBuilder = log ? getAuditService()
                .log(author.getGuild(), AuditActionType.MEMBER_MUTE)
                .withUser(author)
                .withTargetUser(member)
                .withChannel(global ? null : channel)
                .withAttribute(REASON_ATTR, reason)
                .withAttribute(DURATION_ATTR, duration)
                .withAttribute(GLOBAL_ATTR, global) : null;

        Consumer<Boolean> schedule = g -> {
            contextService.inTransaction(() -> {
                if (!stateless) {
                    if (duration != null) {
                        scheduleUnMute(g, channel, member, duration);
                    }
                    storeState(g, channel, member, duration, reason);
                }
                if (actionBuilder != null) {
                    actionBuilder.save();
                }
            });
        };

        if (global) {
            Guild guild = member.getGuild();
            Role mutedRole = getMutedRole(guild);
            if (!member.getRoles().contains(mutedRole)) {
                guild.getController()
                        .addRolesToMember(member, mutedRole)
                        .queue(e -> schedule.accept(true));
                guild.getController()
                        .setMute(member, true)
                        .queue();
                return true;
            }
        } else {
            PermissionOverride override = channel.getPermissionOverride(member);
            if (override != null && override.getDenied().contains(Permission.MESSAGE_WRITE)) {
                return false;
            }
            if (override == null) {
                channel.createPermissionOverride(member)
                        .setDeny(Permission.MESSAGE_WRITE)
                        .queue(e -> schedule.accept(false));
            } else {
                override.getManager().deny(Permission.MESSAGE_WRITE).queue(e -> schedule.accept(false));
            }
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public boolean unmute(Member author, TextChannel channel, Member member) {
        Guild guild = member.getGuild();
        boolean result = false;
        Role mutedRole = getMutedRole(guild);
        if (member.getRoles().contains(mutedRole)) {
            guild.getController()
                    .removeRolesFromMember(member, mutedRole)
                    .queue();
            guild.getController()
                    .setMute(member, false)
                    .queue();
            result = true;
        }
        if (channel != null) {
            PermissionOverride override = channel.getPermissionOverride(member);
            if (override != null) {
                override.delete().queue();
                result = true;
            }
        }
        removeUnMuteSchedule(member, channel);
        if (result) {
            getAuditService()
                    .log(guild, AuditActionType.MEMBER_UNMUTE)
                    .withUser(author)
                    .withTargetUser(member)
                    .withChannel(channel)
                    .save();
        }
        return result;
    }

    @Override
    @Transactional
    @Async
    public void refreshMute(Member member) {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();
        try {
            List<MuteState> muteStates = muteStateRepository.findAllByMember(member);
            if (CollectionUtils.isNotEmpty(muteStates)) {
                muteStates.stream().filter(e -> !processState(member, e)).forEach(muteStateRepository::delete);
                return;
            }

            JobKey key = UnMuteJob.getKey(member);
            if (!scheduler.checkExists(key)) {
                return;
            }
            JobDetail detail = scheduler.getJobDetail(key);
            if (detail == null) {
                return;
            }
            JobDataMap data = detail.getJobDataMap();
            boolean global = data.getBoolean(UnMuteJob.ATTR_GLOBAL_ID);
            String channelId = data.getString(UnMuteJob.ATTR_CHANNEL_ID);
            TextChannel textChannel = channelId != null ? member.getGuild().getTextChannelById(channelId) : null;
            if (global || textChannel != null) {
                mute(null, textChannel, member, global, null, null, false, false);
            }
        } catch (SchedulerException e) {
            // fall down, we don't care
        }
    }

    private void scheduleUnMute(boolean global, TextChannel channel, Member member, int duration) {
        try {
            removeUnMuteSchedule(member, channel);
            JobDetail job = UnMuteJob.createDetails(global, channel, member);
            Trigger trigger = TriggerBuilder
                    .newTrigger()
                    .startAt(DateTime.now().plusMinutes(duration).toDate())
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                    .build();
            schedulerFactoryBean.getScheduler().scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    private void storeState(boolean global, TextChannel channel, Member member, Integer duration, String reason) {
        MuteState state = new MuteState();
        state.setGlobal(global);
        state.setUserId(member.getUser().getId());
        state.setGuildId(member.getGuild().getIdLong());
        DateTime dateTime = DateTime.now();
        if (duration != null) {
            dateTime = dateTime.plusMinutes(duration);
        } else {
            dateTime = dateTime.plusYears(100);
        }
        state.setExpire(dateTime.toDate());
        state.setReason(reason);
        if (channel != null) {
            state.setChannelId(channel.getId());
        }
        muteStateRepository.save(state);
    }

    private boolean processState(Member member, MuteState muteState) {
        DateTime now = DateTime.now();
        DateTime expire = muteState.getExpire() != null ? new DateTime(muteState.getExpire()) : null;
        if (expire != null && now.isAfter(expire)) {
            return false;
        }

        TextChannel textChannel = muteState.getChannelId() != null ? member.getGuild().getTextChannelById(muteState.getChannelId()) : null;
        if (!muteState.isGlobal() && textChannel == null) {
            return false;
        }

        Integer duration = expire != null ? Minutes.minutesBetween(expire, now).getMinutes() : null;
        mute(null, textChannel, member, muteState.isGlobal(), duration, muteState.getReason(), false, true);
        return true;
    }

    private void removeUnMuteSchedule(Member member, TextChannel channel) {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();
        try {
            JobKey key = UnMuteJob.getKey(member);
            if (scheduler.checkExists(key)) {
                scheduler.deleteJob(key);
                muteStateRepository.deleteByMember(member);
            }
            if (channel != null) {
                key = UnMuteJob.getKey(member, channel);
                if (scheduler.checkExists(key)) {
                    scheduler.deleteJob(key);
                }
                muteStateRepository.deleteByMember(member, channel.getId()); // remove it even if job non exists
            }
        } catch (SchedulerException e) {
            // fall down, we don't care
        }
    }

    @Override
    @Transactional
    public boolean kick(Member author, Member member) {
        return kick(author, member, null);
    }

    @Override
    @Transactional
    public boolean kick(Member author, Member member, final String reason) {
        Member self = member.getGuild().getSelfMember();
        if (self.hasPermission(Permission.KICK_MEMBERS) && self.canInteract(member)) {

            AuditActionBuilder actionBuilder = getAuditService()
                    .log(self.getGuild(), AuditActionType.MEMBER_KICK)
                    .withUser(author)
                    .withTargetUser(member)
                    .withAttribute(REASON_ATTR, reason);

            notifyUserAction(e -> {
                actionBuilder.save();
                actionsHolderService.setLeaveNotified(e.getGuild().getIdLong(), e.getUser().getIdLong());
                e.getGuild().getController().kick(e, reason).queue();
            }, member, "discord.command.mod.action.message.kick", reason);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public boolean ban(Member author, Member member) {
        return ban(author, member, null);
    }

    @Override
    @Transactional
    public boolean ban(Member author, Member member, String reason) {
        return ban(author, member, 0, reason);
    }

    @Override
    @Transactional
    public boolean ban(Member author, Member member, int delDays, final String reason) {
        Member self = member.getGuild().getSelfMember();
        if (self.hasPermission(Permission.BAN_MEMBERS) && self.canInteract(member)) {

            AuditActionBuilder actionBuilder = getAuditService()
                    .log(self.getGuild(), AuditActionType.MEMBER_BAN)
                    .withUser(author)
                    .withTargetUser(member)
                    .withAttribute(REASON_ATTR, reason);

            notifyUserAction(e -> {
                actionBuilder.save();
                e.getGuild().getController().ban(e, delDays, reason).queue();
            }, member, "discord.command.mod.action.message.ban", reason);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public List<MemberWarning> getWarnings(Member member) {
        LocalMember localMember = memberService.getOrCreate(member);
        return warningRepository.findActiveByViolator(member.getGuild().getIdLong(), localMember);
    }

    @Override
    @Transactional
    public boolean warn(Member author, Member member) {
        return warn(author, member, null);
    }

    @Override
    @Transactional
    public long warnCount(Member member) {
        LocalMember memberLocal = memberService.getOrCreate(member);
        return warningRepository.countActiveByViolator(member.getGuild().getIdLong(), memberLocal);
    }

    @Override
    @Transactional
    public boolean warn(Member author, Member member, String reason) {
        long guildId = member.getGuild().getIdLong();
        ModerationConfig moderationConfig = getOrCreate(member.getGuild());
        LocalMember authorLocal = memberService.getOrCreate(author);
        LocalMember memberLocal = memberService.getOrCreate(member);

        long count = warningRepository.countActiveByViolator(guildId, memberLocal);
        boolean exceed = count >= moderationConfig.getMaxWarnings() - 1;
        MemberWarning warning = new MemberWarning(guildId, authorLocal, memberLocal, reason);

        getAuditService().log(guildId, AuditActionType.MEMBER_WARN)
                .withUser(author)
                .withTargetUser(memberLocal)
                .withAttribute(REASON_ATTR, reason)
                .withAttribute(COUNT_ATTR, count + 1)
                .withAttribute(MAX_ATTR, moderationConfig.getMaxWarnings())
                .save();

        if (exceed) {
            reason = messageService.getMessage("discord.command.mod.warn.exceeded", count + 1);
            boolean success = true;
            switch (moderationConfig.getWarnExceedAction()) {
                case BAN:
                    success = ban(author, member, reason);
                    break;
                case KICK:
                    success = kick(author, member, reason);
                    break;
                case MUTE:
                    mute(author, null, member, true, moderationConfig.getMuteCount(), reason, true, false);
                    break;
            }
            if (success) {
                warningRepository.flushWarnings(guildId, memberLocal);
                warning.setActive(false);
            }
        } else {
            notifyUserAction(e -> {}, member, "discord.command.mod.action.message.warn", reason, count + 1,
                    moderationConfig.getMaxWarnings());
        }
        warningRepository.save(warning);
        return exceed;
    }

    @Override
    @Transactional
    public void removeWarn(@NonNull MemberWarning warning) {
        warning.setActive(false);
        warningRepository.save(warning);
    }

    @Override
    @Transactional
    public void clearState(long guildId, String userId, String channelId) {
        muteStateRepository.deleteByGuildIdAndUserIdAndChannelId(guildId, userId, channelId);
    }

    private void notifyUserAction(Consumer<Member> consumer, Member member, String code, String reason, Object... objects) {
        if (StringUtils.isEmpty(reason)) {
            code += ".noReason";
        }
        if (member.getUser().isBot()) {
            return; // do not notify bots
        }
        String finalCode = code;
        try {
            Object[] args = new Object[]{member.getGuild().getName()};
            if (ArrayUtils.isNotEmpty(objects)) {
                args = ArrayUtils.addAll(args, objects);
            }
            if (StringUtils.isNotEmpty(reason)) {
                args = ArrayUtils.add(args, reason);
            }
            String message = messageService.getMessage(finalCode, args);

            JDA jda = member.getGuild().getJDA();
            long guildId = member.getGuild().getIdLong();
            long userId = member.getUser().getIdLong();

            member.getUser().openPrivateChannel().queue(e -> {
                contextService.withContext(guildId, () -> {
                    e.sendMessage(message).queue(t -> {
                        Guild guild = jda.getGuildById(guildId);
                        consumer.accept(guild != null ? guild.getMemberById(userId) : null);
                    }, t -> {
                        Guild guild = jda.getGuildById(guildId);
                        consumer.accept(guild != null ? guild.getMemberById(userId) : null);
                    });
                });
            }, t -> {
                Guild guild = jda.getGuildById(guildId);
                consumer.accept(guild != null ? guild.getMemberById(userId) : null);
            });
        } catch (Exception e) {
            consumer.accept(member);
        }
    }

    @Override
    protected Class<ModerationConfig> getDomainClass() {
        return ModerationConfig.class;
    }
}