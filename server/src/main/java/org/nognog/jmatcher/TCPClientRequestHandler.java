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
import java.net.Socket;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nognog.jmatcher.tcp.request.PlainTCPRequest;
import org.nognog.jmatcher.tcp.request.TCPRequest;
import org.nognog.jmatcher.tcp.response.CheckConnectionResponse;
import org.nognog.jmatcher.tcp.response.PreEntryResponse;
import org.nognog.jmatcher.tcp.response.PlainTCPResponse;

/**
 * @author goshi 2015/10/31
 */
public class TCPClientRequestHandler implements Runnable {

	private static final Random random = new Random(new Date().getTime());

	private int number;
	private String name;
	private JMatcherDaemon jmatcherDaemon;
	private ConcurrentMap<Integer, Host> matchingMap;
	private ConcurrentMap<Integer, CopyOnWriteArraySet<RequestingConnectionHostHandler>> waitingForSyncHandlersMap;

	private Integer entryKeyNumber;

	private Socket socket;

	private static Logger logger = LogManager.getLogger(TCPClientRequestHandler.class);

	/**
	 * 
	 */
	static final int WAIT_TIME_FOR_UDP_ENTRY = 5000;

	/**
	 * @param jmatcherDaemon
	 * @param socket
	 * @param number
	 */
	public TCPClientRequestHandler(JMatcherDaemon jmatcherDaemon, Socket socket, int number) {
		this.jmatcherDaemon = jmatcherDaemon;
		this.matchingMap = this.jmatcherDaemon.getMatchingMap();
		this.waitingForSyncHandlersMap = this.jmatcherDaemon.getWaitingHandlersMap();
		this.socket = socket;
		this.number = number;
		this.name = new StringBuilder().append("TCP(").append(this.number).append(")").toString(); //$NON-NLS-1$ //$NON-NLS-2$
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

	@Override
	public void run() {
		try (final ObjectInputStream ois = new ObjectInputStream(this.socket.getInputStream()); final ObjectOutputStream oos = new ObjectOutputStream(this.socket.getOutputStream())) {
			final TCPRequest request = (TCPRequest) ois.readObject();
			this.handleRequest(request, ois, oos);
		} catch (IOException | ClassNotFoundException e) {
			this.log(e, Level.ERROR);
		} catch (Throwable t) {
			this.log("unexpected error occured", t, Level.FATAL); //$NON-NLS-1$
		} finally {
			this.close();
		}
	}

	/**
	 * @param request
	 * @param oos
	 * @param ois
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void handleRequest(TCPRequest request, ObjectInputStream ois, ObjectOutputStream oos) throws IOException, ClassNotFoundException {
		if (request.equals(PlainTCPRequest.ENTRY)) {
			this.handleEntryRequest(ois, oos);
			return;
		}
		sendFailureResponse(oos);
	}

	private static void sendFailureResponse(ObjectOutputStream oos) throws IOException {
		oos.writeObject(PlainTCPResponse.FAILURE);
	}

	private void handleEntryRequest(ObjectInputStream ois, ObjectOutputStream oos) throws IOException, ClassNotFoundException {
		final boolean putted;
		synchronized (TCPClientRequestHandler.class) {
			this.entryKeyNumber = this.createUnregistedKeyNumber();
			if (this.entryKeyNumber != null) {
				this.matchingMap.put(this.entryKeyNumber, new PreEntryHost(this.socket.getInetAddress().getHostAddress(), this.socket.getPort()));
				putted = true;
			} else {
				putted = false;
			}
		}
		if (!putted) {
			sendFailureResponse(oos);
			return;
		}
		final StringBuilder sb = new StringBuilder();
		sb.append("PreEntry : ").append(this.entryKeyNumber).append(" = ").append(this.matchingMap.get(this.entryKeyNumber)); //$NON-NLS-1$ //$NON-NLS-2$
		this.log(sb.toString(), Level.INFO);
		final PreEntryResponse entryResponse = new PreEntryResponse(this.entryKeyNumber);
		oos.writeObject(entryResponse);
		try {
			this.waitForUDPEntry();
		} catch (TimeoutException e) {
			String timeoutMessage = new StringBuilder().append(": ").append(this.entryKeyNumber).append(" timeout").toString(); //$NON-NLS-1$ //$NON-NLS-2$
			this.log(timeoutMessage, Level.INFO);
			return;
		}
		this.jmatcherDaemon.logMatchingMap();
		oos.writeObject(PlainTCPResponse.COMPLETE_ENTRY);
		this.communicateWithRegisteredClientLoop(ois, oos);
	}

	/**
	 * 
	 */
	private void waitForUDPEntry() throws TimeoutException {
		try {
			final long startTime = System.currentTimeMillis();
			synchronized (this.entryKeyNumber) {
				this.entryKeyNumber.wait(WAIT_TIME_FOR_UDP_ENTRY);
			}
			final long waitedTime = System.currentTimeMillis() - startTime;
			if (waitedTime >= WAIT_TIME_FOR_UDP_ENTRY) {
				throw new TimeoutException();
			}
		} catch (InterruptedException e) {
			// complete udp entry
		}
	}

	private void communicateWithRegisteredClientLoop(ObjectInputStream ois, ObjectOutputStream oos) throws ClassNotFoundException, IOException {
		while (true) {
			final Object readObject = ois.readObject();
			final TCPRequest request;
			try {
				request = (TCPRequest) readObject;
			} catch (ClassCastException e) {
				this.log(readObject.toString(), Level.ERROR);
				return;
			}
			if (request == PlainTCPRequest.CHECK_CONNECTION_REQUEST) {
				final CopyOnWriteArraySet<RequestingConnectionHostHandler> waitingHandlers = this.waitingForSyncHandlersMap.get(this.entryKeyNumber);
				if (waitingHandlers == null) {
					oos.writeObject(new CheckConnectionResponse(null));
				} else {
					final Object[] waitingHandlersArray = waitingHandlers.toArray();
					final Host[] hosts = createHostsArray(waitingHandlersArray);
					this.releaseWaitingHandlers(waitingHandlersArray, waitingHandlers);
					oos.writeObject(new CheckConnectionResponse(hosts));
				}
			} else { // catch invalid request
				break;
			}
		}
	}

	private static Host[] createHostsArray(final Object[] waitingHandlersArray) {
		final Host[] hosts = new Host[waitingHandlersArray.length];
		for (int i = 0; i < hosts.length; i++) {
			hosts[i] = ((RequestingConnectionHostHandler) waitingHandlersArray[i]).getHost();
		}
		return hosts;
	}

	private void releaseWaitingHandlers(final Object[] targetsOfReleasing, CopyOnWriteArraySet<RequestingConnectionHostHandler> waitingHandlers) {
		for (Object target : targetsOfReleasing) {
			((RequestingConnectionHostHandler) target).getThread().interrupt();
			waitingHandlers.remove(target);
		}
		synchronized (this.entryKeyNumber) {
			if (waitingHandlers.size() == 0) {
				this.waitingForSyncHandlersMap.remove(this.entryKeyNumber);
			}
		}
	}

	private Integer createUnregistedKeyNumber() {
		if (this.matchingMap.size() >= this.jmatcherDaemon.getMatchingMapCapacity()) {
			return null;
		}
		final Integer key = Integer.valueOf(random.nextInt(this.jmatcherDaemon.getBoundOfKeyNumber()));

		if (!this.matchingMap.containsKey(key)) {
			return key;
		}
		return this.createUnregistedKeyNumber();
	}

	/**
	 * close this handler
	 * 
	 * @throws IOException
	 */
	private void close() {
		if (this.entryKeyNumber != null) {
			synchronized (this.entryKeyNumber) {
				this.matchingMap.remove(this.entryKeyNumber);
				this.waitingForSyncHandlersMap.remove(this.entryKeyNumber);
			}
		}
		try {
			this.socket.close();
		} catch (IOException e) {
			this.log("Failed to close socket", e, Level.ERROR); //$NON-NLS-1$
		}
	}
}