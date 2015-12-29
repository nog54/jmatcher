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

	private String jmatcherHost;
	private int port;
	private int retryCount;

	private DatagramSocket socket;
	private Host connectingHost;

	private static final int defalutRetryCount = 2;
	private static final int buffSize = 128;
	private static final int defaultUdpSocketTimeoutMillSec = 1000;
	private static final int maxCountOfReceivePacketsAtOneTime = 20;

	/**
	 * @param host
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherConnectionClient(String host) {
		this(host, JMatcher.PORT);
	}

	/**
	 * @param host
	 * @param port
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherConnectionClient(String host, int port) {
		this.jmatcherHost = host;
		this.port = port;
		this.retryCount = defalutRetryCount;
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

	@SuppressWarnings("static-method")
	protected void setupUDPSocket(final DatagramSocket udpSocket) throws SocketException {
		// overridden when configure the option of udp-socket
		udpSocket.setSoTimeout(defaultUdpSocketTimeoutMillSec);
	}

	/**
	 * @param message
	 * @return true if succeeded in sending but it doesn't mean whether reach
	 * @throws IOException
	 */
	public boolean sendMessageToConnectingHost(String message) {
		if (this.socket == null || this.connectingHost == null) {
			return false;
		}
		try {
			JMatcherClientUtil.sendMessage(this.socket, message, this.connectingHost);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * @return message from connecting host or null if failed to receive
	 */
	public String receiveMessageFromConnectingHost() {
		if (this.socket == null || this.connectingHost == null) {
			return null;
		}
		try {
			for (int i = 0; i < maxCountOfReceivePacketsAtOneTime; i++) {
				final DatagramPacket packet = this.tryToReceiveUDPPacketFrom(this.connectingHost);
				if (packet != null) {
					return JMatcherClientUtil.getMessageFrom(packet);
				}
			}
		} catch (IOException e) {
			// socket timeout or another io error
		}
		return null;

	}

	/**
	 * @param key
	 * @return true if success
	 * @throws IOException
	 *             thrown if failed to communicate with other
	 */
	public boolean connect(int key) throws IOException {
		this.socket = new DatagramSocket();
		this.setupUDPSocket(this.socket);
		final Host connectionTargetHost = this.getTargetHostFromServer(key);
		if (connectionTargetHost == null) {
			return false;
		}
		final InetSocketAddress connectionTargetHostAddress = new InetSocketAddress(connectionTargetHost.getAddress(), connectionTargetHost.getPort());

		for (int i = 0; i < this.retryCount; i++) {
			JMatcherClientUtil.sendJMatcherClientMessage(this.socket, JMatcherClientMessage.CONNECT_REQUEST, connectionTargetHost);
			try {
				for (int j = 0; j < maxCountOfReceivePacketsAtOneTime; j++) {
					final DatagramPacket packet = this.tryToReceiveUDPPacketFrom(connectionTargetHostAddress);
					if (JMatcherClientUtil.getJMatcherMessageFrom(packet) == JMatcherClientMessage.CONNECT_REQUEST) {
						JMatcherClientUtil.sendJMatcherClientMessage(this.socket, JMatcherClientMessage.GOT_CONNECT_REQUEST, connectionTargetHost);
					} else if (JMatcherClientUtil.getJMatcherMessageFrom(packet) == JMatcherClientMessage.GOT_CONNECT_REQUEST) {
						this.connectingHost = connectionTargetHost;
						return true;
					}
				}
			} catch (SocketTimeoutException e) {
				// one of the end conditions
			}
		}
		this.close();
		return false;
	}

	private Host getTargetHostFromServer(int key) {
		for (int i = 0; i < this.retryCount; i++) {
			try {
				JMatcherClientUtil.sendUDPRequest(this.socket, new ConnectionRequest(Integer.valueOf(key)), new InetSocketAddress(this.jmatcherHost, this.port));
				final ConnectionResponse response = (ConnectionResponse) JMatcherClientUtil.receiveUDPResponse(this.socket, buffSize);
				return response.getHost();
			} catch (IOException | NullPointerException | ClassCastException e) {
				// failed
			}
		}
		return null;
	}

	private DatagramPacket tryToReceiveUDPPacketFrom(Host host) throws IOException {
		return this.tryToReceiveUDPPacketFrom(new InetSocketAddress(host.getAddress(), host.getPort()));
	}

	private DatagramPacket tryToReceiveUDPPacketFrom(InetSocketAddress hostAddress) throws IOException {
		final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(this.socket, buffSize);
		if (JMatcherClientUtil.packetCameFrom(hostAddress, packet) == false) {
			return null;
		}
		return packet;
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
			JMatcherClientUtil.sendJMatcherClientMessage(this.socket, JMatcherClientMessage.CANCEL, this.connectingHost);
			try {
				for (int j = 0; j < maxCountOfReceivePacketsAtOneTime; j++) {
					final DatagramPacket packet = this.tryToReceiveUDPPacketFrom(hostAddress);
					if (JMatcherClientUtil.getJMatcherMessageFrom(packet) == JMatcherClientMessage.CANCELLED) {
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

	@Override
	protected void finalize() {
		this.close();
	}
}
