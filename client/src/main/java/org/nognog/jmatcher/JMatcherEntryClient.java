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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.nognog.jmatcher.tcp.request.PlainTCPRequest;
import org.nognog.jmatcher.tcp.response.CheckConnectionResponse;
import org.nognog.jmatcher.tcp.response.PlainTCPResponse;
import org.nognog.jmatcher.tcp.response.PreEntryResponse;
import org.nognog.jmatcher.tcp.response.TCPResponse;
import org.nognog.jmatcher.udp.request.EnableEntryRequest;

/**
 * Entry Client class. This class isn't thread-safe
 * 
 * @author goshi 2015/11/27
 */
public class JMatcherEntryClient {

	private String host;
	private int port;
	private int retryCount;

	private Socket tcpSocket;
	private ObjectInputStream ois;
	private ObjectOutputStream oos;
	private DatagramSocket udpSocket;

	private Set<Host> requestingHosts;
	private Map<Host, InetSocketAddress> addresses;
	private Set<Host> connectedHosts;

	private static final int defalutRetryCount = 2;
	private static final int buffSize = 128;
	private static final int defaultUdpSocketTimeoutMillSec = 500;
	private static final int maxCountOfReceivePacketsAtOneTime = 20;

	/**
	 * @param host
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherEntryClient(String host) {
		this(host, JMatcher.PORT);
	}

	/**
	 * @param host
	 * @param port
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherEntryClient(String host, int port) {
		this.host = host;
		this.port = port;
		this.retryCount = defalutRetryCount;
		this.requestingHosts = new HashSet<>();
		this.connectedHosts = new HashSet<>();
		this.addresses = new HashMap<>();
	}

	/**
	 * @return the host
	 */
	public String getHost() {
		return this.host;
	}

