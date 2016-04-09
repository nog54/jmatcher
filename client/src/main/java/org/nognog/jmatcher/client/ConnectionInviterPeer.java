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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
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
 * This is a class to communicate with JMatcherConnectionClients. This class is
 * not thread-safe.
 * 
 * @author goshi 2015/11/27
 */
public class ConnectionInviterPeer implements Peer {

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
	private int receiveBuffSize = defaultBuffSize;
	private int udpSoTimeoutCache;

	private ReceivedMessageBuffer receivedMessageBuffer;

	// these sets are designed to be
	// requestingHosts ∩ connectingHosts = Φ.
	private CopyOnWriteArraySet<Host> requestingHosts;
	private CopyOnWriteArraySet<Host> connectingHosts;
	private ConcurrentMap<Host, InetSocketAddress> socketAddressCache;

	private Set<PeerObserver> observers;

	private Logger logger;

	static final int defalutRetryCount = 2;
	static final int defaultBuffSize = Math.max(256, JMatcherClientMessage.buffSizeToReceiveSerializedMessage);
	static final int defaultUdpSocketTimeoutMillSec = 1000; // [msec]
	static final long intervalToUpdateRequestingHosts = 2000; // [msec]

	/**
	 * @param name
	 * @param jmatcherServer
	 */
	public ConnectionInviterPeer(String name, String jmatcherServer) {
		this(name, jmatcherServer, JMatcher.PORT);
	}

