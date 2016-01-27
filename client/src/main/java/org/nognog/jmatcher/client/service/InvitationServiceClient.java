/** Copyright 2016 Goshi Noguchi (noggon54@gmail.com)
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

package org.nognog.jmatcher.client.service;

import java.io.Closeable;
import java.net.DatagramSocket;

import org.nognog.jmatcher.Host;
import org.nognog.jmatcher.client.JMatcherConnectionClient;

/**
 * @author goshi 2016/01/19
 */
public class InvitationServiceClient implements Closeable {
	protected final JMatcherConnectionClient jmatcherConnectionClient;

	/**
	 * @param name
	 * @param host
	 */
	@SuppressWarnings("resource")
	public InvitationServiceClient(String name, String host) {
		this(new JMatcherConnectionClient(name, host));
	}

	/**
	 * @param jmatcherConnectionClient
	 */
	public InvitationServiceClient(JMatcherConnectionClient jmatcherConnectionClient) {
		this.jmatcherConnectionClient = jmatcherConnectionClient;
	}

	/**
	 * @param key
	 * @param listener
	 */
	public void connect(final int key, final EndListener<Void> listener) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final boolean connect = InvitationServiceClient.this.jmatcherConnectionClient.connect(key);
					if (listener != null) {
						if (connect) {
							listener.success(null);
						} else {
							listener.failure(null);
						}
					}
				} catch (Exception e) {
					if (listener != null) {
						listener.failure(e);
					}
				}
			}
		}).start();
	}
	
	/**
	 * @param key
	 * @param listener
	 */
	public void cancelConnection(final EndListener<Void> listener) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final boolean connect = InvitationServiceClient.this.jmatcherConnectionClient.cancelConnection();
					if (listener != null) {
						if (connect) {
							listener.success(null);
						} else {
							listener.failure(null);
						}
					}
				} catch (Exception e) {
					if (listener != null) {
						listener.failure(e);
					}
				}
			}
		}).start();
	}

	/**
	 * Get the core for making connection
	 * 
	 * @return the jmatcherConnectionClient
	 */
	public JMatcherConnectionClient getJMatcherConnectionClient() {
		return this.jmatcherConnectionClient;
	}

	/**
	 * @return socket
	 */
	public DatagramSocket getConnectingSocket() {
		return this.jmatcherConnectionClient.getConnectingSocket();
	}

	/**
	 * @return connecting host
	 */
	public Host getConnectingHost() {
		return this.jmatcherConnectionClient.getConnectingHost();
	}

	@Override
	public void close() {
		this.jmatcherConnectionClient.close();
	}
}