	/**
	 * @param host
	 *            the host to set
	 */
	public void setHost(String host) {
		this.host = host;
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
	 * @return connected hosts
	 */
	public Set<Host> getConnectedHost() {
		return this.connectedHosts;
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

	protected void setupTCPSocket(final Socket tcpSocket) {
		// overridden when configure the option of tcp-socket
	}

	@SuppressWarnings("static-method")
	protected void setupUDPSocket(final DatagramSocket udpSocket) throws SocketException {
		// overridden when configure the option of udp-socket
		udpSocket.setSoTimeout(defaultUdpSocketTimeoutMillSec);
	}

	/**
	 * @return entry key number, or null is returned if failed to get entry key
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public Integer makeEntry() throws IOException {
		this.closeAllSockets();
		for (int i = 0; i < this.retryCount; i++) {
			try {
				this.tcpSocket = new Socket(this.host, this.port);
				this.setupTCPSocket(this.tcpSocket);
				this.oos = new ObjectOutputStream(this.tcpSocket.getOutputStream());
				this.ois = new ObjectInputStream(this.tcpSocket.getInputStream());
				this.oos.writeObject(PlainTCPRequest.ENTRY);
				this.oos.flush();
				final TCPResponse entryResponse = (TCPResponse) this.ois.readObject();
				if (entryResponse == PlainTCPResponse.FAILURE) {
					return null;
				}
				final Integer keyNumber = ((PreEntryResponse) entryResponse).getKeyNumber();
				this.udpSocket = new DatagramSocket();
				this.setupUDPSocket(this.udpSocket);
				JMatcherClientUtils.sendUDPRequest(this.udpSocket, new EnableEntryRequest(keyNumber), new InetSocketAddress(this.host, this.port));
				final TCPResponse response = (TCPResponse) this.ois.readObject();
				if (response == PlainTCPResponse.COMPLETE_ENTRY) {
					return keyNumber;
				}
			} catch (IOException | ClassNotFoundException | ClassCastException e) {
				// failed
			}
			this.closeAllSockets();
		}
		throw new IOException("failed to connect to the server"); //$NON-NLS-1$
	}

	private void closeAllSockets() {
		JMatcherClientUtils.close(this.ois);
		JMatcherClientUtils.close(this.oos);
		JMatcherClientUtils.close(this.tcpSocket);
		JMatcherClientUtils.close(this.udpSocket);
		this.ois = null;
		this.oos = null;
		this.tcpSocket = null;
		this.udpSocket = null;
		this.requestingHosts.clear();
		this.connectedHosts.clear();
		this.addresses.clear();
	}

	/**
	 * @return true if updated set of connected hosts
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public boolean updateConnectedHosts() throws IOException {
		if (this.isConnectingToJMatcherServer()) {
			this.updateRequestingHosts();
		}
		Set<Host> notConnectedHosts = this.getNotConnectedHosts();
		if (notConnectedHosts.isEmpty()) {
			return false;
		}
		// udp hole punching
		final int maxNumberOfTrying = 2;
		for (int i = 0; i < maxNumberOfTrying; i++) {
			for (Host notConnectedHost : notConnectedHosts) {
				JMatcherClientUtils.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessage.CONNECT, notConnectedHost);
			}
			this.handleResponses();
			notConnectedHosts = this.getNotConnectedHosts();
			if (notConnectedHosts.isEmpty()) {
				return true;
			}
		}
		return true;
	}

	private Set<Host> getNotConnectedHosts() {
		final Set<Host> notConnectedHosts = new HashSet<>(this.requestingHosts);
		notConnectedHosts.removeAll(this.connectedHosts);
		return notConnectedHosts;
	}

	private void updateRequestingHosts() throws IOException {
		this.oos.writeObject(PlainTCPRequest.CHECK_CONNECTION_REQUEST);
		this.oos.flush();
		try {
			final CheckConnectionResponse response = (CheckConnectionResponse) this.ois.readObject();
			final Host[] newRequestingHosts = response.getRequestingHosts();
			if (newRequestingHosts != null) {
				for (Host newRequestingHost : newRequestingHosts) {
					this.requestingHosts.add(newRequestingHost);
					this.addresses.put(newRequestingHost, new InetSocketAddress(newRequestingHost.getAddress(), newRequestingHost.getPort()));
				}
			}
		} catch (ClassCastException e) {
			// ignore when got an invalid response from te server
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("unexpected fatal expection occured", e); //$NON-NLS-1$
		}
	}

	private Set<Host> handleResponses() throws IOException {
		final Set<Host> responsedHosts = new HashSet<>();
		try {
			for (int i = 0; i < maxCountOfReceivePacketsAtOneTime; i++) {
				final DatagramPacket packet = JMatcherClientUtils.receiveUDPPacket(this.udpSocket, buffSize);
				final Host from = this.specifyHost(packet);
				final JMatcherClientMessage message = JMatcherClientUtils.getMessageFrom(packet);
				// put this case here in case previous response for cancelling
				// didn't reach.
				if (message == JMatcherClientMessage.CANCEL) {
					this.requestingHosts.remove(from);
					this.connectedHosts.remove(from);
					JMatcherClientUtils.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessage.CANCELLED, from);
					continue;
				}
				if (from == null) { // from unknown host
					continue;
				}
				if (message == JMatcherClientMessage.CONNECTED) {
					this.connectedHosts.add(from);
					JMatcherClientUtils.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessage.CONNECTED, from);
				}
			}
		} catch (SocketTimeoutException e) {
			// one of the end conditions
		}
		return responsedHosts;
	}

	/**
	 * @param packet
	 * @return host which sent the packet, or null if packet is from unkrown
	 *         host
	 */
	private Host specifyHost(DatagramPacket packet) {
		for (Host requestingHost : this.addresses.keySet()) {
			final InetSocketAddress hostAddress = this.addresses.get(requestingHost);
			if (JMatcherClientUtils.packetCameFrom(hostAddress, packet)) {
				return requestingHost;
			}
		}
		return null;
	}

	private boolean isConnectingToJMatcherServer() {
		return this.tcpSocket != null && this.oos != null && this.ois != null;
	}

	/**
	 * @return socket to communicate with requesting hosts, or null if there
	 *         aren't requesting hosts
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public DatagramSocket cancelEntry() throws IOException {
		if (this.isConnectingToJMatcherServer() == false) {
			return null;
		}
		this.oos.writeObject(PlainTCPRequest.CANCEL_ENTRY);
		this.closeTCPSocket();
		return this.udpSocket;
	}

	private void closeTCPSocket() {
		JMatcherClientUtils.close(this.ois);
		JMatcherClientUtils.close(this.oos);
		JMatcherClientUtils.close(this.tcpSocket);
		this.ois = null;
		this.oos = null;
		this.tcpSocket = null;
	}
}
