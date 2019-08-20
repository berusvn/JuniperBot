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
package ru.juniperbot.module.full;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.juniperbot.module.audio.AudioConfiguration;
import ru.juniperbot.module.mafia.MafiaConfiguration;
import ru.juniperbot.module.misc.MiscConfiguration;
import ru.juniperbot.module.ranking.RankingConfiguration;
import ru.juniperbot.module.steam.SteamConfiguration;
import ru.juniperbot.module.wikifur.WikiFurConfiguration;

@Import({
        AudioConfiguration.class,
        SteamConfiguration.class,
        WikiFurConfiguration.class,
        MiscConfiguration.class,
        RankingConfiguration.class,
        MafiaConfiguration.class
})
@Configuration
public class ModulesConfiguration {
}