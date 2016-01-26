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

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;

import org.nognog.jmatcher.Host;
import org.nognog.jmatcher.JMatcher;
import org.nognog.jmatcher.SpecialHostAddress;
import org.nognog.jmatcher.tcp.request.PlainTCPRequest;
import org.nognog.jmatcher.tcp.response.CheckConnectionResponse;
import org.nognog.jmatcher.tcp.response.PlainTCPResponse;
import org.nognog.jmatcher.tcp.response.PreEntryResponse;
import org.nognog.jmatcher.tcp.response.TCPResponse;
import org.nognog.jmatcher.udp.request.EnableEntryRequest;

/**
 * Entry Client of JMatcher class.
 * 
 * @author goshi 2015/11/27
 */
public class JMatcherEntryClient implements Closeable {

	private String name;

	private String jmatcherServer;
	private int jmatcherServerPort;
	private int retryCount = defalutRetryCount;
	private int maxSizeOfConnectingHosts = Integer.MAX_VALUE;
	private int portTellerPort = JMatcher.PORT;
	protected Thread communicationThread;
	protected Thread portTellerThread;

	private Socket tcpSocket;
	private Integer lastEntryKey;
	private ObjectInputStream ois;
	private ObjectOutputStream oos;
	private DatagramSocket udpSocket;
	private int udpReceiveBuffSize = defaultBuffSize;

	// these sets are designed to be
	// requestingHosts ∩ connectingHosts = Φ.
	private CopyOnWriteArraySet<Host> requestingHosts;
	private Set<Host> connectingHosts;
	private ConcurrentMap<Host, InetSocketAddress> socketAddressCache;

	private Map<Host, BlockingQueue<String>> receivedMessages;

	private Set<JMatcherEntryClientObserver> observers;

	static final int defalutRetryCount = 2;
	static final int defaultBuffSize = Math.max(256, JMatcherClientMessage.buffSizeToReceiveSerializedMessage);
	static final int defaultUdpSocketTimeoutMillSec = 1000; // [msec]
	static final long intervalToUpdateRequestingHosts = 2000; // [msec]

	/**
	 * @param name
	 * @param jmatcherServer
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherEntryClient(String name, String jmatcherServer) {
		this(name, jmatcherServer, JMatcher.PORT);
	}

	/**
	 * @param name
	 * @param jmatcherServer
	 * @param port
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherEntryClient(String name, String jmatcherServer, int port) {
		this.setName(name);
		this.jmatcherServer = jmatcherServer;
		this.jmatcherServerPort = port;
		this.requestingHosts = new CopyOnWriteArraySet<>();
		this.connectingHosts = new HashSet<>();
		this.socketAddressCache = new ConcurrentHashMap<>();
		this.receivedMessages = new HashMap<>();
		this.observers = new HashSet<>();
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
	 * @return the jmatcherServer
	 */
	public String getJmatcherServer() {
		return this.jmatcherServer;
	}

	/**
	 * @param jmatcherServer
	 *            the jmatcherServer to set
	 */
	public void setJMatcherServer(String jmatcherServer) {
		this.jmatcherServer = jmatcherServer;
	}

	/**
	 * @return the port
	 */
	public int getJMatcherServerPort() {
		return this.jmatcherServerPort;
	}

	/**
	 * @param port
	 *            the port to set
	 */
	public void setJMatcherServerPort(int port) {
		this.jmatcherServerPort = port;
	}

