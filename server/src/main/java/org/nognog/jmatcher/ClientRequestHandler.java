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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.Logger;
import org.nognog.jmatcher.request.Request;
import org.nognog.jmatcher.request.RequestType;
import org.nognog.jmatcher.response.Response;

/**
 * @author goshi 2015/10/31
 */
@SuppressWarnings("static-method")
public class ClientRequestHandler implements Runnable {

	private static final ConcurrentMap<String, InetAddress> map = new ConcurrentHashMap<>();

	private Socket socket;
	private Logger logger;
	private boolean hasClosedSocket;

	/**
	 * @param socket
	 * @param logger
	 * 
	 */
	public ClientRequestHandler(Socket socket, Logger logger) {
		this.socket = socket;
		this.logger = logger;
		this.hasClosedSocket = false;
	}

	@Override
	public void run() {
		try (final ObjectInputStream objectInputStream = new ObjectInputStream(this.socket.getInputStream());
				final ObjectOutputStream objectOutputStream = new ObjectOutputStream(this.socket.getOutputStream())) {
			final Request request = (Request) objectInputStream.readObject();
			final Response response = this.performRequest(request);
			objectOutputStream.writeObject(response);
		} catch (Exception e) {
			this.logger.error("Failed to input request", e); //$NON-NLS-1$
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
		final InetAddress address = map.get(request.getKeyNumber());
		final Response result = new Response(request, true);
		result.setAddress(address);
		return result;
	}

	private Response performEntryRequest(Request request) {
		final String keyNumber = request.getKeyNumber();
		if (keyNumber == null || keyNumber.equals("")) { //$NON-NLS-1$
			return new Response(request, false);
		}
		final InetAddress previousValue = map.putIfAbsent(keyNumber, this.socket.getInetAddress());
		final boolean isAlreadyAssociated = (previousValue != null);
		if (isAlreadyAssociated) {
			return new Response(request, false);
		}
		this.logger.info(map);
		return new Response(request, true);
	}

	private Response performCancelEntryRequest(Request request) {
		final String keyNumber = request.getKeyNumber();
		if (keyNumber == null || keyNumber.equals("")) { //$NON-NLS-1$
			return new Response(request, false);
		}
		final InetAddress previousAddress = map.remove(request.getKeyNumber());
		final Response response = new Response(request, true);
		response.setAddress(previousAddress);
		this.logger.info(map);
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
			this.logger.error("Failed to close socket", e); //$NON-NLS-1$
		}
	}

	/**
	 * @return true if this has closed
	 */
	public boolean hasClosedSocket() {
		return this.hasClosedSocket;
	}

}
