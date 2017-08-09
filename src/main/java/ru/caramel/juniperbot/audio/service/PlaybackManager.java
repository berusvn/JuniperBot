package ru.caramel.juniperbot.audio.service;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PlaybackManager {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AudioPlayerManager playerManager;

    @Autowired
    private MessageManager messageManager;

    private Map<Long, GuildPlaybackManager> musicManagers = new HashMap<>();

    private synchronized GuildPlaybackManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        return musicManagers.computeIfAbsent(guildId,
                e -> applicationContext.getBean(GuildPlaybackManager.class));
    }

    public void loadAndPlay(final TextChannel channel, final User requestedBy, final String trackUrl) {
        GuildPlaybackManager musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                musicManager.play(track, channel, requestedBy);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();
                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }
                musicManager.play(firstTrack, channel, requestedBy);
            }

            @Override
            public void noMatches() {
                messageManager.onNoMatches(channel, trackUrl);
            }

            @Override
            public void loadFailed(FriendlyException e) {
                messageManager.onError(channel, e);
            }
        });
    }

    public void skipTrack(TextChannel channel) {
        GuildPlaybackManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.nextTrack();
    }
}