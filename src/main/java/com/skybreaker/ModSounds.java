package com.skybreaker;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Skybreaker's own sounds (assets/skybreaker/sounds/*.ogg, declared in sounds.json). */
public final class ModSounds {
	public static final SoundEvent REMOTE_BEEP = reg("remote_beep");
	public static final SoundEvent CHARGE = reg("charge");
	public static final SoundEvent FIRE = reg("fire");
	public static final SoundEvent BOOM = reg("boom");
	public static final SoundEvent BH_DRONE = reg("bh_drone");
	public static final SoundEvent BH_COLLAPSE = reg("bh_collapse");
	public static final SoundEvent METEOR_WHISTLE = reg("meteor_whistle");
	public static final SoundEvent LASER_LOOP = reg("laser_loop");

	private static SoundEvent reg(String name) {
		Identifier id = SkybreakerMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}

	private ModSounds() {
	}
}
