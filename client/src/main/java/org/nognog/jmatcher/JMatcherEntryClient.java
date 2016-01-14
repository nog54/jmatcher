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
import java.util.concurrent.LinkedBlockingQueue;

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

	private String jmatcherHost;
	private int port;
	private int retryCount = defalutRetryCount;
	protected Thread communicationThread;
	protected boolean isStoppingCommunication;

	private Socket tcpSocket;
	private ObjectInputStream ois;
	private ObjectOutputStream oos;
	private DatagramSocket udpSocket;
	private int udpReceiveBuffSize = defaultBuffSize;

	// knownHosts ⊇ requestingHosts ⊇ connectingHosts
	private Set<Host> knownHosts;
	private Set<Host> requestingHosts;
	private Set<Host> connectingHosts;
	private Map<Host, InetSocketAddress> socketAddressCache;

	private Map<Host, BlockingQueue<String>> receivedMessages;

	private Set<JMatcherEntryClientObserver> observers;

	static final int defalutRetryCount = 2;
	static final int defaultBuffSize = Math.max(256, JMatcherClientMessage.buffSizeToReceiveSerializedMessage);
	static final int defaultUdpSocketTimeoutMillSec = 1000; // [msec]
	static final long intervalToUpdateRequestingHosts = 2000; // [msec]

	/**
	 * @param name
	 * @param jmatcherHost
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherEntryClient(String name, String jmatcherHost) {
		this(name, jmatcherHost, JMatcher.PORT);
	}

	/**
	 * @param name
	 * @param jmatcherHost
	 * @param port
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherEntryClient(String name, String jmatcherHost, int port) {
		this.setName(name);
		this.jmatcherHost = jmatcherHost;
		this.port = port;
		this.knownHosts = new HashSet<>();
		this.requestingHosts = new HashSet<>();
		this.connectingHosts = new HashSet<>();
		this.socketAddressCache = new HashMap<>();
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
	 * @return the jmatcherHost
	 */
	public String getJmatcherHost() {
		return this.jmatcherHost;
	}

	/**
	 * @param jmatcherHost
	 *            the jmatcherHost to set
	 */
	public void setJMatcherHost(String jmatcherHost) {
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
		if (this.isStoppingCommunication) {
			this.waitForCommunicationThread();
		}
		if (this.isCommunicating()) {
			return null;
		}
		for (int i = 0; i < this.retryCount; i++) {
			this.closeAllConnections();
			try {
				this.createTCPConnection();
				final Integer keyNumber = this.makePreEntry();
				if (keyNumber == null) {
					return null;
				}
				this.createUDPConnection();
				final boolean enabledEntry = this.enableEntry(keyNumber);
				if (enabledEntry) {
					this.startCommunicationThread();
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

	private void waitForCommunicationThread() {
		try {
			this.communicationThread.join(defaultUdpSocketTimeoutMillSec * 2);
		} catch (InterruptedException | NullPointerException e) {
			// end
		}
	}

	private void createTCPConnection() throws UnknownHostException, IOException, SocketException {
		this.tcpSocket = new Socket(this.jmatcherHost, this.port);
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

	private void createUDPConnection() throws SocketException {
		this.udpSocket = new DatagramSocket();
		this.udpSocket.setSoTimeout(defaultUdpSocketTimeoutMillSec);
		this.setupUDPSocket(this.udpSocket);
	}

	private boolean enableEntry(final Integer keyNumber) throws IOException, ClassNotFoundException {
		JMatcherClientUtil.sendUDPRequest(this.udpSocket, new EnableEntryRequest(keyNumber), new InetSocketAddress(this.jmatcherHost, this.port));
		final TCPResponse response = (TCPResponse) this.ois.readObject();
		if (response == PlainTCPResponse.COMPLETE_ENTRY) {
			return true;
		}
		return false;
	}

	/**
	 * close all connections
	 */
	public void closeAllConnections() {
		this.closeTCPConnection();
		if (this.communicationThread != null) {
			this.isStoppingCommunication = true;
		}
		this.closeUDPConnection();
		this.requestingHosts.clear();
		final int prevSizeOfConnectingHosts = this.connectingHosts.size();
		this.connectingHosts.clear();
		this.socketAddressCache.clear();
		this.receivedMessages.clear();
		if (prevSizeOfConnectingHosts != 0) {
			this.notifyObservers(UpdateEvent.CLEAR, null);
		}
	}

	private void closeTCPConnection() {
		JMatcherClientUtil.close(this.ois);
		JMatcherClientUtil.close(this.oos);
		JMatcherClientUtil.close(this.tcpSocket);
		this.ois = null;
		this.oos = null;
		this.tcpSocket = null;
	}

	private void closeUDPConnection() {
		JMatcherClientUtil.close(this.udpSocket);
		this.udpSocket = null;
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
		return this.communicationThread != null;
	}

	/**
	 * request to stop invitation
	 */
	public void stopInvitation() {
		if (!this.isInviting()) {
			return;
		}
		this.closeTCPConnection();
	}

	private void startCommunicationThread() {
		this.communicationThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					JMatcherEntryClient.this.doCommunicationLoop();
				} finally {
					JMatcherEntryClient.this.communicationThread = null;
					JMatcherEntryClient.this.isStoppingCommunication = false;
				}
			}
		});
		this.communicationThread.start();
	}

	protected void doCommunicationLoop() {
		try {
			long lastUpdatedTime = 0;
			while (!this.isStoppingCommunication) {
				if (this.isInviting() && this.isTheTimeToUpdateRuestingHosts(lastUpdatedTime)) {
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
					this.requestingHosts.add(newRequestingHost);
					this.knownHosts.add(newRequestingHost);
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
		final Set<Host> requestingNotConnectingHosts = this.getRequestingNotConnectingHosts();
		for (Host requestingNotConnectingHost : requestingNotConnectingHosts) {
			JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.CONNECT_REQUEST, this.name, requestingNotConnectingHost);
		}
	}

	private Set<Host> getRequestingNotConnectingHosts() {
		final Set<Host> result = new HashSet<>(this.requestingHosts);
		result.removeAll(this.connectingHosts);
		return result;
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
			this.receivedMessages.remove(from);
			this.requestingHosts.remove(from);
			if (this.connectingHosts.remove(from)) {
				this.notifyObservers(UpdateEvent.REMOVE, from);
			}
			JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.CANCELLED, this.name, from);
		} else if (jmatcherClientMessage.getType() == JMatcherClientMessageType.GOT_CONNECT_REQUEST && this.requestingHosts.contains(from)) {
			if (this.connectingHosts.add(from)) {
				from.setName(jmatcherClientMessage.getSenderName());
				this.notifyObservers(UpdateEvent.ADD, from);
			}
			if (this.receivedMessages.get(from) == null) {
				this.receivedMessages.put(from, new LinkedBlockingQueue<String>());
			}
			JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.GOT_CONNECT_REQUEST, this.name, from);
		}
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
		for (Host knownHost : this.knownHosts) {
			final InetSocketAddress hostAddress = this.socketAddressCache.get(knownHost);
			if (hostAddress == null) {
				continue;
			}
			if (JMatcherClientUtil.packetCameFrom(hostAddress, packet)) {
				return knownHost;
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

	@Override
	public void close() {
		this.closeAllConnections();
	}
}
