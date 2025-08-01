package me.dablakbandit.ao.sponge;

import me.dablakbandit.ao.hybrid.AlwaysOnline;
import me.dablakbandit.ao.utils.UUIDUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandExecutor;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.parameter.CommandContext;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;

import java.util.UUID;

public class SpongeCommand implements CommandExecutor {

	private final SpongeLoader loader;

	public SpongeCommand(SpongeLoader loader) {
		this.loader = loader;
	}

	Parameter.Value<String> action = Parameter.choices("toggle", "enable", "disable", "reload", "debug", "resetcache", "updateip").key("action").optional().build();
	Parameter.Value<String> username = Parameter.string().key("username").optional().build();
	Parameter.Value<String> ip = Parameter.string().key("ip").optional().build();
	Parameter.Value<String> uuid = Parameter.string().key("uuid").optional().build();

	protected Command.Parameterized build() {
		return Command.builder().permission("alwaysonline.admin").addParameter(action).addParameter(username).addParameter(ip).addParameter(uuid).executor(this).build();
	}

	@Override
	public CommandResult execute(CommandContext context) throws CommandException {
		if (!context.hasAny(action)) {
			displayHelp(context.cause().audience());
			return CommandResult.success();
		}

		String action = context.requireOne(this.action);
		AlwaysOnline alwaysOnline = loader.getAOInstance();

		switch (action.toLowerCase()) {
			case "toggle":
				alwaysOnline.toggleOfflineMode();
				context.cause().audience().sendMessage(Component.text("Mojang offline mode is now ").color(NamedTextColor.GOLD).append(Component.text(alwaysOnline.getOfflineMode() ? "enabled" : "disabled").color(alwaysOnline.getOfflineMode() ? NamedTextColor.GREEN : NamedTextColor.RED)).append(Component.text("!").color(NamedTextColor.GOLD)));

				if (!alwaysOnline.getOfflineMode()) {
					context.cause().audience().sendMessage(Component.text("AlwaysOnline will now treat the mojang servers as being online.").color(NamedTextColor.GOLD));
				} else {
					context.cause().audience().sendMessage(Component.text("AlwaysOnline will no longer treat the mojang servers as being online.").color(NamedTextColor.GOLD));
				}
				break;

			case "disable":
				alwaysOnline.setCheckSessionStatus(false);
				context.cause().audience().sendMessage(Component.text("AlwaysOnline has been disabled! AlwaysOnline will no longer check to see if the session server is offline.").color(NamedTextColor.GOLD));
				break;

			case "enable":
				alwaysOnline.setCheckSessionStatus(true);
				context.cause().audience().sendMessage(Component.text("AlwaysOnline has been enabled! AlwaysOnline will now check to see if the session server is offline.").color(NamedTextColor.GOLD));
				break;

			case "reload":
				alwaysOnline.reload();
				context.cause().audience().sendMessage(Component.text("AlwaysOnline has been reloaded!").color(NamedTextColor.GOLD));
				break;

			case "debug":
				if (context.cause().audience() instanceof ServerPlayer) {
					context.cause().audience().sendMessage(Component.text("Check console for debug information").color(NamedTextColor.GREEN));
				}
				alwaysOnline.printDebugInformation();
				break;

			case "resetcache":
				alwaysOnline.database.resetCache();
				context.cause().audience().sendMessage(Component.text("AlwaysOnline cache reset'd").color(NamedTextColor.GREEN));
				break;

			case "updateip":
				if (context.hasAny(username) && context.hasAny(ip)) {
					String usernameStr = context.requireOne(this.username);
					String ipStr = context.requireOne(this.ip);
					UUID uuidObj = null;

					// Check if UUID is provided as optional parameter
					if (context.hasAny(uuid)) {
						String uuidString = context.requireOne(this.uuid);
						if (UUIDUtil.isValidUUID(uuidString)) {
							uuidObj = UUID.fromString(uuidString);
						} else {
							context.cause().audience().sendMessage(Component.text("AlwaysOnline " + uuidString + " is not a valid UUID").color(NamedTextColor.GREEN));
							break;
						}
					} else {
						// No UUID provided, try to get it from database
						uuidObj = alwaysOnline.database.getUUID(usernameStr);
						if (uuidObj == null) {
							context.cause().audience().sendMessage(Component.text("AlwaysOnline player " + usernameStr + " not found in database").color(NamedTextColor.GREEN));
							break;
						}
					}

					if (!alwaysOnline.database.isValidIP(ipStr)) {
						context.cause().audience().sendMessage(Component.text("AlwaysOnline " + ipStr + " is not a valid IP").color(NamedTextColor.GREEN));
						break;
					}

					alwaysOnline.database.updatePlayer(usernameStr, ipStr, uuidObj);
					context.cause().audience().sendMessage(Component.text("AlwaysOnline updated " + usernameStr + " to " + ipStr).color(NamedTextColor.GREEN));
				} else {
					context.cause().audience().sendMessage(Component.text("Usage: /alwaysonline updateip <username> <ip> [uuid]").color(NamedTextColor.GREEN));
				}
				break;

			default:
				displayHelp(context.cause().audience());
				break;
		}

		alwaysOnline.saveState();
		return CommandResult.success();
	}

	private void displayHelp(net.kyori.adventure.audience.Audience audience) {
		Component divider = Component.text("----------").color(NamedTextColor.GOLD).decorate(TextDecoration.STRIKETHROUGH);

		audience.sendMessage(divider.append(Component.text("[").color(NamedTextColor.GOLD)).append(Component.text("AlwaysOnline").color(NamedTextColor.DARK_GREEN)).append(Component.text(" ").color(NamedTextColor.GOLD)).append(Component.text(loader.getVersion()).color(NamedTextColor.GRAY)).append(Component.text("]").color(NamedTextColor.GOLD)).append(divider));

		audience.sendMessage(Component.text("/alwaysonline toggle - ").color(NamedTextColor.GOLD).append(Component.text("Toggles between mojang online mode").color(NamedTextColor.DARK_GREEN)));

		audience.sendMessage(Component.text("/alwaysonline enable - ").color(NamedTextColor.GOLD).append(Component.text("Enables the plugin").color(NamedTextColor.DARK_GREEN)));

		audience.sendMessage(Component.text("/alwaysonline disable - ").color(NamedTextColor.GOLD).append(Component.text("Disables the plugin").color(NamedTextColor.DARK_GREEN)));

		audience.sendMessage(Component.text("/alwaysonline reload - ").color(NamedTextColor.GOLD).append(Component.text("Reloads the configuration file").color(NamedTextColor.DARK_GREEN)));

		audience.sendMessage(Component.text("/alwaysonline resetcache - ").color(NamedTextColor.GOLD).append(Component.text("Clear database cache").color(NamedTextColor.DARK_GREEN)));

		audience.sendMessage(Component.text("/alwaysonline updateip <username> <ip> [uuid] - ").color(NamedTextColor.GOLD).append(Component.text("Update a users ip in the database").color(NamedTextColor.DARK_GREEN)));

		audience.sendMessage(Component.text("------------------------------").color(NamedTextColor.GOLD).decorate(TextDecoration.STRIKETHROUGH));
	}

}
