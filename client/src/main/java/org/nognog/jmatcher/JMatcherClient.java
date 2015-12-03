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
import java.net.InetAddress;
import java.net.Socket;

/**
 * @author goshi 2015/11/27
 */
public class JMatcherClient implements java.io.Closeable {

	private final String host;
	private final int port;
	private boolean isClosed;

	private Socket socket;
	private ObjectOutputStream oos;
	private ObjectInputStream ois;

	private static final int defaultPort = 11600;

	/**
	 * @param host
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherClient(String host) throws IOException {
		this(host, defaultPort);
	}

	/**
	 * @param host
	 * @param port
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherClient(String host, int port) throws IOException {
		this.host = host;
		this.port = port;
		this.setupSocket();
	}

	private void setupSocket() throws IOException {
		if (this.isClosed) {
			return;
		}
		this.cleanSocket();
		this.socket = new Socket(this.host, this.port);
		this.oos = new ObjectOutputStream(this.socket.getOutputStream());
		this.ois = new ObjectInputStream(this.socket.getInputStream());
	}

	private void cleanSocket() {
		if (this.oos != null) {
			closeClosable(this.oos);
			this.oos = null;
		}
		if (this.ois != null) {
			closeClosable(this.ois);
			this.ois = null;
		}
		if (this.socket != null) {
			closeClosable(this.socket);
			this.socket = null;
		}
	}

	private static void closeClosable(Closeable closable) {
		try {
			closable.close();
		} catch (IOException e) {
			// Just print
			e.printStackTrace();
		}
	}

	/**
	 * @param key
	 * @return true if success
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public boolean makeEntry(Integer key) throws IOException {
		if (this.isClosed) {
			throw new RuntimeException("it has been closed"); //$NON-NLS-1$
		}
		try {
			return JMatcherClientUtils.makeEntry(key, this.oos, this.ois);
		} catch (IOException e) {
			this.setupSocket();
		}
		return JMatcherClientUtils.makeEntry(key, this.oos, this.ois);
	}

	/**
	 * @param key
	 * @return true if success
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public boolean cancelEntry(Integer key) throws IOException {
		if (this.isClosed) {
			throw new RuntimeException("it has been closed"); //$NON-NLS-1$
		}
		try {
			return JMatcherClientUtils.cancelEntry(key, this.oos, this.ois);
		} catch (IOException e) {
			this.setupSocket();
		}
		return JMatcherClientUtils.cancelEntry(key, this.oos, this.ois);
	}

	/**
	 * @param key
	 * @return response
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public InetAddress findEntry(Integer key) throws IOException {
		if (this.isClosed) {
			throw new RuntimeException("it has been closed"); //$NON-NLS-1$
		}
		try {
			return JMatcherClientUtils.findEntry(key, this.oos, this.ois);
		} catch (IOException e) {
			this.setupSocket();
		}
		return JMatcherClientUtils.findEntry(key, this.oos, this.ois);
	}

	@Override
	public void close() {
		if (this.isClosed) {
			return;
		}
		this.cleanSocket();
		this.isClosed = true;
	}

}