	/**
	 * @param name
	 * @param jmatcherServer
	 * @param port
	 */
	public ConnectionInviterPeer(String name, String jmatcherServer, int port) {
		// it contains validation of the name
		this.setNameIfNotCommunicating(name);
		this.jmatcherServer = jmatcherServer;
		this.jmatcherServerPort = port;
		this.requestingHosts = new CopyOnWriteArraySet<>();
		this.connectingHosts = new CopyOnWriteArraySet<>();
		this.socketAddressCache = new ConcurrentHashMap<>();
		this.observers = new HashSet<>();
		this.receivedMessageBuffer = new ReceivedMessageBuffer();
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
	 * @return true if new name is set
	 */
	public boolean setNameIfNotCommunicating(String name) {
		if (this.isCommunicating()) {
			return false;
		}
		if (!JMatcherClientMessage.regardsAsValidName(name)) {
			final String message = new StringBuilder().append("name is too long : ").append(name).toString(); //$NON-NLS-1$
			throw new IllegalArgumentException(message);
		}
		this.name = name;
		return true;
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
	@Override
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
		// overridden when configure the option of this tcp-socket
	}

	@SuppressWarnings("unused")
	protected void setupUDPSocket(final DatagramSocket udpSocket) throws SocketException {
		// overridden when configure the option this udp-socket
	}

	@Override
	public int getReceiveBuffSize() {
		return this.receiveBuffSize;
	}

	/**
	 * Set receiveBuffSize, but the min value is restricted by
	 * {@link JMatcherClientMessage#buffSizeToReceiveSerializedMessage}}
	 * 
	 * @param receiveBuffSize
	 *            the udpReceiveBuffSize to set
	 */
	@Override
	public void setReceiveBuffSize(int receiveBuffSize) {
		this.receiveBuffSize = Math.max(receiveBuffSize, JMatcherClientMessage.buffSizeToReceiveSerializedMessage);
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

	private void notifyObservers(UpdateEvent event, Host target) {
		for (PeerObserver observer : this.observers) {
			observer.updateConnectingHosts(new HashSet<>(this.connectingHosts), event, target);
		}
	}

	/**
	 * @return entry key number, or null is returned if it has been started or
	 *         failed to get entry key from the server
	 * @throws IOException
	 *             thrown if an I/O error occurs
	 */
	public Integer startInvitation() throws IOException {
		this.log(Level.INFO, "starting a invitation."); //$NON-NLS-1$
		if (this.isCommunicating()) {
			this.log(Level.INFO, "it is already communicating."); //$NON-NLS-1$
			return null;
		}
		for (int i = 0; i < this.retryCount; i++) {
			this.closeAllConnections();
			try {
				this.setupTCPConnection();
				final Integer keyNumber = this.makePreEntry();
				if (keyNumber == null) {
					this.closeAllConnections();
					return null;
				}
				this.setupUDPConnection();
				if (this.enableEntry(keyNumber)) {
					this.startPortTellerThread();
					if (this.portTellerThread == null) {
						this.closeAllConnections();
						return null;
					}
					this.startCommunicationThread();
					this.lastEntryKey = keyNumber;
					this.udpSoTimeoutCache = this.udpSocket.getSoTimeout();
					this.log(Level.INFO, "succeeded in starting the invitation"); //$NON-NLS-1$
					return keyNumber;
				}
			} catch (IOException | ClassNotFoundException | ClassCastException e) {
				this.log(Level.ERROR, "failed to start the invitation", e); //$NON-NLS-1$
				// failed
			} catch (Exception e) {
				this.log(Level.ERROR, "unexpected expection occured", e); //$NON-NLS-1$
				e.printStackTrace();
			}
		}
		this.closeAllConnections();
		throw new IOException("failed to connect to the server"); //$NON-NLS-1$
	}

	private void setupTCPConnection() throws UnknownHostException, IOException, SocketException {
		this.log(Level.INFO, "doing setup a TCP connection to make entry to jmatcher-server"); //$NON-NLS-1$
		this.tcpSocket = new Socket(this.jmatcherServer, this.jmatcherServerPort);
		this.setupTCPSocket(this.tcpSocket);
		this.oos = new ObjectOutputStream(this.tcpSocket.getOutputStream());
		this.ois = new ObjectInputStream(this.tcpSocket.getInputStream());
		this.log(Level.INFO, "finised doing setup a TCP connection"); //$NON-NLS-1$
	}

	private Integer makePreEntry() throws IOException, ClassNotFoundException {
		this.oos.writeObject(PlainTCPRequest.ENTRY);
		this.oos.flush();
		final TCPResponse entryResponse = (TCPResponse) this.ois.readObject();
		if (entryResponse == PlainTCPResponse.FAILURE) {
			return null;
		}
		final Integer keyNumber = ((PreEntryResponse) entryResponse).getKeyNumber();
		if (keyNumber != null) {
			this.log(Level.INFO, "succeeded in pre-entry, key = ", keyNumber); //$NON-NLS-1$
		} else {
			this.log(Level.INFO, "failed to do pre-entry. there is a possibility that the server is currently full."); //$NON-NLS-1$
		}
		return keyNumber;
	}

	private void setupUDPConnection() throws SocketException {
		this.log(Level.INFO, "doing setup a UDP connection to communicate with other peer"); //$NON-NLS-1$
		this.udpSocket = new DatagramSocket();
		this.udpSocket.setSoTimeout(defaultUdpSocketTimeoutMillSec);
		this.setupUDPSocket(this.udpSocket);
		this.log(Level.INFO, "finished doing setup a UDP connection"); //$NON-NLS-1$
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
	 * Stop all communication. It should be used when Reinvitation is no longer
	 * done.
	 */
	public void stopCommunication() {
		this.closeAllConnections();
	}

	private void closeAllConnections() {
		this.log(Level.DEBUG, "closing all connections"); //$NON-NLS-1$
		this.closeTCPCommunication();
		this.closeUDPCommunication();
		this.log(Level.DEBUG, "clearing the information of hosts"); //$NON-NLS-1$
		this.requestingHosts.clear();
		this.connectingHosts.clear();
		this.socketAddressCache.clear();
		this.receivedMessageBuffer.clear();
		this.notifyObservers(UpdateEvent.CLEAR, null);
		this.log(Level.DEBUG, "cleared the information of hosts"); //$NON-NLS-1$
		this.waitForCommunicationThread();
		this.waitForPortTellerThread();
		this.log(Level.DEBUG, "closed all connections"); //$NON-NLS-1$
	}

	private void closeTCPCommunication() {
		this.log(Level.DEBUG, "closing the tcp connection"); //$NON-NLS-1$
		JMatcherClientUtil.close(this.ois);
		JMatcherClientUtil.close(this.oos);
		JMatcherClientUtil.close(this.tcpSocket);
		this.ois = null;
		this.oos = null;
		this.tcpSocket = null;
		this.log(Level.DEBUG, "closed the tcp connection"); //$NON-NLS-1$
	}

	private void closeUDPCommunication() {
		this.log(Level.DEBUG, "closing the udp connection"); //$NON-NLS-1$
		if (this.udpSocket != null) {
			for (Host closeTargetHost : this.connectingHosts) {
				try {
					JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.CANCEL, this.name, closeTargetHost);
				} catch (IOException e) {
					// ignore
				}
			}
			JMatcherClientUtil.close(this.udpSocket);
			this.udpSocket = null;
		}
		this.udpSoTimeoutCache = 0;
		this.log(Level.DEBUG, "closed the udp connection"); //$NON-NLS-1$
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
		this.log(Level.INFO, "stopping the invitation"); //$NON-NLS-1$
		this.closeTCPCommunication();
		this.log(Level.INFO, "stopped the invitation"); //$NON-NLS-1$
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
						ConnectionInviterPeer.this.performTellerLoop(portTellerSocket);
					} finally {
						portTellerSocket.close();
						ConnectionInviterPeer.this.portTellerThread = null;
						ConnectionInviterPeer.this.log(Level.INFO, "end port-teller thread for LAN"); //$NON-NLS-1$
					}
				}
			});
			this.log(Level.INFO, "start port-teller thread for LAN"); //$NON-NLS-1$
			this.portTellerThread.start();
		} catch (SocketException e) {
			this.log(Level.ERROR, "failed to start port-teller thread"); //$NON-NLS-1$
			e.printStackTrace();
			return;
		}
	}

	protected void performTellerLoop(final DatagramSocket portTellerSocket) {
		while (this.isInviting()) {
			try {
				final DatagramPacket packet = JMatcherClientUtil.receiveUDPPacket(portTellerSocket, this.receiveBuffSize);
				final String message = JMatcherClientUtil.getMessageFrom(packet);
				this.log(Level.INFO, "port-teller thread : received ", message, " from ", packet.getSocketAddress()); //$NON-NLS-1$ //$NON-NLS-2$
				final Integer sentKey = Integer.valueOf(message);
				final Integer currentEntryKey = this.getCurrentEntryKey();
				if (sentKey.equals(currentEntryKey)) {
					JMatcherClientUtil.sendMessage(portTellerSocket, String.valueOf(this.getSocket().getLocalPort()), packet.getSocketAddress());
					this.log(Level.INFO, "port-teller thread : sent ", Integer.valueOf(this.getSocket().getLocalPort()), " to ", packet.getSocketAddress()); //$NON-NLS-1$ //$NON-NLS-2$
					final Host senderHost = new Host(packet.getAddress().getHostAddress(), packet.getPort());
					final InetSocketAddress senderSocketAddress = new InetSocketAddress(senderHost.getAddress(), senderHost.getPort());
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

	protected Integer getCurrentEntryKey() {
		if (this.isInviting()) {
			return this.lastEntryKey;
		}
		return null;
	}

	private void startCommunicationThread() {
		this.communicationThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					ConnectionInviterPeer.this.performCommunicationLoop();
				} finally {
					ConnectionInviterPeer.this.communicationThread = null;
				}
			}
		});
		this.log(Level.INFO, "start communication thread"); //$NON-NLS-1$
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
					this.log(Level.ERROR, "communication thread : unexpected exception occured", e); //$NON-NLS-1$
				}
			}
		} catch (IOException e) {
			// IOException is mainly caused by closing socket
		}
	}

	private void updateRequestingHosts() throws IOException {
		this.log(Level.DEBUG, "communication thread : updating requesting hosts"); //$NON-NLS-1$
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
		this.log(Level.DEBUG, "communication thread : updated requesting hosts"); //$NON-NLS-1$
		this.log(Level.DEBUG, this.requestingHosts);
	}

	@SuppressWarnings("static-method")
	private boolean isTheTimeToUpdateRuestingHosts(long lastUpdatedTime) {
		return System.currentTimeMillis() - lastUpdatedTime > intervalToUpdateRequestingHosts;
	}

	private void sendHolePunchingMessage() throws IOException {
		for (Host requestingHost : this.requestingHosts) {
			JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.CONNECT_REQUEST, this.name, requestingHost);
			this.log(Level.DEBUG, "communication thread : sent hole-panching message to ", requestingHost); //$NON-NLS-1$
		}
	}

	/**
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	private void receivePacketAndHandle() throws IOException {
		final DatagramPacket packet;
		try {
			synchronized (this.udpSocket) {
				this.udpSoTimeoutCache = this.udpSocket.getSoTimeout();
				packet = JMatcherClientUtil.receiveUDPPacket(this.udpSocket, this.receiveBuffSize);
			}
		} catch (SocketTimeoutException e) {
			return;
		}
		this.log(Level.DEBUG, "communication thread : received message from ", packet.getSocketAddress()); //$NON-NLS-1$
		final Host from = this.specifyHost(packet);
		if (from == null) {
			return;
		}
		final JMatcherClientMessage jmatcherClientMessage = JMatcherClientUtil.getJMatcherMessageFrom(packet);
		// in case JmatcherClientMessage isn't sent
		if (jmatcherClientMessage == null) {
			if (this.connectingHosts.contains(from)) {
				this.storeMessage(packet, from);
				this.log(Level.DEBUG, "communication thread : stored the message which is from ", packet.getSocketAddress()); //$NON-NLS-1$
			}
			return;
		}
		if (jmatcherClientMessage.getType() == JMatcherClientMessageType.CANCEL) {
			this.log(Level.DEBUG, "communication thread : the message which is from ", packet.getSocketAddress(), " is connection-cancel request"); //$NON-NLS-1$ //$NON-NLS-2$
			this.handleCancelMessage(from);
			return;
		}
		if (jmatcherClientMessage.getType() == JMatcherClientMessageType.GOT_CONNECT_REQUEST) {
			this.log(Level.DEBUG, "communication thread : the message which is from ", packet.getSocketAddress(), " means ", packet.getSocketAddress(), " caught the connection request from me"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			this.handleGotConnectRequestMessage(from, jmatcherClientMessage);
			return;
		}
	}

	private void handleCancelMessage(final Host from) throws IOException {
		this.log(Level.INFO, "communication thread : cancelling connection to ", from); //$NON-NLS-1$
		boolean notAlreadyCancelled = false;
		notAlreadyCancelled |= this.requestingHosts.remove(from);
		notAlreadyCancelled |= this.socketAddressCache.remove(from) == null;
		if (this.connectingHosts.remove(from)) {
			notAlreadyCancelled = true;
			this.receivedMessageBuffer.clear(from);
			this.notifyObservers(UpdateEvent.REMOVE, from);
		}

		JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.CANCELLED, this.name, from);
		if (notAlreadyCancelled) {
			this.log(Level.INFO, "communication thread : cancelled connection to ", from); //$NON-NLS-1$
		} else {
			this.log(Level.INFO, "communication thread : connection to ", from, " has already been cancelled"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private void handleGotConnectRequestMessage(final Host from, final JMatcherClientMessage jmatcherClientMessage) throws IOException {
		if (this.connectingHosts.size() >= this.maxSizeOfConnectingHosts) {
			this.log(Level.INFO, "communication thread : could not accept connection request from ", from, " because the list of the connecting hosts is full"); //$NON-NLS-1$ //$NON-NLS-2$
			JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.ENTRY_CLIENT_IS_FULL, this.name, from);
			return;
		}
		if (this.connectingHosts.add(from)) {
			this.log(Level.INFO, "communication thread : ", from, " is added into the list of the connecting hosts"); //$NON-NLS-1$ //$NON-NLS-2$
			from.setName(jmatcherClientMessage.getSenderName());
			this.requestingHosts.remove(from);
			this.notifyObservers(UpdateEvent.ADD, from);
		}
		JMatcherClientUtil.sendJMatcherClientMessage(this.udpSocket, JMatcherClientMessageType.GOT_CONNECT_REQUEST, this.name, from);
		this.log(Level.INFO, "communication thread : sent ", from, " notice that the connection request is accepted"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void storeMessage(final DatagramPacket packet, final Host from) {
		final String message = JMatcherClientUtil.getMessageFrom(packet);
		if (message != null) {
			this.receivedMessageBuffer.store(from, message);
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

	@Override
	public ReceivedMessage receiveMessage() {
		if (!this.isCommunicating()) {
			return null;
		}
		try {
			return this.receivedMessageBuffer.poll(this.udpSoTimeoutCache);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public String receiveMessageFrom(Host host) {
		if (!this.isCommunicating()) {
			return null;
		}
		try {
			final ReceivedMessage receivedMessage = this.receivedMessageBuffer.poll(host, this.udpSocket.getSoTimeout());
			if (receivedMessage == null) {
				return null;
			}
			return receivedMessage.getMessage();
		} catch (SocketException e) {
			return null;
		}
	}

	@Override
	public Host[] sendMessageTo(String message, Host... hosts) {
		if (!this.isCommunicating()) {
			return new Host[0];
		}
		final List<Host> successHost = new ArrayList<>();
		for (Host host : hosts) {
			if (sendMessageTo(message, host)) {
				successHost.add(host);
			}
		}
		return successHost.toArray(new Host[0]);
	}

	/**
	 * @param message
	 * @param host
	 * @return true if succeed in sending
	 */
	public boolean sendMessageTo(String message, Host host) {
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
	@Override
	public DatagramSocket getSocket() {
		return this.udpSocket;
	}

	@Override
	public void close() {
		this.closeAllConnections();
	}

	@Override
	public boolean isOnline() {
		return this.udpSocket != null;
	}

	protected void log(Level level, Object... msgs) {
		if (this.logger == null) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (Object msg : msgs) {
			sb.append(msg);
		}
		this.logger.log(level, sb.toString());
	}

	protected void log(Level level, String msg, Throwable t) {
		if (this.logger == null) {
			return;
		}
		this.logger.log(level, msg, t);
	}

	/**
	 * dump
	 */
	public void dump() {
		System.out.print("requestingHosts = "); //$NON-NLS-1$
		System.out.println(this.requestingHosts);
		System.out.print("connectingHosts = "); //$NON-NLS-1$
		System.out.println(this.connectingHosts);
	}
}
