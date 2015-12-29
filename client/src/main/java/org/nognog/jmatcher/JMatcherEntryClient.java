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

	private String jmatcherHost;
	private int port;
	private int retryCount;

	private Socket tcpSocket;
	private ObjectInputStream ois;
	private ObjectOutputStream oos;
	private DatagramSocket udpSocket;

	private Set<Host> requestingHosts;
	private Map<Host, InetSocketAddress> addresses;
	private Set<Host> connectingHosts;

	static final int defalutRetryCount = 2;
	static final int buffSize = 128;
	static final int defaultUdpSocketTimeoutMillSec = 1000;
	static final int maxCountOfReceivePacketsAtOneTime = 20;

	/**
	 * @param jmatcherHost
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherEntryClient(String jmatcherHost) {
		this(jmatcherHost, JMatcher.PORT);
	}

	/**
	 * @param jmatcherHost
	 * @param port
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherEntryClient(String jmatcherHost, int port) {
		this.jmatcherHost = jmatcherHost;
		this.port = port;
		this.retryCount = defalutRetryCount;
		this.requestingHosts = new HashSet<>();
		this.connectingHosts = new HashSet<>();
		this.addresses = new HashMap<>();
	}

	/**
	 * @return the jmatcherHost
	 */
	public String getJmatcherHost() {
		return this.jmatcherHost;
	}

	/**
	 * @param jmatcherHost
	 *            the jmatcherHost to set
	 */
	public void setHost(String jmatcherHost) {
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
	 * @return connecting hosts
	 */
	public Set<Host> getConnectingHosts() {
		return this.connectingHosts;
	}

	/**
	 * @param host
	 * @return the address of host
	 */
	public InetSocketAddress getAddress(Host host) {
		return this.addresses.get(host);
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
		this.cancelEntry();
		this.closeAllCurrentSockets();
		for (int i = 0; i < this.retryCount; i++) {
			try {
				this.tcpSocket = new Socket(this.jmatcherHost, this.port);
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
				JMatcherClientUtil.sendUDPRequest(this.udpSocket, new EnableEntryRequest(keyNumber), new InetSocketAddress(this.jmatcherHost, this.port));
				final TCPResponse response = (TCPResponse) this.ois.readObject();
				if (response == PlainTCPResponse.COMPLETE_ENTRY) {
					return keyNumber;
				}
			} catch (IOException | ClassNotFoundException | ClassCastException e) {
				// failed
			}
			this.closeAllCurrentSockets();
		}
		throw new IOException("failed to connect to the server"); //$NON-NLS-1$
	}

	/**
	 * close all current sockets
	 */
	public void closeAllCurrentSockets() {
		JMatcherClientUtil.close(this.ois);
		JMatcherClientUtil.close(this.oos);
		JMatcherClientUtil.close(this.tcpSocket);
		JMatcherClientUtil.close(this.udpSocket);
		this.ois = null;
		this.oos = null;
		this.tcpSocket = null;
		this.udpSocket = null;
		this.requestingHosts.clear();
		this.connectingHosts.clear();
		this.addresses.clear();
	}

	/**
	 * @return true if updated set of connecting hosts
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public boolean updateConnectingHosts() throws IOException {
		if (this.isConnectingToJMatcherServer()) {
			this.updateRequestingHosts();
		} else {
			return false;
		}
		Set<Host> requestingNotConnectingHosts = this.getRequestingNotConnectingHosts();
		boolean updated = false;
		for (int i = 0; i < this.retryCount; i++) {
			for (Host requestingNotConnectingHost : requestingNotConnectingHosts) {
				JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessage.CONNECT_REQUEST, requestingNotConnectingHost);
			}
			updated |= this.handleResponses();
			if (requestingNotConnectingHosts.isEmpty() == false) {
				requestingNotConnectingHosts = this.getRequestingNotConnectingHosts();
			}
			if (requestingNotConnectingHosts.isEmpty()) {
				return updated;
			}
		}
		return updated;
	}

	private Set<Host> getRequestingNotConnectingHosts() {
		final Set<Host> result = new HashSet<>(this.requestingHosts);
		result.removeAll(this.connectingHosts);
		return result;
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

	private boolean handleResponses() throws IOException {
		boolean updatedConnectingHosts = false;
		try {
			for (int i = 0; i < maxCountOfReceivePacketsAtOneTime; i++) {
				final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(this.udpSocket, buffSize);
				final Host from = this.specifyHost(packet);
				final JMatcherClientMessage message = JMatcherClientUtil.getJMatcherMessageFrom(packet);
				// put this case here in case previous response for cancelling
				// didn't reach.
				if (message == JMatcherClientMessage.CANCEL) {
					this.requestingHosts.remove(from);
					updatedConnectingHosts |= this.connectingHosts.remove(from);
					JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessage.CANCELLED, from);
					continue;
				}
				if (from == null) { // from unknown host
					continue;
				}
				if (message == JMatcherClientMessage.GOT_CONNECT_REQUEST) {
					updatedConnectingHosts |= this.connectingHosts.add(from);
					JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessage.GOT_CONNECT_REQUEST, from);
				}
			}
		} catch (SocketTimeoutException e) {
			// one of the end conditions
		}
		return updatedConnectingHosts;
	}

	/**
	 * @param packet
	 * @return host which sent the packet, or null if packet is from unkrown
	 *         host
	 */
	private Host specifyHost(DatagramPacket packet) {
		for (Host requestingHost : this.addresses.keySet()) {
			final InetSocketAddress hostAddress = this.addresses.get(requestingHost);
			if (JMatcherClientUtil.packetCameFrom(hostAddress, packet)) {
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
		JMatcherClientUtil.close(this.ois);
		JMatcherClientUtil.close(this.oos);
		JMatcherClientUtil.close(this.tcpSocket);
		this.ois = null;
		this.oos = null;
		this.tcpSocket = null;
	}

	@Override
	protected void finalize() {
		this.closeAllCurrentSockets();
	}
}
