/** Copyright 2015 Goshi Noguchi (noggon54@gmail.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. */

package org.nognog.jmatcher.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.nognog.jmatcher.Host;
import org.nognog.jmatcher.JMatcher;
import org.nognog.jmatcher.SpecialHostAddress;
import org.nognog.jmatcher.udp.request.ConnectionRequest;
import org.nognog.jmatcher.udp.response.ConnectionResponse;

/**
 * This is a class to communicate with a JMatcherEntryClient. This class is not
 * thread-safe.
 * 
 * @author goshi 2015/11/27
 */
public class Connector {

	private String name;

	private Logger logger;

	private String jmatcherServer;
	private int jmatcherServerPort;
	private int internalNetworkPortTellerPort = JMatcher.PORT;
	private int retryCount = defaultRetryCount;
	private int receiveBuffSize = defaultBuffSize;

	private static final int defaultRetryCount = 2;
	private static final int defaultBuffSize = JMatcherClientMessage.buffSizeToReceiveSerializedMessage;
	private static final int defaultUdpSocketTimeoutMillSec = 4000;
	private static final int maxCountOfReceivePacketsAtOneTime = 10;

	/**
	 * @param name
	 * @param host
	 */
	public Connector(String name, String host) {
		this(name, host, JMatcher.PORT);
	}

	/**
	 * @param name
	 * @param host
	 * @param port
	 */
	public Connector(String name, String host, int port) {
		this.setName(name);
		this.jmatcherServer = host;
		this.jmatcherServerPort = port;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		if (!JMatcherClientMessage.regardsAsValidName(name)) {
			final String message = new StringBuilder().append("name is too long : ").append(name).toString(); //$NON-NLS-1$
			throw new IllegalArgumentException(message);
		}
		this.name = name;
	}

	/**
	 * @return the logger
	 */
	public Logger getLogger() {
		return this.logger;
	}

	/**
	 * @param logger
	 *            the logger to set
	 */
	public void setLogger(Logger logger) {
		this.logger = logger;
	}

	/**
	 * @return the jmatcherServer
	 */
	public String getJmatcherServer() {
		return this.jmatcherServer;
	}

	/**
	 * @param jmatcherServer
	 *            the jmatcherServer to set
	 */
	public void setJmatcherServer(String jmatcherServer) {
		this.jmatcherServer = jmatcherServer;
	}

	/**
	 * @return the jmatcherServerPort
	 */
	public int getJmatcherServerPort() {
		return this.jmatcherServerPort;
	}

	/**
	 * @param jmatcherServerPort
	 *            the jmatcherServerPort to set
	 */
	public void setJmatcherServerPort(int jmatcherServerPort) {
		this.jmatcherServerPort = jmatcherServerPort;
	}

	/**
	 * @return the retryCount
	 */
	public int getRetryCount() {
		return this.retryCount;
	}

	/**
	 * @param retryCount
	 *            the retryCount to set
	 */
	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	/**
	 * @return the receiveBuffSize
	 */
	public int getReceiveBuffSize() {
		return this.receiveBuffSize;
	}

	/**
	 * set receiveBuffSize, but the min value is restricted by
	 * {@link JMatcherClientMessage#buffSizeToReceiveSerializedMessage}}
	 * 
	 * @param receiveBuffSize
	 *            the receiveBuffSize to set
	 */
	public void setReceiveBuffSize(int receiveBuffSize) {
		this.receiveBuffSize = Math.max(receiveBuffSize, JMatcherClientMessage.buffSizeToReceiveSerializedMessage);
	}

	@SuppressWarnings("static-method")
	protected void setupUDPSocket(final DatagramSocket udpSocket) throws SocketException {
		// overridden when configure the option of udp-socket
		udpSocket.setSoTimeout(defaultUdpSocketTimeoutMillSec);
	}

	/**
	 * @return the internalNetworkPortTellerPort
	 */
	public int getInternalNetworkPortTellerPort() {
		return this.internalNetworkPortTellerPort;
	}

