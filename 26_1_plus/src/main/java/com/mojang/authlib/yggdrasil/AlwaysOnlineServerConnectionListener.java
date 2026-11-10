package com.mojang.authlib.yggdrasil;

import io.netty.channel.*;
import me.dablakbandit.ao.hybrid.IAlwaysOnline;
import me.dablakbandit.ao.utils.NMSUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

@ChannelHandler.Sharable
public class AlwaysOnlineServerConnectionListener extends ChannelDuplexHandler {

	private static final Field fieldChannels = NMSUtils.getFieldSilent(ServerConnectionListener.class, "channels");
	private static final Field fieldConnections = NMSUtils.getFieldSilent(ServerConnectionListener.class, "connections");

	private final IAlwaysOnline alwaysOnline;
	private List<ChannelFuture> originalChannels;

	AlwaysOnlineServerConnectionListener(IAlwaysOnline alwaysOnline) {
		this.alwaysOnline = alwaysOnline;
	}

	@SuppressWarnings("unchecked")
	public void init() {
		DedicatedServer server = ((CraftServer) Bukkit.getServer()).getServer();

		try {
			if (fieldChannels != null) {
				List<ChannelFuture> channels = (List<ChannelFuture>) fieldChannels.get(server.getConnection());
				alwaysOnline.getNativeExecutor().log(Level.INFO, "Original server connection channels: " + channels.size());
				if (!(channels instanceof WrappedChannelFutureList)) {
					originalChannels = channels;
					fieldChannels.set(server.getConnection(), new WrappedChannelFutureList(channels));
				} else {
					originalChannels = ((WrappedChannelFutureList) channels).oldList;
				}
				return;
			}

			if (fieldConnections != null) {
				alwaysOnline.getNativeExecutor().log(Level.INFO, "Falling back to direct connection pipeline injection");
				for (Connection connection : server.getConnection().getConnections()) {
					attachToPipeline(connection.channel, ATTACH_ATTEMPTS);
				}
				return;
			}

			alwaysOnline.getNativeExecutor().log(Level.WARNING, "Could not find ServerConnectionListener channels/connections field");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public void restore() {
		DedicatedServer server = ((CraftServer) Bukkit.getServer()).getServer();
		try {
			if (fieldChannels != null) {
				List<ChannelFuture> channels = (List<ChannelFuture>) fieldChannels.get(server.getConnection());
				List<ChannelFuture> toRestore = originalChannels;
				if (channels instanceof WrappedChannelFutureList) {
					toRestore = ((WrappedChannelFutureList) channels).oldList;
				}
				if (toRestore != null && channels != toRestore) {
					fieldChannels.set(server.getConnection(), toRestore);
				}
				if (toRestore != null) {
					for (ChannelFuture channelFuture : toRestore) {
						ChannelPipeline pipeline = channelFuture.channel().pipeline();
						if (pipeline.get("ao_server_acceptor") != null) {
							pipeline.remove("ao_server_acceptor");
						}
					}
				}
			}

			for (Connection connection : server.getConnection().getConnections()) {
				ChannelPipeline pipeline = connection.channel.pipeline();
				if (pipeline.get("ao_handler") != null) {
					pipeline.remove("ao_handler");
				}
			}

			alwaysOnline.getNativeExecutor().log(Level.INFO, "Restored server connection listener state");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void decorateServerChannel(ChannelFuture channelFuture) {
		Channel serverChannel = channelFuture.channel();
		alwaysOnline.getNativeExecutor().log(Level.INFO, "Decorating server channel: " + serverChannel);
		if (serverChannel.pipeline().get("ao_server_acceptor") != null) {
			serverChannel.pipeline().remove("ao_server_acceptor");
		}
		serverChannel.pipeline().addFirst("ao_server_acceptor", new ChannelInboundHandlerAdapter() {

			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
				super.channelRead(ctx, msg);
				if (msg instanceof Channel) {
					((Channel) msg).pipeline().addLast(new ChannelInitializer<Channel>() {
						@Override
						protected void initChannel(Channel ch) {
							attachToPipeline(ch, ATTACH_ATTEMPTS);
						}
					});
				}
			}

		});
	}

	private class WrappedChannelFutureList extends AbstractList<ChannelFuture> {

		private final List<ChannelFuture> oldList;

		WrappedChannelFutureList(List<ChannelFuture> oldList) {
			this.oldList = oldList;
			for (ChannelFuture channelFuture : oldList) {
				decorateServerChannel(channelFuture);
			}
		}

		@Override
		public ChannelFuture get(int index) {
			return oldList.get(index);
		}

		@Override
		public int size() {
			return oldList.size();
		}

		@Override
		public ChannelFuture set(int index, ChannelFuture element) {
			decorateServerChannel(element);
			return oldList.set(index, element);
		}

		@Override
		public void add(int index, ChannelFuture element) {
			decorateServerChannel(element);
			oldList.add(index, element);
		}

		@Override
		public ChannelFuture remove(int index) {
			return oldList.remove(index);
		}

		@Override
		public boolean add(ChannelFuture channelFuture) {
			decorateServerChannel(channelFuture);
			return oldList.add(channelFuture);
		}

		@Override
		public boolean addAll(Collection<? extends ChannelFuture> c) {
			for (ChannelFuture channelFuture : c) {
				decorateServerChannel(channelFuture);
			}
			return oldList.addAll(c);
		}

		@Override
		public boolean addAll(int index, Collection<? extends ChannelFuture> c) {
			for (ChannelFuture channelFuture : c) {
				decorateServerChannel(channelFuture);
			}
			return oldList.addAll(index, c);
		}

		@Override
		public void clear() {
			oldList.clear();
		}

		@Override
		public Iterator<ChannelFuture> iterator() {
			return oldList.iterator();
		}

		@Override
		public ListIterator<ChannelFuture> listIterator() {
			return oldList.listIterator();
		}

		@Override
		public ListIterator<ChannelFuture> listIterator(int index) {
			return oldList.listIterator(index);
		}

		@Override
		public boolean contains(Object o) {
			return oldList.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return oldList.containsAll(c);
		}

		@Override
		public boolean remove(Object o) {
			return oldList.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return oldList.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return oldList.retainAll(c);
		}

		@Override
		public int indexOf(Object o) {
			return oldList.indexOf(o);
		}

		@Override
		public int lastIndexOf(Object o) {
			return oldList.lastIndexOf(o);
		}

		@Override
		public Object[] toArray() {
			return oldList.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return oldList.toArray(a);
		}

		@Override
		public List<ChannelFuture> subList(int fromIndex, int toIndex) {
			return oldList.subList(fromIndex, toIndex);
		}

		@Override
		public boolean equals(Object obj) {
			return oldList.equals(obj);
		}

		@Override
		public int hashCode() {
			return oldList.hashCode();
		}
	}

	// The handler has to sit after the server's packet decoder, just before "packet_handler", or it only ever
	// sees raw bytes and never the login packet. A new connection can reach us before the server has finished
	// building its pipeline, and adding ourselves at the end would then put us in front of the decoder, so
	// wait for "packet_handler" to appear instead.
	private static final int ATTACH_ATTEMPTS = 200;
	private static final long ATTACH_RETRY_MILLIS = 5;

	private void attachToPipeline(Channel channel, int attemptsLeft) {
		ChannelPipeline pipeline = channel.pipeline();
		if (pipeline.get("ao_handler") != null) {
			pipeline.remove("ao_handler");
		}
		if (pipeline.get("packet_handler") != null) {
			pipeline.addBefore("packet_handler", "ao_handler", this);
			return;
		}
		if (!channel.isOpen()) return;
		if (attemptsLeft > 0) {
			channel.eventLoop().schedule(() -> attachToPipeline(channel, attemptsLeft - 1), ATTACH_RETRY_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
			return;
		}
		alwaysOnline.getNativeExecutor().log(Level.WARNING, "Could not find the packet handler on a new connection, so its UUID may not be restored if mojang is offline");
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (alwaysOnline.getOfflineMode() && msg instanceof ServerboundHelloPacket) {
			ServerboundHelloPacket serverboundHelloPacket = (ServerboundHelloPacket) msg;
			if (alwaysOnline.isDebug()) {
				alwaysOnline.getNativeExecutor().log(Level.INFO, "Player " + serverboundHelloPacket.name() + " is trying to log in with UUID " + serverboundHelloPacket.profileId());
			}
			UUID uuid = alwaysOnline.getDatabase().getUUID(serverboundHelloPacket.name());
			if (uuid != null) {
				msg = new ServerboundHelloPacket(serverboundHelloPacket.name(), uuid);
				if (alwaysOnline.isDebug()) {
					alwaysOnline.getNativeExecutor().log(Level.INFO, "Player " + serverboundHelloPacket.name() + " updating UUID to " + uuid);
				}
				// "packet_handler" is the Connection itself. Looking it up in the server's connection list is
				// not enough: Paper only moves new connections into that list on the next tick, so most logins
				// were not found there yet and kept their offline UUID.
				ChannelHandler packetHandler = ctx.pipeline().get("packet_handler");
				if (packetHandler instanceof Connection) {
					((Connection) packetHandler).spoofedUUID = uuid;
				} else {
					for (Connection connection : ((CraftServer) Bukkit.getServer()).getServer().getConnection().getConnections()) {
						if (connection.channel == ctx.channel()) {
							connection.spoofedUUID = uuid;
							break;
						}
					}
				}

			}
		}
		super.channelRead(ctx, msg);
	}
}
