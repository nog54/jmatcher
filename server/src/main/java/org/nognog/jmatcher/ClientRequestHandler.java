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
import java.net.InetAddress;
import java.net.Socket;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;

import org.nognog.jmatcher.request.Request;
import org.nognog.jmatcher.request.RequestType;
import org.nognog.jmatcher.response.Response;

/**
 * @author goshi 2015/10/31
 */
public class ClientRequestHandler implements Runnable {

	private static final Random random = new Random(new Date().getTime());

	private Integer number;
	private JMatcherDaemon jmatcherDaemon;
	private ConcurrentMap<Integer, InetAddress> matchingMap;
	private Socket socket;
	private boolean hasClosedSocket;
	
	/**
	 * @param jmatcherDaemon
	 * @param socket
	 * @param number
	 * 
	 */
	public ClientRequestHandler(JMatcherDaemon jmatcherDaemon, Socket socket, Integer number) {
		this.number = number;
		this.jmatcherDaemon = jmatcherDaemon;
		this.matchingMap = this.jmatcherDaemon.getMatchingMap();
		this.socket = socket;
		this.hasClosedSocket = false;
	}

	@Override
	public void run() {
		final StringBuilder sb = new StringBuilder();
		sb.append("handler number ").append(this.number).append(" - ").append(this.socket.getInetAddress().getHostName()).append(":").append(this.socket.getInetAddress().getHostAddress()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		this.jmatcherDaemon.getLogger().info(sb.toString());

		try (final ObjectInputStream objectInputStream = new ObjectInputStream(this.socket.getInputStream());
				final ObjectOutputStream objectOutputStream = new ObjectOutputStream(this.socket.getOutputStream())) {
			final Request request = (Request) objectInputStream.readObject();
			final Response response = this.performRequest(request);
			objectOutputStream.writeObject(response);
		} catch (Exception e) {
			this.jmatcherDaemon.getLogger().error("Failed to input request", e); //$NON-NLS-1$
		}
		this.close();
	}

	/**
	 * @param request
	 * @return
	 */
	private Response performRequest(Request request) {
		final RequestType requestType = request.getType();
		if (requestType == RequestType.FIND) {
			return performFindRequest(request);
		} else if (requestType == RequestType.ENTRY) {
			return performEntryRequest(request);
		} else if (requestType == RequestType.CANCEL_ENTRY) {
			return performCancelEntryRequest(request);
		}
		return new Response(request, false);
	}

	private Response performFindRequest(Request request) {
		final InetAddress address = this.matchingMap.get(request.getKeyNumber());
		final Response result = new Response(request, true);
		result.setAddress(address);
		return result;
	}

	private Response performEntryRequest(Request request) {
		final Integer entryKeyNumber;
		synchronized (ClientRequestHandler.class) {
			entryKeyNumber = this.createUnregistedKeyNumber();
			if (entryKeyNumber == null) {
				return new Response(request, false);
			}
			if (this.matchingMap.containsKey(entryKeyNumber)) {
				return new Response(request, false);
			}
			this.matchingMap.put(entryKeyNumber, this.socket.getInetAddress());
		}
		this.jmatcherDaemon.logMatchingMap();
		final Response response = new Response(request, true);
		response.setKeyNumber(entryKeyNumber);
		return response;
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

	private Response performCancelEntryRequest(Request request) {
		final Integer keyNumber = request.getKeyNumber();
		if (keyNumber == null || keyNumber.equals("")) { //$NON-NLS-1$
			return new Response(request, false);
		}
		final InetAddress previousAddress;
		synchronized (ClientRequestHandler.class) {
			previousAddress = this.matchingMap.remove(request.getKeyNumber());
		}
		final Response response = new Response(request, true);
		response.setAddress(previousAddress);
		this.jmatcherDaemon.logMatchingMap();
		return response;
	}

	/**
	 * close this handler
	 * 
	 * @throws IOException
	 */
	public void close() {
		if (this.hasClosedSocket) {
			return;
		}
		try {
			this.socket.close();
			this.hasClosedSocket = true;
		} catch (IOException e) {
			this.jmatcherDaemon.getLogger().error("Failed to close socket", e); //$NON-NLS-1$
		}
	}

	/**
	 * @return true if this has closed
	 */
	public boolean hasClosedSocket() {
		return this.hasClosedSocket;
	}

}