	/**
	 * @return connecting hosts
	 */
	public Set<Host> getConnectingHosts() {
		return new HashSet<>(this.connectingHosts);
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
	 * @return the maxSizeOfConnectingHosts
	 */
	public int getMaxSizeOfConnectingHosts() {
		return this.maxSizeOfConnectingHosts;
	}

	/**
	 * @param maxSizeOfConnectingHosts
	 *            the maxNumberOfConnectingHosts to set
	 */
	public void setMaxSizeOfConnectingHosts(int maxSizeOfConnectingHosts) {
		this.maxSizeOfConnectingHosts = maxSizeOfConnectingHosts;
	}

	/**
	 * @return the portTellerPort
	 */
	public int getPortTellerPort() {
		return this.portTellerPort;
	}

	/**
	 * It has to be called before{@link #startInvitation()}
	 * 
	 * @param portTellerPort
	 *            the portTellerPort to set
	 */
	public void setPortTellerPort(int portTellerPort) {
		this.portTellerPort = portTellerPort;
	}

	@SuppressWarnings("unused")
	protected void setupTCPSocket(final Socket tcpSocket) throws SocketException {
		// overridden when configure the option of tcp-socket
	}

	@SuppressWarnings("unused")
	protected void setupUDPSocket(final DatagramSocket udpSocket) throws SocketException {
		// overridden when configure the option of udp-socket
	}

	/**
	 * @return the udpReceiveBuffSize
	 */
	public int getUDPReceiveBuffSize() {
		return this.udpReceiveBuffSize;
	}

	protected Integer getCurrentEntryKey() {
		if (this.isInviting()) {
			return this.lastEntryKey;
		}
		return null;
	}

	/**
	 * @return true if this is inviting other peers
	 */
	public boolean isInviting() {
		return this.tcpSocket != null && !this.tcpSocket.isClosed();
	}

	/**
	 * @return true if this is communicating
	 */
	public boolean isCommunicating() {
		return this.communicationThread != null || this.portTellerThread != null;
	}

	/**
	 * set UDPReceiveBuffSize, but the min value is restricted by
	 * {@link JMatcherClientMessage#buffSizeToReceiveSerializedMessage}}
	 * 
	 * @param udpReceiveBuffSize
	 *            the udpReceiveBuffSize to set
	 */
	public void setUDPReceiveBuffSize(int udpReceiveBuffSize) {
		this.udpReceiveBuffSize = Math.max(udpReceiveBuffSize, JMatcherClientMessage.buffSizeToReceiveSerializedMessage);
	}

	/**
	 * @param observer
	 */
	public void addObserver(JMatcherEntryClientObserver observer) {
		this.observers.add(observer);
	}

	/**
	 * @param observer
	 */
	public void removeObserver(JMatcherEntryClientObserver observer) {
		this.observers.remove(observer);
	}

	private void notifyObservers(UpdateEvent event, Host target) {
		for (JMatcherEntryClientObserver observer : this.observers) {
			observer.updateConnectingHosts(new HashSet<>(this.connectingHosts), event, target);
		}
	}

	/**
	 * @return entry key number, or null is returned if it has been started or
	 *         failed to get entry key from the server
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public synchronized Integer startInvitation() throws IOException {
		if (this.isCommunicating()) {
			return null;
		}
		for (int i = 0; i < this.retryCount; i++) {
			this.closeAllConnections();
			try {
				this.setupTCPConnection();
				final Integer keyNumber = this.makePreEntry();
				if (keyNumber == null) {
					return null;
				}
				this.setupUDPConnection();
				if (this.enableEntry(keyNumber)) {
					this.startPortTellerThread();
					if (this.portTellerThread == null) {
						return null;
					}
					this.startCommunicationThread();
					this.lastEntryKey = keyNumber;
					return keyNumber;
				}
			} catch (IOException | ClassNotFoundException | ClassCastException e) {
				// failed
			} catch (Exception e) {
				System.err.println("unexpected expection occured"); //$NON-NLS-1$
				e.printStackTrace();
			}
		}
		this.closeAllConnections();
		throw new IOException("failed to connect to the server"); //$NON-NLS-1$
	}

	private void setupTCPConnection() throws UnknownHostException, IOException, SocketException {
		this.tcpSocket = new Socket(this.jmatcherServer, this.jmatcherServerPort);
		this.setupTCPSocket(this.tcpSocket);
		this.oos = new ObjectOutputStream(this.tcpSocket.getOutputStream());
		this.ois = new ObjectInputStream(this.tcpSocket.getInputStream());
	}

	private Integer makePreEntry() throws IOException, ClassNotFoundException {
		this.oos.writeObject(PlainTCPRequest.ENTRY);
		this.oos.flush();
		final TCPResponse entryResponse = (TCPResponse) this.ois.readObject();
		if (entryResponse == PlainTCPResponse.FAILURE) {
			return null;
		}
		final Integer keyNumber = ((PreEntryResponse) entryResponse).getKeyNumber();
		return keyNumber;
	}

	private void setupUDPConnection() throws SocketException {
		this.udpSocket = new DatagramSocket();
		this.udpSocket.setSoTimeout(defaultUdpSocketTimeoutMillSec);
		this.setupUDPSocket(this.udpSocket);
	}

	private boolean enableEntry(final Integer keyNumber) throws IOException, ClassNotFoundException {
		JMatcherClientUtil.sendUDPRequest(this.udpSocket, new EnableEntryRequest(keyNumber), new InetSocketAddress(this.jmatcherServer, this.jmatcherServerPort));
		final TCPResponse response = (TCPResponse) this.ois.readObject();
		if (response == PlainTCPResponse.COMPLETE_ENTRY) {
			return true;
		}
		return false;
	}

	/**
	 * close all connections
	 * 
	 * @throws IOException
	 */
	public void closeAllConnections() {
		this.closeTCPCommunication();
		this.closeUDPCommunication();
		this.requestingHosts.clear();
		final int prevSizeOfConnectingHosts = this.connectingHosts.size();
		this.connectingHosts.clear();
		this.socketAddressCache.clear();
		this.receivedMessages.clear();
		if (prevSizeOfConnectingHosts != 0) {
			this.notifyObservers(UpdateEvent.CLEAR, null);
		}
		this.waitForCommunicationThread();
		this.waitForPortTellerThread();
	}

	private void closeTCPCommunication() {
		JMatcherClientUtil.close(this.ois);
		JMatcherClientUtil.close(this.oos);
		JMatcherClientUtil.close(this.tcpSocket);
		this.ois = null;
		this.oos = null;
		this.tcpSocket = null;
	}

	private void closeUDPCommunication() {
		JMatcherClientUtil.close(this.udpSocket);
		this.udpSocket = null;
	}

	private void waitForCommunicationThread() {
		try {
			this.communicationThread.join(defaultUdpSocketTimeoutMillSec * 2);
		} catch (InterruptedException | NullPointerException e) {
			// end
		}
	}

	private void waitForPortTellerThread() {
		try {
			this.portTellerThread.join(defaultUdpSocketTimeoutMillSec * 2);
		} catch (InterruptedException | NullPointerException e) {
			// end
		}
	}

	/**
	 * request to stop invitation
	 */
	public void stopInvitation() {
		this.closeTCPCommunication();
	}

	private void startPortTellerThread() {
		try {
			@SuppressWarnings("resource")
			final DatagramSocket portTellerSocket = new DatagramSocket(this.portTellerPort);
			portTellerSocket.setSoTimeout(defaultUdpSocketTimeoutMillSec);
			this.portTellerThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						JMatcherEntryClient.this.performTellerLoop(portTellerSocket);
					} finally {
						portTellerSocket.close();
						JMatcherEntryClient.this.portTellerThread = null;
					}
				}
			});
			this.portTellerThread.start();
		} catch (SocketException e) {
			e.printStackTrace();
			return;
		}
	}

	protected void performTellerLoop(final DatagramSocket portTellerSocket) {
		while (JMatcherEntryClient.this.isInviting()) {
			try {
				final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(portTellerSocket, JMatcherEntryClient.this.getUDPReceiveBuffSize());
				final String message = JMatcherClientUtil.getMessageFrom(packet);
				final Integer sentKey = Integer.valueOf(message);
				final Integer currentEntryKey = JMatcherEntryClient.this.getCurrentEntryKey();
				if (sentKey.equals(currentEntryKey)) {
					final Host senderHost = new Host(packet.getAddress().getHostAddress(), packet.getPort());
					final InetSocketAddress senderSocketAddress = new InetSocketAddress(senderHost.getAddress(), senderHost.getPort());
					JMatcherClientUtil.sendMessage(portTellerSocket, String.valueOf(JMatcherEntryClient.this.getUDPSocket().getLocalPort()), senderHost);
					this.requestingHosts.add(senderHost);
					this.socketAddressCache.put(senderHost, senderSocketAddress);
				}
			} catch (SocketTimeoutException | NumberFormatException e) {
				// just timeout or received invalid message
			} catch (Exception e) {
				break;
			}
		}
	}

	private void startCommunicationThread() {
		this.communicationThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					JMatcherEntryClient.this.performCommunicationLoop();
				} finally {
					JMatcherEntryClient.this.communicationThread = null;
				}
			}
		});
		this.communicationThread.start();
	}

	protected void performCommunicationLoop() {
		try {
			long lastUpdatedTime = 0;
			while (this.udpSocket != null) {
				if (this.isInviting() && this.connectingHosts.size() < this.maxSizeOfConnectingHosts && this.isTheTimeToUpdateRuestingHosts(lastUpdatedTime)) {
					try {
						this.updateRequestingHosts();
					} catch (IOException e) {
						// closed tcp socket while updating
						// (stopped inviting while updating)
						continue;
					}
					lastUpdatedTime = System.currentTimeMillis();
					this.sendHolePunchingMessage();
				}
				try {
					this.receivePacketAndHandle();
				} catch (IOException e) {
					throw e;
				} catch (Exception e) {
					// unexpected exception occured
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			// IOException is mainly caused by closing socket
		}
	}

	private void updateRequestingHosts() throws IOException {
		this.oos.writeObject(PlainTCPRequest.CHECK_CONNECTION_REQUEST);
		this.oos.flush();
		try {
			final CheckConnectionResponse response = (CheckConnectionResponse) this.ois.readObject();
			final Host[] newRequestingHosts = response.getRequestingHosts();
			if (newRequestingHosts != null) {
				for (Host newRequestingHost : newRequestingHosts) {
					if (SpecialHostAddress.ON_INTERNAL_NETWORK_HOST.equals(newRequestingHost.getAddress())) {
						continue;
					}
					this.requestingHosts.add(newRequestingHost);
					this.socketAddressCache.put(newRequestingHost, new InetSocketAddress(newRequestingHost.getAddress(), newRequestingHost.getPort()));
				}
			}
		} catch (ClassCastException e) {
			// ignore when got an invalid response from te server
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("unexpected fatal expection occured", e); //$NON-NLS-1$
		}
	}

	@SuppressWarnings("static-method")
	private boolean isTheTimeToUpdateRuestingHosts(long lastUpdatedTime) {
		return System.currentTimeMillis() - lastUpdatedTime > intervalToUpdateRequestingHosts;
	}

	private void sendHolePunchingMessage() throws IOException {
		for (Host requestingHost : this.requestingHosts) {
			JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.CONNECT_REQUEST, this.name, requestingHost);
		}
	}

	/**
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	private void receivePacketAndHandle() throws IOException {
		final DatagramPacket packet;
		try {
			packet = JMatcherClientUtil.receiveUDPPacket(this.udpSocket, this.udpReceiveBuffSize);
		} catch (SocketTimeoutException e) {
			return;
		}
		final Host from = this.specifyHost(packet);
		if (from == null) {
			return;
		}
		final JMatcherClientMessage jmatcherClientMessage = JMatcherClientUtil.getJMatcherMessageFrom(packet);
		// in case JmatcherClientMessage isn't sent
		if (jmatcherClientMessage == null) {
			this.storeMessageIfFromConnectingHost(packet, from);
			return;
		}
		if (jmatcherClientMessage.getType() == JMatcherClientMessageType.CANCEL) {
			this.handleCancelMessage(from);
			return;
		}
		if (jmatcherClientMessage.getType() == JMatcherClientMessageType.GOT_CONNECT_REQUEST) {
			this.handleGotConnectRequestMessage(from, jmatcherClientMessage);
			return;
		}
	}

	private void handleCancelMessage(final Host from) throws IOException {
		this.requestingHosts.remove(from);
		this.socketAddressCache.remove(from);
		if (this.connectingHosts.remove(from)) {
			this.receivedMessages.remove(from);
			this.notifyObservers(UpdateEvent.REMOVE, from);
		}
		JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.CANCELLED, this.name, from);
	}

	private void handleGotConnectRequestMessage(final Host from, final JMatcherClientMessage jmatcherClientMessage) throws IOException {
		if (this.connectingHosts.size() >= this.maxSizeOfConnectingHosts) {
			JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.ENTRY_CLIENT_IS_FULL, this.name, from);
			return;
		}
		if (this.connectingHosts.add(from)) {
			from.setName(jmatcherClientMessage.getSenderName());
			this.requestingHosts.remove(from);
			this.notifyObservers(UpdateEvent.ADD, from);
			this.receivedMessages.put(from, new LinkedBlockingQueue<String>());
		}
		JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.GOT_CONNECT_REQUEST, this.name, from);
	}

	private void storeMessageIfFromConnectingHost(final DatagramPacket packet, final Host from) {
		if (this.connectingHosts.contains(from)) {
			final String receivedMessage = JMatcherClientUtil.getMessageFrom(packet);
			if (receivedMessage != null) {
				final boolean added = this.receivedMessages.get(from).offer(receivedMessage);
				if (added) {
					synchronized (from) {
						from.notifyAll();
					}
				}
			}
		}
	}

	/**
	 * @param packet
	 * @return host which sent the packet, or null if packet is from unkrown
	 *         host
	 */
	private Host specifyHost(DatagramPacket packet) {
		final Host connectingHost = this.lookupIn(this.connectingHosts, packet);
		if (connectingHost != null) {
			return connectingHost;
		}
		final Host requestingHost = this.lookupIn(this.requestingHosts, packet);
		if (requestingHost != null) {
			return requestingHost;
		}
		return null;
	}

	private Host lookupIn(Set<Host> hosts, DatagramPacket packet) {
		for (Host host : hosts) {
			final InetSocketAddress hostAddress = this.socketAddressCache.get(host);
			if (hostAddress == null) {
				continue;
			}
			if (JMatcherClientUtil.packetCameFrom(hostAddress, packet)) {
				return host;
			}
		}
		return null;
	}

	/**
	 * Receive message from argument host. The argument host have to be
	 * contained in a set which is obtained by {@link #getConnectingHosts()}
	 * 
	 * @param host
	 * @return message from host
	 */
	public String receiveMessageFrom(Host host) {
		final String alreadyReceivedMessage = this.receivedMessages.get(host).poll();
		if (alreadyReceivedMessage != null || this.udpSocket == null) {
			return alreadyReceivedMessage;
		}

		synchronized (host) {
			try {
				host.wait(this.udpSocket.getSoTimeout());
			} catch (SocketException | InterruptedException e) {
				return null;
			}
		}
		return this.receivedMessages.get(host).poll();
	}

	/**
	 * @param host
	 * @param message
	 * @return true if succeed in sending
	 */
	public boolean sendMessageTo(Host host, String message) {
		if (!this.connectingHosts.contains(host)) {
			return false;
		}
		final InetSocketAddress address = this.socketAddressCache.get(host);
		if (address == null) {
			return false;
		}
		try {
			JMatcherClientUtil.sendMessage(this.udpSocket, message, address);
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	/**
	 * Get using udpSocket. You should be careful when you use this method. It
	 * might cause unexpected error.
	 * 
	 * @return udpSocket
	 */
	public DatagramSocket getUDPSocket() {
		return this.udpSocket;
	}

	@Override
	public void close() {
		this.closeAllConnections();
	}
}
