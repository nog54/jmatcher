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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;

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
public class JMatcherConnectionRequester {

	private String name;

	private String jmatcherServer;
	private int jmatcherServerPort;
	private int internalNetworkPortTellerPort = JMatcher.PORT;
	private int retryCount = defaultRetryCount;
	private int receiveBuffSize = defaultBuffSize;

	private static final int defaultRetryCount = 2;
	private static final int defaultBuffSize = Math.max(256, JMatcherClientMessage.buffSizeToReceiveSerializedMessage);
	private static final int defaultUdpSocketTimeoutMillSec = 4000;
	private static final int maxCountOfReceivePacketsAtOneTime = 10;

	/**
	 * @param name
	 * @param host
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherConnectionRequester(String name, String host) {
		this(name, host, JMatcher.PORT);
	}

	/**
	 * @param name
	 * @param host
	 * @param port
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherConnectionRequester(String name, String host, int port) {
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
	 * @return true if success
	 * @throws IOException
	 *             thrown if failed to communicate with other
	 */
	@SuppressWarnings("resource")
	public JMatcherConnectionRequesterPeer connect(int key) throws IOException {
		final DatagramSocket socket = new DatagramSocket();
		try {
			final JMatcherConnectionRequesterPeer peer = this.tryToConnect(key, socket);
			if (peer == null) {
				JMatcherClientUtil.close(socket);
				return null;
			}
			return peer;
		} catch (Exception e) {
			JMatcherClientUtil.close(socket);
			throw e;
		}
	}

