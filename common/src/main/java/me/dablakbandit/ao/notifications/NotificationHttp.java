package me.dablakbandit.ao.notifications;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class NotificationHttp {

	private NotificationHttp() {
	}

	public static void post(String url, String contentType, String body, Map<String, String> headers) throws IOException {
		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		con.setConnectTimeout(10000);
		con.setReadTimeout(10000);
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", contentType);
		con.setRequestProperty("User-Agent", "AlwaysOnline");
		if (headers != null) {
			for (Map.Entry<String, String> header : headers.entrySet()) {
				con.setRequestProperty(header.getKey(), header.getValue());
			}
		}
		con.setDoOutput(true);
		OutputStream out = con.getOutputStream();
		out.write(body.getBytes(StandardCharsets.UTF_8));
		out.flush();
		out.close();
		int responseCode = con.getResponseCode();
		if (responseCode < 200 || responseCode >= 300) {
			throw new IOException("Server responded with HTTP " + responseCode);
		}
		con.disconnect();
	}

	public static String urlEncode(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

}
