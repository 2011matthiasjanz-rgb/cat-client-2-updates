/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import meteordevelopment.meteorclient.MeteorClient;

public class CreatorUtils {
    private static final String CREATOR_USERNAME = "Mattander2011";

    private CreatorUtils() {
    }

    public static boolean isCreator() {
        return MeteorClient.mc != null
            && MeteorClient.mc.getSession() != null
            && CREATOR_USERNAME.equalsIgnoreCase(MeteorClient.mc.getSession().getUsername());
    }
}