	/**
	 * It has to be called before {@link #connect(int)}
	 * 
	 * @param internalNetworkPortTellerPort
	 *            the internalNetworkPortTellerPort to set
	 */
	public void setInternalNetworkPortTellerPort(int internalNetworkPortTellerPort) {
		this.internalNetworkPortTellerPort = internalNetworkPortTellerPort;
	}

	/**
	 * @param key
	 * @return a peer which has connection to another peer that the key is
	 *         corresponding with, or null is returned if failed to connect
	 * @throws IOException
	 *             thrown if an I/O error occurs
	 */
	@SuppressWarnings("resource")
	public ConnectorPeer connect(int key) throws IOException {
		final DatagramSocket socket = new DatagramSocket();
		this.setupUDPSocket(socket);
		this.log(Level.INFO, "start to try to connect to ", Integer.valueOf(key)); //$NON-NLS-1$
		try {
			final ConnectorPeer peer = this.tryToConnect(key, socket);
			if (peer == null) {
				JMatcherClientUtil.close(socket);
				return null;
			}
			this.log(Level.INFO, "succeeded in connecting to ", Integer.valueOf(key)); //$NON-NLS-1$
			return peer;
		} catch (Exception e) {
			JMatcherClientUtil.close(socket);
			this.log(Level.ERROR, new StringBuilder("falied to connect to ").append(key).toString(), e); //$NON-NLS-1$
			throw e;
		}
	}

	private ConnectorPeer tryToConnect(int key, DatagramSocket socket) throws IOException {
		Host connectionTargetHost = this.getTargetHostFromServer(key, socket);
		if (connectionTargetHost == null) {
			this.log(Level.INFO, "could not find ", Integer.valueOf(key)); //$NON-NLS-1$
			return null;
		}
		if (SpecialHostAddress.ON_INTERNAL_NETWORK_HOST.equals(connectionTargetHost.getAddress())) {
			this.log(Level.INFO, "target host(", Integer.valueOf(key), ") is on my internal network"); //$NON-NLS-1$ //$NON-NLS-2$
			connectionTargetHost = this.findInternalNetworkEntryHost(key, socket);
			if (connectionTargetHost == null) {
				this.log(Level.INFO, "could not find ", Integer.valueOf(key), " on internal network"); //$NON-NLS-1$ //$NON-NLS-2$
				return null;
			}
		}
		this.log(Level.DEBUG, "target host is ", connectionTargetHost); //$NON-NLS-1$

		for (int i = 0; i < this.retryCount; i++) {
			this.log(Level.DEBUG, "count of trying to connect : ", Integer.valueOf(i)); //$NON-NLS-1$
			final ConnectorPeer peer = this.tryToConnectTo(connectionTargetHost, socket);
			if (peer != null) {
				return peer;
			}
		}
		return null;
	}

	private Host getTargetHostFromServer(int key, DatagramSocket socket) throws IOException {
		for (int i = 0; i < this.retryCount; i++) {
			try {
				JMatcherClientUtil.sendUDPRequest(socket, new ConnectionRequest(Integer.valueOf(key)), new InetSocketAddress(this.jmatcherServer, this.jmatcherServerPort));
				this.log(Level.DEBUG, "sent connection request to ", this.jmatcherServer, ":", Integer.valueOf(this.jmatcherServerPort)); //$NON-NLS-1$ //$NON-NLS-2$
				final ConnectionResponse response = (ConnectionResponse) JMatcherClientUtil.receiveUDPResponse(socket, this.receiveBuffSize);
				this.log(Level.DEBUG, "received connection response"); //$NON-NLS-1$
				return response.getHost();
			} catch (SocketTimeoutException | ClassCastException | IllegalArgumentException | NullPointerException e) {
				// failed
				this.log(Level.DEBUG, "caught exception while getting target host from server", e); //$NON-NLS-1$
			}
		}
		return null;
	}

