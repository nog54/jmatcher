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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nognog.jmatcher.client.ConnectionInviterPeer;
import org.nognog.jmatcher.client.PeerObserver;


/**
 * @author goshi 2016/01/10
 */
public abstract class InvitationService implements PeerObserver, Closeable {
	protected final ConnectionInviterPeer connectionInviter;

	private static final long defaultInvitationTimeoutSec = 900; // 15 minutes
	private long invitationTimeoutSec = defaultInvitationTimeoutSec;

	protected TimeoutMaker timeoutMaker;

	/**
	 * @param name
	 * @param jmatcherServer
	 * @param receiveBuffSize
	 * @param version
	 */
	@SuppressWarnings("resource")
	public InvitationService(String name, String jmatcherServer, int receiveBuffSize) {
		this(new ConnectionInviterPeer(name, jmatcherServer));
		this.connectionInviter.setReceiveBuffSize(receiveBuffSize);
	}

	/**
	 * @param connectionInviter
	 * @param receiveBuffSize
	 * @param version
	 */
	public InvitationService(ConnectionInviterPeer connectionInviter) {
		if (connectionInviter == null) {
			throw new IllegalArgumentException();
		}
		this.connectionInviter = connectionInviter;
		this.connectionInviter.addObserver(this);
		this.setLogger(LogManager.getLogger(this.getClass()));
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return this.connectionInviter.getName();
	}

	/**
	 * @param logger
	 */
	public void setLogger(Logger logger) {
		this.connectionInviter.setLogger(logger);
	}

	/**
	 * @return the invitationTimeoutSec
	 */
	public long getInvitationTimeoutSec() {
		return this.invitationTimeoutSec;
	}

	/**
	 * @param invitationTimeoutSec
	 *            the invitationTimeoutSec to set
	 */
	public void setInvitationTimeoutSec(long invitationTimeoutSec) {
		this.invitationTimeoutSec = invitationTimeoutSec;
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
					final Integer entryKey;
					synchronized (InvitationService.this) {
						entryKey = InvitationService.this.connectionInviter.startInvitation();
					}
					if (listener != null) {
						if (entryKey != null) {
							listener.success(entryKey);
							InvitationService.this.startTimeoutMaker();
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
	 * 
	 */
	protected void startTimeoutMaker() {
		if (this.invitationTimeoutSec != 0) {
			this.timeoutMaker = new TimeoutMaker(this.invitationTimeoutSec, this);
			this.timeoutMaker.start();
		}
	}

	/**
	 * Stop current invitation if this is inviting. UDP connection still will be
	 * alive after this method. If you want to reset all communication, you
	 * should use {@link #stopCommunication(EndListener)} } or {@link #close()}
	 * 
	 * @param listener
	 */
	public void stopInvitation(final EndListener<Void> listener) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					synchronized (InvitationService.this) {
						if (InvitationService.this.timeoutMaker != null) {
							InvitationService.this.timeoutMaker.interruptTimeout();
							InvitationService.this.timeoutMaker = null;
						}
						if (!InvitationService.this.connectionInviter.isInviting()) {
							throw new IllegalStateException("not inviting"); //$NON-NLS-1$
						}
						InvitationService.this.connectionInviter.stopInvitation();
					}
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
	}

	/**
	 * Stop all current communication. The difference between this method and
	 * {@link #stopInvitation(EndListener)}} is whether udp connection will be
	 * closed or not. If you want to communicate with other peers and stop
	 * invitation, you will have to use {@link #stopInvitation(EndListener)}}.
	 * 
	 * @param listener
	 */
	public void stopCommunication(final EndListener<Void> listener) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					synchronized (InvitationService.this) {
						if (InvitationService.this.timeoutMaker != null) {
							InvitationService.this.timeoutMaker.interruptTimeout();
							InvitationService.this.timeoutMaker = null;
						}
						if (!InvitationService.this.connectionInviter.isCommunicating()) {
							throw new IllegalStateException("not communicating"); //$NON-NLS-1$
						}
						InvitationService.this.connectionInviter.stopCommunication();
					}
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
	}

	/**
	 * @return true if this is inviting
	 */
	public boolean isInviting() {
		return this.connectionInviter.isInviting();
	}

	/**
	 * @return true if this is communicating
	 */
	public boolean isCommunicating() {
		return this.connectionInviter.isCommunicating();
	}

	/**
	 * @param maxSizeOfConnectingHosts
	 */
	public void setMaxSizeOfConnectingHosts(int maxSizeOfConnectingHosts) {
		this.connectionInviter.setMaxSizeOfConnectingHosts(maxSizeOfConnectingHosts);
	}

	/**
	 * @return the maxSizeOfConnectingHosts
	 */
	public int getMaxSizeOfConnectingHosts() {
		return this.connectionInviter.getMaxSizeOfConnectingHosts();
	}

	/**
	 * @param name
	 */
	public void setNameIfNotCommunicating(String name) {
		this.connectionInviter.setNameIfNotCommunicating(name);
	}

	@Override
	public void close() {
		this.connectionInviter.removeObserver(this);
		this.connectionInviter.close();
	}

	/**
	 * start to observe an update event of connecting hosts
	 */
	public void startToObserveUpdateOfConnectingHosts() {
		this.connectionInviter.addObserver(this);
	}

	/**
	 * stop observing an update event of connecting hosts
	 */
	public void stopObservingUpdateOfConnectingHosts() {
		this.connectionInviter.removeObserver(this);
	}

	/**
	 * @return socket
	 */
	public DatagramSocket getUdpSocket() {
		return this.connectionInviter.getSocket();
	}

	/**
	 * @return the connectionInviter
	 */
	public ConnectionInviterPeer getConnectionInviterPeer() {
		return this.connectionInviter;
	}

	/**
	 * this method is called after the communication is stopped by invitation
	 * timeout
	 */
	protected abstract void invitationTimeout();

	private class TimeoutMaker extends Thread {
		private long timeoutSec;
		protected InvitationService service;
		private volatile boolean isInterrpted;

		/**
		 * @param timeoutSec
		 * @param service
		 */
		public TimeoutMaker(long timeoutSec, InvitationService service) {
			this.timeoutSec = timeoutSec;
			this.service = service;
			this.isInterrpted = false;
		}

		@Override
		public void run() {
			try {
				Thread.sleep(this.timeoutSec * 1000);
			} catch (InterruptedException e) {
				// ignore
			}
			if (this.isInterrpted) {
				return;
			}
			this.service.stopInvitation(new EndListener<Void>() {
				@Override
				public void success(Void result) {
					TimeoutMaker.this.service.invitationTimeout();
				}

				@Override
				public void failure(Exception e) {
					// probably service has already been stopped.
				}
			});

		}

		public void interruptTimeout() {
			this.isInterrpted = true;
			this.interrupt();
		}
	}
}
