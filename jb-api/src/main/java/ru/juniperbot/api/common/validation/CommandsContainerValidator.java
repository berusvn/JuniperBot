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
package ru.juniperbot.api.common.validation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import ru.juniperbot.api.dto.config.CustomCommandDto;
import ru.juniperbot.common.model.command.CommandInfo;
import ru.juniperbot.common.service.GatewayService;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CommandsContainerValidator implements Validator {

    @Autowired
    private SpringValidatorAdapter validatorAdapter;

    @Autowired
    private GatewayService gatewayService;

    @Override
    public boolean supports(Class<?> clazz) {
        return CustomCommandDto.class.equals(clazz) || Collection.class.isAssignableFrom(clazz);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void validate(Object target, Errors errors) {
        validatorAdapter.validate(target, errors);
        if (target instanceof List) {
            List<CommandInfo> commandInfoList = gatewayService.getCommandList();
            List<CustomCommandDto> container = (List<CustomCommandDto>) target;
            if (CollectionUtils.isNotEmpty(container)) {
                Set<String> existingKey = new HashSet<>();
                for (int i = 0; i < container.size(); i++) {
                    String path = "commands[" + i + "].";
                    CustomCommandDto command = container.get(i);
                    String key = command.getKey();
                    if (StringUtils.isNotEmpty(key)) {
                        if (!existingKey.add(key)) {
                            errors.rejectValue(path + "key", "validation.commands.key.unique.message");
                        } else if (commandInfoList.stream().anyMatch(e -> key.equals(e.getKey()))) {
                            errors.rejectValue(path + "key", "validation.commands.key.service.message");
                        }
                    }
                }
            }
        }
    }
}
