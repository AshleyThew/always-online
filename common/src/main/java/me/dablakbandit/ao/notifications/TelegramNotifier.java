package me.dablakbandit.ao.notifications;

import com.google.gson.Gson;
import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.config.AlwaysOnlineConfig;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class TelegramNotifier extends AbstractStatusNotifier {

	private final Gson gson = new Gson();
	private final String botToken;
	private final String chatId;

	public TelegramNotifier(NativeExecutor nativeExecutor, AlwaysOnlineConfig.Notifications config) {
		super(nativeExecutor, config);
		this.botToken = AlwaysOnlineConfig.text(config.telegram.botToken);
		this.chatId = AlwaysOnlineConfig.text(config.telegram.chatId);
	}

	@Override
	public String name() {
		return "Telegram";
	}

	@Override
	public boolean isEnabled() {
		return !this.botToken.isEmpty() && !this.chatId.isEmpty();
	}

	@Override
	protected void sendNotification(boolean offline, String message) throws IOException {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("chat_id", this.chatId);
		payload.put("text", message);
		NotificationHttp.post("https://api.telegram.org/bot" + this.botToken + "/sendMessage", "application/json", this.gson.toJson(payload), null);
	}

}
