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
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nognog.jmatcher.udp.request.ConnectionRequest;
import org.nognog.jmatcher.udp.request.EnableEntryRequest;
import org.nognog.jmatcher.udp.request.UDPRequest;
import org.nognog.jmatcher.udp.request.UDPRequestSerializer;
import org.nognog.jmatcher.udp.response.ConnectionResponse;
import org.nognog.jmatcher.udp.response.UDPResponse;
import org.nognog.jmatcher.udp.response.UDPResponseSerializer;

/**
 * @author goshi 2015/10/31
 */
public class UDPClientRequestHandler implements Runnable {

	private int number;
	private String name;
	private JMatcherDaemon jmatcherDaemon;
	private ConcurrentMap<Integer, Host> matchingMap;
	private ConcurrentMap<Integer, CopyOnWriteArraySet<RequestingConnectionHostHandler>> waitingForSyncHandlersMap;

	private DatagramSocket socket;
	private InetSocketAddress clientAddress;
	private String receivedMessage;

	private static Logger logger = LogManager.getLogger(UDPClientRequestHandler.class);

	/**
	 * 
	 */
	public static final int WAIT_TIME_FOR_MATCHING_TIMING = 5000; //

	/**
	 * @param jmatcherDaemon
	 * @param socket
	 * @param receivedMessage
	 * @param clientAddress
	 * @param number
	 * 
	 */
	public UDPClientRequestHandler(JMatcherDaemon jmatcherDaemon, DatagramSocket socket, InetSocketAddress clientAddress, String receivedMessage, int number) {
		this.jmatcherDaemon = jmatcherDaemon;
		this.matchingMap = this.jmatcherDaemon.getMatchingMap();
		this.waitingForSyncHandlersMap = this.jmatcherDaemon.getWaitingHandlersMap();
		this.socket = socket;
		this.clientAddress = clientAddress;
		this.receivedMessage = receivedMessage;
		this.number = number;
		this.name = new StringBuilder().append("UDP(").append(this.number).append(")").toString(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void log(String message, Level level) {
		logger.log(level, createLappedMessage(message));
	}

	private void log(Throwable t, Level level) {
		this.log("", t, level); //$NON-NLS-1$
	}

	private void log(String message, Throwable t, Level level) {
		logger.log(level, createLappedMessage(message), t);
	}

	private String createLappedMessage(String message) {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.name).append(" ").append(message); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * @return the clientAddress
	 */
	public InetSocketAddress getClientAddress() {
		return this.clientAddress;
	}

	@Override
	public void run() {
		final UDPRequest request = UDPRequestSerializer.getInstance().deserialize(this.receivedMessage);
		if (request == null) {
			return;
		}
		try {
			this.handleRequest(request);
		} catch (IOException e) {
			this.log(e, Level.ERROR);
		} catch (Throwable e) {
			this.log(e, Level.FATAL);
		}
	}

	/**
	 * @param request
	 * @throws IOException
	 */
	private void handleRequest(UDPRequest request) throws IOException {
		if (request instanceof ConnectionRequest) {
			this.handleConnectionRequest((ConnectionRequest) request);
		} else if (request instanceof EnableEntryRequest) {
			this.handleEnableEntryRequest((EnableEntryRequest) request);
		}
	}

	private void handleConnectionRequest(ConnectionRequest request) throws IOException {
		final Host targetHost = this.matchingMap.get(request.getKeyNumber());
		if (targetHost == null || targetHost instanceof PreEntryHost) {
			this.sendResponse(new ConnectionResponse(null));
			return;
		}
		final Integer connectionTargetKeyNumber = this.getRealKeyNumber(request.getKeyNumber());
		if (connectionTargetKeyNumber == null) {
			this.sendResponse(new ConnectionResponse(null));
			return;
		}
		try {
			this.waitToMatchConnectionTiming(connectionTargetKeyNumber);
		} catch (TimeoutException e) {
			this.sendResponse(new ConnectionResponse(null));
			return;
		}

		this.sendResponse(new ConnectionResponse(targetHost));
	}

	/**
	 * @param requestKeyNumber
	 * @return real key number, which is contained in the matchingMap
	 */
	private Integer getRealKeyNumber(Integer requestKeyNumber) {
		for (Integer key : this.matchingMap.keySet()) {
			if (key.equals(requestKeyNumber)) {
				return key;
			}
		}
		return null;
	}

	/**
	 * @param connectionTargetKeyNumber
	 */
	private void waitToMatchConnectionTiming(Integer connectionTargetKeyNumber) throws TimeoutException {
		final CopyOnWriteArraySet<RequestingConnectionHostHandler> newSet = new CopyOnWriteArraySet<>();
		synchronized (connectionTargetKeyNumber) {
			final CopyOnWriteArraySet<RequestingConnectionHostHandler> previousSettedSet = this.waitingForSyncHandlersMap.putIfAbsent(connectionTargetKeyNumber, newSet);
			final Host requestingConnectionHost = this.getClientHost();
			final RequestingConnectionHostHandler waitingHandler = new RequestingConnectionHostHandler(Thread.currentThread(), requestingConnectionHost);
			if (previousSettedSet == null) { // putted new set
				newSet.add(waitingHandler);
			} else {
				previousSettedSet.add(waitingHandler);
			}
		}
		try {
			Thread.sleep(WAIT_TIME_FOR_MATCHING_TIMING);
		} catch (InterruptedException e) {
			return;
		}
		throw new TimeoutException();
	}

	private Host getClientHost() {
		return new Host(this.clientAddress.getAddress().getHostAddress(), this.clientAddress.getPort());
	}

	/**
	 * @param response
	 * @throws IOException
	 */
	private void sendResponse(UDPResponse response) throws IOException {
		final String serializedResponse = UDPResponseSerializer.getInstance().serialize(response);
		final byte[] buf = serializedResponse.getBytes();
		final DatagramPacket packet = new DatagramPacket(buf, buf.length, this.clientAddress);
		this.socket.send(packet);
		final String logMessage = new StringBuilder().append(this.name).append(" ").append(this.socket.getLocalAddress()).append(" -> ") //$NON-NLS-1$ //$NON-NLS-2$
				.append(packet.getSocketAddress()).append(" : ").append(serializedResponse).toString(); //$NON-NLS-1$
		this.log(logMessage, Level.INFO);
	}

	/**
	 * @param request
	 */
	private void handleEnableEntryRequest(EnableEntryRequest request) {
		final Integer keyNumber = getRealKeyNumber(request.getKeyNumber());
		synchronized (keyNumber) {
			if (this.matchingMap.get(keyNumber) instanceof PreEntryHost) {
				this.matchingMap.put(keyNumber, this.getClientHost());
				keyNumber.notifyAll();
			}
		}
	}
}