package com.skybreaker.client;

import com.skybreaker.SkybreakerConfig;
import com.skybreaker.StrikeItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Status readout shown above the hotbar while a Skybreaker remote is in hand: model code + name, READY / RECHARGING
 * (with a charge bar) and target range, and the controls. Pure fills + text, a few draw calls.
 */
final class RemoteHud {
	private static final String[] CODE = {"OA-1", "OA-2", "OA-3", "OA-4"};
	private static final int[] ACCENT = {0xFFFFB030, 0xFFFF3040, 0xFFFF6A20, 0xFFC070FF};

	static void render(GuiGraphicsExtractor g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || CinematicHud.isActive()) {
			return;
		}
		ItemStack stack = heldStack(mc);
		if (stack == null) {
			return;
		}
		StrikeItem item = (StrikeItem) stack.getItem();
		int k = item.kind.ordinal();
		var font = mc.font;

		// the server puts a vanilla item cooldown on the remote; its remaining fraction (1 -> 0) drives the bar
		float pct = mc.player.getCooldowns().getCooldownPercent(stack, 0.0f);
		boolean charging = pct > 0.001f;

		String name = CODE[k] + " " + item.kind.display;
		String status;
		int statusColor;
		if (charging) {
			status = "RECHARGING " + (int) Math.ceil(pct * cooldownSeconds(item.kind)) + "s";
			statusColor = 0xFFFFAA33;
		} else {
			double d = FxManager.aimDistance;
			boolean locked = d >= 0.0;
			status = locked ? "READY  " + (int) d + " m" : "NO TARGET";
			statusColor = locked ? 0xFF55FF77 : 0xFFFF6060;
		}
		String hint = charging ? "Please wait" : "Right-click: fire";
		String sep = "  |  ";

		int wName = font.width(name);
		int wStat = font.width(status);
		int wHint = font.width(hint);
		int wSep = font.width(sep);
		int total = wName + wSep + wStat + wSep + wHint;

		int w = g.guiWidth();
		int h = g.guiHeight();
		int left = w / 2 - total / 2;
		int y = h - 62;
		g.fill(left - 6, y - 3, left + total + 6, y + 11, 0x99000000);
		g.fill(left - 6, y - 3, left - 4, y + 11, ACCENT[k]);
		if (charging) {
			// charge bar along the bottom edge of the panel: fills up as the remote recharges
			int bw = total + 12;
			g.fill(left - 6, y + 11, left - 6 + bw, y + 13, 0x99000000);
			g.fill(left - 6, y + 11, left - 6 + (int) (bw * (1f - pct)), y + 13, ACCENT[k]);
		}

		int x = left;
		g.centeredText(font, name, x + wName / 2, y, ACCENT[k]);
		x += wName;
		g.centeredText(font, sep, x + wSep / 2, y, 0xFF707070);
		x += wSep;
		g.centeredText(font, status, x + wStat / 2, y, statusColor);
		x += wStat;
		g.centeredText(font, sep, x + wSep / 2, y, 0xFF707070);
		x += wSep;
		g.centeredText(font, hint, x + wHint / 2, y, 0xFFB8B8B8);
	}

	/** Configured cooldown in seconds (used to turn the cooldown fraction into a countdown). */
	private static double cooldownSeconds(StrikeItem.Kind kind) {
		SkybreakerConfig c = SkybreakerConfig.get();
		return switch (kind) {
			case RAILGUN -> c.railgunCooldownSeconds;
			case LASER -> c.laserCooldownSeconds;
			case METEOR -> c.meteorCooldownSeconds;
			case BLACKHOLE -> c.blackHoleCooldownSeconds;
		};
	}

	private static ItemStack heldStack(Minecraft mc) {
		ItemStack main = mc.player.getMainHandItem();
		if (main.getItem() instanceof StrikeItem) {
			return main;
		}
		ItemStack off = mc.player.getOffhandItem();
		if (off.getItem() instanceof StrikeItem) {
			return off;
		}
		return null;
	}

	private RemoteHud() {
	}
}
