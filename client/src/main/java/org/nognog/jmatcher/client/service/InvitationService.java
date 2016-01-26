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
import java.io.IOException;
import java.net.DatagramSocket;

import org.nognog.jmatcher.client.JMatcherEntryClient;
import org.nognog.jmatcher.client.JMatcherEntryClientObserver;

/**
 * @author goshi 2016/01/10
 */
public abstract class InvitationService implements JMatcherEntryClientObserver, Closeable {
	protected final JMatcherEntryClient jmatcherEntryClient;

	/**
	 * @param name
	 * @param jmatcherServer
	 */
	@SuppressWarnings("resource")
	public InvitationService(String name, String jmatcherServer) {
		this(new JMatcherEntryClient(name, jmatcherServer));
	}

	/**
	 * @param jmatcherEntryClient
	 */
	public InvitationService(JMatcherEntryClient jmatcherEntryClient) {
		if (jmatcherEntryClient == null) {
			throw new IllegalArgumentException();
		}
		this.jmatcherEntryClient = jmatcherEntryClient;
		this.jmatcherEntryClient.addObserver(this);
	}

	/**
	 * start the service
	 * 
	 * @param listener
	 * 
	 */
	public void start(final EndListener<Integer> listener) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final Integer entryKey = InvitationService.this.jmatcherEntryClient.startInvitation();
					if (listener != null) {
						if (entryKey != null) {
							listener.success(entryKey);
						} else {
							listener.failure(null);
						}
					}
				} catch (IOException e) {
					if (listener != null) {
						listener.failure(e);
					}
				}
			}
		}).start();
	}

	/**
	 * Stop current invitation if this is inviting. UDP connection still will be
	 * alive after this method. If you want to reset all communication, you
	 * should use {@link #stopCommunication()}} or {@link #close()}
	 * 
	 * @param listener
	 */
	public void stopInvitation(final EndListener<Void> listener) {
		if (this.jmatcherEntryClient.isInviting()) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						InvitationService.this.jmatcherEntryClient.stopInvitation();
					} catch (Exception e) {
						if (listener != null) {
							listener.failure(e);
						}
						return;
					}
					if (listener != null) {
						listener.success(null);
					}
				}
			}).start();
		} else if (listener != null) {
			listener.failure(null);
		}
	}

	/**
	 * Stop all current communication. The difference between this method and
	 * {@link #stopInvitation()}} is whether udp connection will be closed or
	 * not. If you want to communicate with other peers and stop invitation, you
	 * will have to use {@link stopInvitation}}.
	 * 
	 * @param listener
	 */
	public void stopCommunication(final EndListener<Void> listener) {
		if (this.isCommunicating()) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						InvitationService.this.jmatcherEntryClient.closeAllConnections();
					} catch (Exception e) {
						if (listener != null) {
							listener.failure(e);
						}
						return;
					}
					if (listener != null) {
						listener.success(null);
					}
				}
			}).start();
		} else if (listener != null) {
			listener.failure(null);
		}
	}

	/**
	 * @return true if this is inviting
	 */
	public boolean isInviting() {
		return this.jmatcherEntryClient.isInviting();
	}

	/**
	 * @return true if this is communicating
	 */
	public boolean isCommunicating() {
		return this.jmatcherEntryClient.isCommunicating();
	}

	/**
	 * @param maxSizeOfConnectingHosts
	 */
	public void setMaxSizeOfConnectingHosts(int maxSizeOfConnectingHosts) {
		this.jmatcherEntryClient.setMaxSizeOfConnectingHosts(maxSizeOfConnectingHosts);
	}

	/**
	 * @return the maxSizeOfConnectingHosts
	 */
	public int getMaxSizeOfConnectingHosts() {
		return this.jmatcherEntryClient.getMaxSizeOfConnectingHosts();
	}

	@Override
	public void close() {
		this.jmatcherEntryClient.close();
	}

	/**
	 * @return socket
	 */
	public DatagramSocket getUdpSocket() {
		return this.jmatcherEntryClient.getUDPSocket();
	}
}