	private Host findInternalNetworkEntryHost(int key, DatagramSocket socket) {
		try {
			final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				final NetworkInterface networkInterface = interfaces.nextElement();
				for (final InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
					try {
						if (interfaceAddress.getBroadcast() == null) {
							continue;
						}
						final InetSocketAddress broadcastSocketAddress = new InetSocketAddress(interfaceAddress.getBroadcast(), this.internalNetworkPortTellerPort);
						JMatcherClientUtil.sendMessage(socket, String.valueOf(key), broadcastSocketAddress);
						this.log(Level.DEBUG, "sent connection request to broadcast address ", broadcastSocketAddress); //$NON-NLS-1$
						final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(socket, this.receiveBuffSize);
						final String hostAddress = packet.getAddress().getHostAddress();
						this.log(Level.DEBUG, "received packet from  ", hostAddress); //$NON-NLS-1$
						final int toldPort = Integer.valueOf(JMatcherClientUtil.getMessageFrom(packet)).intValue();
						this.log(Level.DEBUG, "told port is ", Integer.valueOf(toldPort)); //$NON-NLS-1$
						return new Host(hostAddress, toldPort);
					} catch (NumberFormatException | IOException e) {
						// when toldPort was invalid or the broadcast didn't
						// reach
						this.log(Level.DEBUG, "caught exception while searching on ", interfaceAddress, e); //$NON-NLS-1$
					}
				}
			}
		} catch (IOException e) {
			this.log(Level.DEBUG, "caught exception while finding entry host being on the internal network on the same network", e); //$NON-NLS-1$
		}
		return null;
	}

	private ConnectorPeer tryToConnectTo(final Host connectionTargetHost, DatagramSocket socket) throws IOException {
		JMatcherClientUtil.sendJMatcherClientMessage(socket, JMatcherClientMessageType.CONNECT_REQUEST, this.name, connectionTargetHost);
		this.log(Level.DEBUG, "sent connection request to ", connectionTargetHost, "to do hole-panching"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			for (int i = 0; i < maxCountOfReceivePacketsAtOneTime; i++) {
				final JMatcherClientMessage receivedJMatcherMessage = this.tryToReceiveJMatcherMessageFrom(connectionTargetHost, socket);
				if (receivedJMatcherMessage == null) {
					continue;
				}
				final JMatcherClientMessageType messageType = receivedJMatcherMessage.getType();
				this.log(Level.DEBUG, "received ", messageType); //$NON-NLS-1$
				if (messageType == JMatcherClientMessageType.CONNECT_REQUEST) {
					JMatcherClientUtil.sendJMatcherClientMessage(socket, JMatcherClientMessageType.GOT_CONNECT_REQUEST, this.name, connectionTargetHost);
					continue;
				}
				if (messageType == JMatcherClientMessageType.ENTRY_CLIENT_IS_FULL || messageType == JMatcherClientMessageType.CANCEL) {
					return null;
				}
				if (messageType == JMatcherClientMessageType.GOT_CONNECT_REQUEST) {
					connectionTargetHost.setName(receivedJMatcherMessage.getSenderName());
					return new ConnectorPeer(this.name, socket, connectionTargetHost, this.receiveBuffSize, this.retryCount);
				}
			}
		} catch (SocketTimeoutException e) {
			// one of the end conditions
		}
		return null;
	}

	private JMatcherClientMessage tryToReceiveJMatcherMessageFrom(Host host, DatagramSocket socket) throws SocketTimeoutException, IOException {
		final DatagramPacket packet = this.tryToReceiveUDPPacketFrom(host, socket);
		final JMatcherClientMessage result = JMatcherClientUtil.getJMatcherMessageFrom(packet);
		this.log(Level.DEBUG, "receive ", result, " from ", host); //$NON-NLS-1$ //$NON-NLS-2$
		return result;
	}

	private DatagramPacket tryToReceiveUDPPacketFrom(Host host, DatagramSocket socket) throws SocketTimeoutException, IOException {
		final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(socket, this.receiveBuffSize);
		this.log(Level.DEBUG, "received packet from", packet.getSocketAddress()); //$NON-NLS-1$
		if (JMatcherClientUtil.packetCameFrom(host, packet) == false) {
			return null;
		}
		this.log(Level.DEBUG, "accept packet which came from", packet.getAddress(), ":", Integer.valueOf(packet.getPort())); //$NON-NLS-1$ //$NON-NLS-2$
		return packet;
	}

	private void log(Level level, Object... msgs) {
		if (this.logger == null) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (Object msg : msgs) {
			sb.append(msg);
		}
		this.logger.log(level, sb.toString());
	}

	private void log(Level level, String msg, Throwable t) {
		if (this.logger == null) {
			return;
		}
		this.logger.log(level, msg, t);
	}

	/**
	 * @author goshi 2016/02/08
	 */
	public static class ConnectorPeer implements Peer {
		private String name;
		private final DatagramSocket socket;
		private Host connectingHost;
		private int receiveBuffSize;
		private int retryCount;
		private Set<PeerObserver> observers;

		private Thread communicationThread;
		private ReceivedMessageBuffer receivedMessageBuffer;

		private volatile boolean isDisconnecting;

		ConnectorPeer(String name, DatagramSocket socket, Host connectingHost, int receiveBuffSize, int retryCount) {
			if (socket == null || connectingHost == null) {
				throw new IllegalArgumentException();
			}
			this.name = name;
			this.socket = socket;
			this.connectingHost = connectingHost;
			this.receiveBuffSize = receiveBuffSize;
			this.retryCount = retryCount;
			this.observers = new HashSet<>();
			this.receivedMessageBuffer = new ReceivedMessageBuffer();
			this.communicationThread = new Thread() {
				@Override
				public void run() {
					ConnectorPeer.this.performCommunicationLoop();
				}
			};
			this.communicationThread.start();
		}

		/**
		 * 
		 */
		public void performCommunicationLoop() {
			try {
				while (this.connectingHost != null && this.socket.isClosed() == false) {
					try {
						this.receivePacketAndHandle();
					} catch (IOException e) {
						throw e;
					} catch (Exception e) {
						// it should be logged
					}
				}
			} catch (IOException e) {
				// IOException is mainly caused by closing socket
			}
		}

		private void receivePacketAndHandle() throws IOException {
			final DatagramPacket packet = this.tryToReceiveUDPPacketFrom(this.connectingHost);
			if (packet == null) {
				return;
			}
			final String receivedMessage = JMatcherClientUtil.getMessageFrom(packet);
			final JMatcherClientMessage jmatcherClientMessage = tryTransformToJMatcherClientMessage(receivedMessage);
			if (jmatcherClientMessage == null) {
				this.receivedMessageBuffer.store(this.connectingHost, receivedMessage);
			} else if (JMatcherClientMessageType.CANCEL == jmatcherClientMessage.getType()) {
				final Host removedHost = this.connectingHost;
				this.connectingHost = null;
				this.notifyObservers(UpdateEvent.REMOVE, removedHost);
			} else if (JMatcherClientMessageType.CANCELLED == jmatcherClientMessage.getType()) {
				synchronized (this) {
					this.notifyAll();
				}
				this.isDisconnecting = false;
			}
		}

		private DatagramPacket tryToReceiveUDPPacketFrom(Host host) throws IOException {
			try {
				final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(this.socket, this.receiveBuffSize);
				if (JMatcherClientUtil.packetCameFrom(host, packet) == false) {
					return null;
				}
				return packet;
			} catch (SocketTimeoutException e) {
				return null;
			}
		}

		private static JMatcherClientMessage tryTransformToJMatcherClientMessage(final String receiveMessage) {
			return JMatcherClientMessage.deserialize(receiveMessage);
		}

		private void notifyObservers(UpdateEvent event, Host target) {
			for (PeerObserver observer : this.observers) {
				observer.updateConnectingHosts(new HashSet<Host>(), event, target);
			}
		}

		@Override
		public void disconnect(Host host) {
			if (this.connectingHost == null || host != this.connectingHost) {
				return;
			}
			this.sendDisconnectionMessage();
			this.connectingHost = null;
			this.notifyObservers(UpdateEvent.REMOVE, host);
		}

		/**
		 * Disconnect
		 */
		public void disconnect() {
			this.disconnect(this.connectingHost);
		}

		@Override
		public void close() {
			if (this.socket.isClosed()) {
				return;
			}
			if (this.connectingHost == null) {
				this.closeWithoutNotificationToConnectingHost();
				return;
			}
			this.sendDisconnectionMessage();
			this.connectingHost = null;
			this.closeWithoutNotificationToConnectingHost();
			this.notifyObservers(UpdateEvent.CLEAR, null);
		}

		/**
		 * Close it without notification to the connecting host. We should
		 * generally use {@link #close()} instead of this method
		 */
		public void closeWithoutNotificationToConnectingHost() {
			JMatcherClientUtil.close(this.socket);
		}

		/**
		 * The disconnection message may not reach the target (connecting host).
		 */
		private void sendDisconnectionMessage() {
			try {
				this.isDisconnecting = true;
				for (int i = 0; i < this.retryCount; i++) {
					JMatcherClientUtil.sendJMatcherClientMessage(this.socket, JMatcherClientMessageType.CANCEL, this.name, this.connectingHost);
					synchronized (this) {
						this.wait(this.socket.getSoTimeout());
					}
					if (this.isDisconnecting == false) {
						break;
					}
				}
			} catch (Exception e) {
				// terminate forcely
			} finally {
				this.isDisconnecting = false;
			}

		}

		@Override
		public ReceivedMessage receiveMessage() {
			if (this.socket.isClosed() || this.connectingHost == null) {
				return null;
			}
			try {
				return this.receivedMessageBuffer.poll(this.socket.getSoTimeout());
			} catch (Exception e) {
				return null;
			}
		}

		@Override
		public String receiveMessageFrom(Host host) {
			if (!host.equals(this.connectingHost)) {
				return null;
			}
			final ReceivedMessage receiveMessage = this.receiveMessage();
			if (receiveMessage == null) {
				return null;
			}
			return receiveMessage.getMessage();
		}

		@Override
		public Host[] sendMessageTo(String message, Host... hosts) {
			if (hosts.length != 1 || hosts[0].equals(this.connectingHost) == false) {
				return new Host[0];
			}
			final boolean success = this.sendMessage(message);
			if (success) {
				final Host[] result = new Host[1];
				result[0] = hosts[0];
				return result;
			}
			return new Host[0];
		}

		/**
		 * @param message
		 * @return true if succeed in sending
		 * @throws IOException
		 *             thrown if an I/O error occurs
		 */
		public boolean sendMessage(String message) {
			if (message == null || this.socket.isClosed() || this.connectingHost == null) {
				return false;
			}
			try {
				JMatcherClientUtil.sendMessage(this.socket, message, this.connectingHost);
			} catch (IOException e) {
				return false;
			}
			return true;
		}

		/**
		 * @return the connectingHost
		 */
		public Host getConnectingHost() {
			return this.connectingHost;
		}

		@Override
		public Set<Host> getConnectingHosts() {
			final Set<Host> result = new HashSet<>();
			if (this.socket.isClosed() || this.connectingHost == null) {
				return result;
			}
			result.add(this.connectingHost);
			return result;
		}

		/**
		 * @return the socket
		 */
		@Override
		public DatagramSocket getSocket() {
			return this.socket;
		}

		@Override
		public void setReceiveBuffSize(int buffSize) {
			this.receiveBuffSize = Math.max(buffSize, JMatcherClientMessage.buffSizeToReceiveSerializedMessage);
		}

		@Override
		public int getReceiveBuffSize() {
			return this.receiveBuffSize;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return this.name;
		}

		/**
		 * @param name
		 *            the name to set
		 */
		public void setName(String name) {
			this.name = name;
		}

		@Override
		public boolean isOnline() {
			return !this.socket.isClosed();
		}

		/**
		 * @param observer
		 */
		@Override
		public void addObserver(PeerObserver observer) {
			this.observers.add(observer);
		}

		/**
		 * @param observer
		 */
		@Override
		public void removeObserver(PeerObserver observer) {
			this.observers.remove(observer);
		}
	}
}
