/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

// Only has a real effect when this mod's user is hosting the world themselves (integrated
// server / LAN with friends also running Cat Client 2) - see LivingEntityMixin. No effect on
// servers not running this mod, since Totem of Undying death-negation is server-authoritative.
public class TotemBypass extends Module {
    public TotemBypass() {
        super(Categories.Combat, "totem-bypass", "When hosting: prevents entities you attack from being saved by a Totem of Undying. No effect on servers not running this mod.", true);
    }
}