	private JMatcherConnectionRequesterPeer tryToConnect(int key, DatagramSocket socket) throws IOException {
		this.setupUDPSocket(socket);
		Host connectionTargetHost = this.getTargetHostFromServer(key, socket);
		if (connectionTargetHost == null) {
			return null;
		}
		if (SpecialHostAddress.ON_INTERNAL_NETWORK_HOST.equals(connectionTargetHost.getAddress())) {
			connectionTargetHost = this.findInternalNetworkEntryHost(key, socket);
			if (connectionTargetHost == null) {
				return null;
			}
		}
		for (int i = 0; i < this.retryCount; i++) {
			final JMatcherConnectionRequesterPeer peer = this.tryToConnectTo(connectionTargetHost, socket);
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
				final ConnectionResponse response = (ConnectionResponse) JMatcherClientUtil.receiveUDPResponse(socket, this.receiveBuffSize);
				return response.getHost();
			} catch (SocketTimeoutException | ClassCastException | IllegalArgumentException e) {
				// failed
			}
		}
		return null;
	}

	private Host findInternalNetworkEntryHost(int key, DatagramSocket socket) {
		try {
			for (final InterfaceAddress networkInterface : NetworkInterface.getByInetAddress(InetAddress.getLocalHost()).getInterfaceAddresses()) {
				try {
					final InetSocketAddress broadcastSocketAddress = new InetSocketAddress(networkInterface.getBroadcast(), this.internalNetworkPortTellerPort);
					JMatcherClientUtil.sendMessage(socket, String.valueOf(key), broadcastSocketAddress);
					final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(socket, this.receiveBuffSize);
					final int toldPort = Integer.valueOf(JMatcherClientUtil.getMessageFrom(packet)).intValue();
					final String address = packet.getAddress().getHostAddress();
					return new Host(address, toldPort);
				} catch (NumberFormatException | IOException e) {
					// when toldPort was invalid or the broadcast didn't reach
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private JMatcherConnectionRequesterPeer tryToConnectTo(final Host connectionTargetHost, DatagramSocket socket) throws IOException {
		JMatcherClientUtil.sendJMatcherClientMessage(socket, JMatcherClientMessageType.CONNECT_REQUEST, this.name, connectionTargetHost);
		try {
			for (int i = 0; i < maxCountOfReceivePacketsAtOneTime; i++) {
				final JMatcherClientMessage receivedJMatcherMessage = tryToReceiveJMatcherMessageFrom(connectionTargetHost, socket, this.receiveBuffSize);
				if (receivedJMatcherMessage == null) {
					continue;
				}
				final JMatcherClientMessageType messageType = receivedJMatcherMessage.getType();
				if (messageType == JMatcherClientMessageType.CONNECT_REQUEST) {
					JMatcherClientUtil.sendJMatcherClientMessage(socket, JMatcherClientMessageType.GOT_CONNECT_REQUEST, this.name, connectionTargetHost);
					continue;
				}
				if (messageType == JMatcherClientMessageType.ENTRY_CLIENT_IS_FULL || messageType == JMatcherClientMessageType.CANCEL) {
					return null;
				}
				if (messageType == JMatcherClientMessageType.GOT_CONNECT_REQUEST) {
					connectionTargetHost.setName(receivedJMatcherMessage.getSenderName());
					return new JMatcherConnectionRequesterPeer(this.name, socket, connectionTargetHost, this.receiveBuffSize, this.retryCount);
				}
			}
		} catch (SocketTimeoutException e) {
			// one of the end conditions
		}
		return null;
	}

	static JMatcherClientMessage tryToReceiveJMatcherMessageFrom(Host host, DatagramSocket socket, int receiveBuffSize) throws SocketTimeoutException, IOException {
		final DatagramPacket packet = tryToReceiveUDPPacketFrom(host, socket, receiveBuffSize);
		return JMatcherClientUtil.getJMatcherMessageFrom(packet);
	}

	static DatagramPacket tryToReceiveUDPPacketFrom(Host host, DatagramSocket socket, int receiveBuffSize) throws SocketTimeoutException, IOException {
		final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(socket, receiveBuffSize);
		if (JMatcherClientUtil.packetCameFrom(host, packet) == false) {
			return null;
		}
		return packet;
	}

	/**
	 * @author goshi 2016/02/08
	 */
	public static class JMatcherConnectionRequesterPeer implements Peer {
		private String name;
		private final DatagramSocket socket;
		private final Host connectingHost;
		private int receiveBuffSize;
		private int retryCount;

		JMatcherConnectionRequesterPeer(String name, DatagramSocket socket, Host connectingHost, int receiveBuffSize, int retryCount) {
			if (socket == null || connectingHost == null) {
				throw new IllegalArgumentException();
			}
			this.name = name;
			this.socket = socket;
			this.connectingHost = connectingHost;
			this.receiveBuffSize = receiveBuffSize;
			this.retryCount = retryCount;
		}

		@Override
		public void close() {
			if (this.socket.isClosed()) {
				return;
			}
			try {
				for (int i = 0; i < this.retryCount; i++) {
					JMatcherClientUtil.sendJMatcherClientMessage(this.socket, JMatcherClientMessageType.CANCEL, this.name, this.connectingHost);
					try {
						for (int j = 0; j < maxCountOfReceivePacketsAtOneTime; j++) {
							final JMatcherClientMessage receivedMessage = tryToReceiveJMatcherMessageFrom(this.connectingHost, this.socket, this.receiveBuffSize);
							if (receivedMessage != null && receivedMessage.getType() == JMatcherClientMessageType.CANCELLED) {
								return;
							}
						}
					} catch (SocketTimeoutException e) {
						// one of the end conditions
					}
				}
			} catch (IOException e) {
				return;
			} finally {
				JMatcherClientUtil.close(this.socket);
			}
		}

		@Override
		public ReceivedMessage receiveMessage() {
			if (this.socket.isClosed()) {
				return null;
			}
			try {
				for (int i = 0; i < this.retryCount; i++) {
					final DatagramPacket packet = JMatcherConnectionRequester.tryToReceiveUDPPacketFrom(this.connectingHost, this.socket, this.receiveBuffSize);
					if (packet == null) {
						continue;
					}
					final String receivedMessage = JMatcherClientUtil.getMessageFrom(packet);
					final JMatcherClientMessage jmatcherClientMessage = tryTransformToJMatcherClientMessage(receivedMessage);
					if (jmatcherClientMessage == null) {
						return new ReceivedMessage(this.connectingHost, receivedMessage);
					} else if (JMatcherClientMessageType.CANCEL == jmatcherClientMessage.getType()) {
						this.closeWithoutNotificationToConnectingHost();
						return null;
					}
				}
				System.err.println("JMatcherConnectionClient : abnormal state"); //$NON-NLS-1$
				return null;
			} catch (IOException e) {
				return null;
			}
		}

		/**
		 * Close it without notification to the connecting host. We should
		 * generally use {@link #close()} or {@link #cancelConnection()} instead
		 * of this method
		 */
		public void closeWithoutNotificationToConnectingHost() {
			JMatcherClientUtil.close(this.socket);
		}

		private static JMatcherClientMessage tryTransformToJMatcherClientMessage(final String receiveMessage) {
			return JMatcherClientMessage.deserialize(receiveMessage);
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
		 */
		public boolean sendMessage(String message) {
			if (message == null || this.socket.isClosed()) {
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
			if (this.socket.isClosed()) {
				return result;
			}
			result.add(this.connectingHost);
			return result;
		}

		/**
		 * @return the socket
		 */
		public DatagramSocket getSocket() {
			return this.socket;
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

	}
}
