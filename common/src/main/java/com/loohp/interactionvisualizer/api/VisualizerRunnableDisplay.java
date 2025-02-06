/*
 * This file is part of InteractionVisualizer.
 *
 * Copyright (C) 2022. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2022. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.loohp.interactionvisualizer.api;

import com.loohp.interactionvisualizer.managers.TaskManager;
import com.loohp.interactionvisualizer.objectholders.EntryKey;
import org.bukkit.Bukkit;
import org.tjdev.util.tjpluginutil.spigot.scheduler.universalscheduler.scheduling.tasks.MyScheduledTask;

import java.util.HashSet;
import java.util.Set;

/**
 * This class is used for Displays which are shown when something is active by itself like a furnace cooking
 */
public abstract class VisualizerRunnableDisplay implements VisualizerDisplay {

    /**
     * DO NOT CHANGE THESE FIELD
     */
    private Set<MyScheduledTask> tasks;

    /**
     * This method is used for cleaning up, return the task id, return -1 to disable
     */
    public abstract MyScheduledTask gc();

    /**
     * This method is used for a runnable, return the task id, return -1 to disable
     */
    public abstract MyScheduledTask run();

    /**
     * Register this custom display to InteractionVisualizer.
     */
    public final void register() {
        if (key().isNative()) {
            throw new IllegalStateException("EntryKey must not have the default interactionvisualizer namespace");
        }
        InteractionVisualizerAPI.getPreferenceManager().registerEntry(key());
        TaskManager.runnables.add(this);
        this.tasks = new HashSet<>();
        MyScheduledTask gc = gc();
        if (gc != null) {
            this.tasks.add(gc);
        }
        MyScheduledTask run = run();
        if (run != null) {
            this.tasks.add(run);
        }
    }

    @Deprecated
    public final EntryKey registerNative() {
        TaskManager.runnables.add(this);
        this.tasks = new HashSet<>();
        MyScheduledTask gc = gc();
        if (gc != null) {
            this.tasks.add(gc);
        }
        MyScheduledTask run = run();
        if (run != null) {
            this.tasks.add(run);
        }
        return key();
    }

    /**
     * Unregister this custom display to InteractionVisualizer.
     * You don't have to use this normally.
     */
    @Deprecated
    public final void unregister() {
        TaskManager.runnables.remove(this);
        this.tasks.forEach(MyScheduledTask::cancel);
    }

}
