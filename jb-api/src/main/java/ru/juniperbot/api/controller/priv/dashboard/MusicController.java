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
package ru.juniperbot.api.controller.priv.dashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.juniperbot.api.common.aspect.GuildId;
import ru.juniperbot.api.controller.base.BaseRestController;
import ru.juniperbot.api.dao.MusicDao;
import ru.juniperbot.api.dto.config.MusicConfigDto;

@RestController
public class MusicController extends BaseRestController {

    @Autowired
    private MusicDao musicDao;

    @RequestMapping(value = "/music/{guildId}", method = RequestMethod.GET)
    @ResponseBody
    public MusicConfigDto load(@GuildId @PathVariable long guildId) {
        return musicDao.getConfig(guildId);
    }

    @RequestMapping(value = "/music/{guildId}", method = RequestMethod.POST)
    public void save(@GuildId @PathVariable long guildId,
                     @RequestBody @Validated MusicConfigDto dto) {
        musicDao.saveConfig(dto, guildId);
    }
}
