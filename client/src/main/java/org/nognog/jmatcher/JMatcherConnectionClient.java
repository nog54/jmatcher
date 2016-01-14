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

package org.nognog.jmatcher;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.nognog.jmatcher.udp.request.ConnectionRequest;
import org.nognog.jmatcher.udp.response.ConnectionResponse;

/**
 * @author goshi 2015/11/27
 */
public class JMatcherConnectionClient {

	private String name;

	private String jmatcherHost;
	private int port;
	private int retryCount = defaultRetryCount;
	private int receiveBuffSize = defaultBuffSize;

	private DatagramSocket socket;
	private Host connectingHost;

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
	public JMatcherConnectionClient(String name, String host) {
		this(name, host, JMatcher.PORT);
	}

	/**
	 * @param name
	 * @param host
	 * @param port
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherConnectionClient(String name, String host, int port) {
		this.setName(name);
		this.jmatcherHost = host;
		this.port = port;
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
			throw new IllegalArgumentException("too long name"); //$NON-NLS-1$
		}
		this.name = name;
	}

	/**
	 * @return the jmatcher host
	 */
	public String getJmatcherHost() {
		return this.jmatcherHost;
	}

	/**
	 * @param jmatcherHost
	 *            the jmatcherHost to set
	 */
	public void setJmatcherHost(String jmatcherHost) {
		this.jmatcherHost = jmatcherHost;
	}

	/**
	 * @return the port
	 */
	public int getPort() {
		return this.port;
	}

	/**
	 * @param port
	 *            the port to set
	 */
	public void setPort(int port) {
		this.port = port;
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
	 * @return socket to send packet to the connected entryClient, or null if it
	 *         hasn't connected yet
	 */
	public DatagramSocket getConnectingSocket() {
		return this.socket;
	}

	/**
	 * @return connecting host
	 */
	public Host getConnectingHost() {
		return this.connectingHost;
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
	 * @param key
	 * @return true if success
	 * @throws IOException
	 *             thrown if failed to communicate with other
	 */
	public boolean connect(int key) throws IOException {
		try {
			this.socket = new DatagramSocket();
			this.setupUDPSocket(this.socket);
			final Host connectionTargetHost = this.getTargetHostFromServer(key);
			if (connectionTargetHost == null) {
				return false;
			}
			final InetSocketAddress connectionTargetHostAddress = new InetSocketAddress(connectionTargetHost.getAddress(), connectionTargetHost.getPort());
			for (int i = 0; i < this.retryCount; i++) {
				final boolean success = this.tryToConnectTo(connectionTargetHost, connectionTargetHostAddress);
				if (success) {
					return true;
				}
			}
			this.close();
			return false;
		} catch (IOException e) {
			this.close();
			throw e;
		}
	}

	private boolean tryToConnectTo(final Host connectionTargetHost, final InetSocketAddress connectionTargetHostAddress) throws IOException {
		JMatcherClientUtil.sendJMatcherClientMessage(this.socket, JMatcherClientMessageType.CONNECT_REQUEST, this.name, connectionTargetHost);
		try {
			for (int i = 0; i < maxCountOfReceivePacketsAtOneTime; i++) {
				final JMatcherClientMessage receivedJMatcherMessage = this.tryToReceiveJMatcherMessageFrom(connectionTargetHostAddress);
				if (receivedJMatcherMessage == null) {
					continue;
				}
				if (receivedJMatcherMessage.getType() == JMatcherClientMessageType.CONNECT_REQUEST) {
					JMatcherClientUtil.sendJMatcherClientMessage(this.socket, JMatcherClientMessageType.GOT_CONNECT_REQUEST, this.name, connectionTargetHost);
					continue;
				}
				if (receivedJMatcherMessage.getType() == JMatcherClientMessageType.ENTRY_CLIENT_IS_FULL) {
					return false;
				}
				if (receivedJMatcherMessage.getType() == JMatcherClientMessageType.GOT_CONNECT_REQUEST) {
					this.connectingHost = connectionTargetHost;
					this.connectingHost.setName(receivedJMatcherMessage.getSenderName());
					return true;
				}
			}
		} catch (SocketTimeoutException e) {
			// one of the end conditions
		}
		return false;
	}

	private Host getTargetHostFromServer(int key) {
		for (int i = 0; i < this.retryCount; i++) {
			try {
				JMatcherClientUtil.sendUDPRequest(this.socket, new ConnectionRequest(Integer.valueOf(key)), new InetSocketAddress(this.jmatcherHost, this.port));
				final ConnectionResponse response = (ConnectionResponse) JMatcherClientUtil.receiveUDPResponse(this.socket, this.receiveBuffSize);
				return response.getHost();
			} catch (IOException | NullPointerException | ClassCastException e) {
				// failed
			}
		}
		return null;
	}

	private JMatcherClientMessage tryToReceiveJMatcherMessageFrom(InetSocketAddress hostAddress) throws IOException {
		final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(this.socket, this.receiveBuffSize);
		if (JMatcherClientUtil.packetCameFrom(hostAddress, packet) == false) {
			return null;
		}
		return JMatcherClientUtil.getJMatcherMessageFrom(packet);
	}

	/**
	 * 
	 */
	private void close() {
		JMatcherClientUtil.close(this.socket);
		this.socket = null;
		this.connectingHost = null;
	}

	/**
	 * @throws IOException
	 *             thrown if failed to communicate with other
	 */
	public void cancelConnection() throws IOException {
		if (this.socket == null) {
			return;
		}
		final InetSocketAddress hostAddress = new InetSocketAddress(this.connectingHost.getAddress(), this.connectingHost.getPort());
		for (int i = 0; i < this.retryCount; i++) {
			JMatcherClientUtil.sendJMatcherClientMessage(this.socket, JMatcherClientMessageType.CANCEL, this.name, this.connectingHost);
			try {
				for (int j = 0; j < maxCountOfReceivePacketsAtOneTime; j++) {
					if (this.tryToReceiveJMatcherMessageFrom(hostAddress).getType() == JMatcherClientMessageType.CANCELLED) {
						this.close(); // successful end
						return;
					}
				}
			} catch (SocketTimeoutException e) {
				// one of the end conditions
			}
		}
		this.close(); // force end
		return;
	}

	/**
	 * @param message
	 * @return true if succeed in sending
	 * @throws IOException
	 */
	public boolean sendMessage(String message) {
		if (this.socket == null || this.connectingHost == null || message == null || this.socket.isClosed()) {
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
	 * @return message from connectingHost
	 */
	public String receiveMessage() {
		if (this.socket == null || this.connectingHost == null || this.socket.isClosed()) {
			return null;
		}
		try {
			for (int i = 0; i < this.retryCount; i++) {
				final String receiveMessage = JMatcherClientUtil.receiveMessage(this.socket, this.receiveBuffSize);
				if (isNotJMatcherClientMessage(receiveMessage)) {
					return receiveMessage;
				}
			}
			System.err.println("JMatcherConnectionClient : abnormal state"); //$NON-NLS-1$
			return null;
		} catch (IOException e) {
			return null;
		}
	}

	private static boolean isNotJMatcherClientMessage(final String receiveMessage) {
		return JMatcherClientMessage.deserialize(receiveMessage) == null;
	}
}
