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

/**
 * @author goshi 2015/11/27
 */
public class JMatcherClient {

	private String host;
	private int port;
	private int retryCount;

	private static final int defaultPort = 11600;
	private static final int defalutRetryCount = 2;

	/**
	 * @param host
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherClient(String host) {
		this(host, defaultPort);
	}

	/**
	 * @param host
	 * @param port
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public JMatcherClient(String host, int port) {
		this.host = host;
		this.port = port;
		this.retryCount = defalutRetryCount;
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

	protected void setupSocket(final Socket socket) {
		// overridden when configure the option of sockets
	}

	/**
	 * @param key
	 * @return true if success
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public boolean makeEntry(Integer key) throws IOException {
		try (final Socket socket = new Socket(this.host, this.port)) {
			this.setupSocket(socket);
			for (int i = 0; i < this.retryCount; i++) {
				try (final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream()); final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
					return JMatcherClientUtils.makeEntry(key, oos, ois);
				} catch (IOException e) {
					// failed
				}
			}
			throw new IOException("failed to connect to the server"); //$NON-NLS-1$
		}
	}

	/**
	 * @param key
	 * @return true if success
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public boolean cancelEntry(Integer key) throws IOException {
		try (final Socket socket = new Socket(this.host, this.port)) {
			this.setupSocket(socket);
			for (int i = 0; i < this.retryCount; i++) {
				try (final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream()); final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
					return JMatcherClientUtils.cancelEntry(key, oos, ois);
				} catch (IOException e) {
					// failed
				}
			}
			throw new IOException("failed to connect to the server"); //$NON-NLS-1$
		}
	}

	/**
	 * @param key
	 * @return response
	 * @throws IOException
	 *             It's thrown if failed to connect to the server
	 */
	public InetAddress findEntry(Integer key) throws IOException {
		try (final Socket socket = new Socket(this.host, this.port)) {
			this.setupSocket(socket);
			for (int i = 0; i < this.retryCount; i++) {
				try (final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream()); final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
					return JMatcherClientUtils.findEntry(key, oos, ois);
				} catch (IOException e) {
					// failed
				}
			}
			throw new IOException("failed to connect to the server"); //$NON-NLS-1$
		}
	}

}
