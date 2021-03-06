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
package ru.juniperbot.common.model.exception;

import lombok.Getter;

public class DiscordException extends Exception {

    private static final long serialVersionUID = 4621225391411561750L;

    @Getter
    private final Object[] args;

    public DiscordException() {
        args = null;
    }

    public DiscordException(String message, Object... args) {
        this(message, null, args);
    }

    public DiscordException(String message, Throwable cause, Object... args) {
        super(message, cause);
        this.args = args;
    }

    public DiscordException(String message, Throwable cause,
                            boolean enableSuppression,
                            boolean writableStackTrace, Object... args) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.args = args;
    }
}
