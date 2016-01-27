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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.nognog.jmatcher.JMatcher;
import org.nognog.jmatcher.client.JMatcherConnectionClient;
import org.nognog.jmatcher.client.JMatcherEntryClient;
import org.nognog.jmatcher.server.JMatcherDaemon;

import mockit.Mocked;
import mockit.Verifications;

/**
 * @author goshi 2016/01/26
 */
@SuppressWarnings({ "boxing" })
public class InvitationServiceClientTest {

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.client.service.InvitationServiceClient#connect(int, org.nognog.jmatcher.client.service.EndListener)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testConnect() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try (final JMatcherEntryClient entryClient = new JMatcherEntryClient("Colombia", "localhost")) { //$NON-NLS-1$ //$NON-NLS-2$
			final int portTellerPort = JMatcher.PORT - 1;
			entryClient.setPortTellerPort(portTellerPort);
			final Integer key = entryClient.startInvitation();
			this.doConnectTest(key, portTellerPort);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param key
	 * @throws InterruptedException
	 */
	private void doConnectTest(Integer key, int portTellerPort) throws InterruptedException {
		this.doConnectTestWith(key, portTellerPort, "Kenya", "localhost", true); //$NON-NLS-1$ //$NON-NLS-2$
		this.doConnectTestWith(key, portTellerPort, "Guatemala", "wrongHost", false); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void doConnectTestWith(Integer key, int portTellerPort, String name, String server, boolean expected) throws InterruptedException {
		try (JMatcherConnectionClient jmatcherConnectionClient = new JMatcherConnectionClient(name, server)) {
			jmatcherConnectionClient.setInternalNetworkPortTellerPort(portTellerPort);
			try (final InvitationServiceClient client = new InvitationServiceClient(jmatcherConnectionClient)) {
				final AtomicBoolean isSuccess = new AtomicBoolean();
				final EndListener<Void> listener = new EndListener<Void>() {
					@Override
					public void success(Void result) {
						isSuccess.set(true);
						synchronized (InvitationServiceClientTest.this) {
							InvitationServiceClientTest.this.notifyAll();
						}
					}

					@Override
					public void failure(Exception e) {
						isSuccess.set(false);
						synchronized (InvitationServiceClientTest.this) {
							InvitationServiceClientTest.this.notifyAll();
						}
					}
				};
				client.connect(key, listener);
				synchronized (this) {
					this.wait();
				}
				assertThat(isSuccess.get(), is(expected));
			}
		}
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.client.service.InvitationServiceClient#connect(int, org.nognog.jmatcher.client.service.EndListener)}
	 * .
	 * 
	 * @param listener
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testCancelConnection(@Mocked EndListener<Void> listener) throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try (final JMatcherEntryClient entryClient = new JMatcherEntryClient("Colombia", "localhost")) { //$NON-NLS-1$ //$NON-NLS-2$
			final int portTellerPort = JMatcher.PORT - 1;
			entryClient.setPortTellerPort(portTellerPort);
			final Integer key = entryClient.startInvitation();
			this.doTestCancelConnectionSuite(key, portTellerPort);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param key
	 * @param portTellerPort
	 * @param listener
	 * @throws InterruptedException
	 */
	private void doTestCancelConnectionSuite(Integer key, int portTellerPort) throws Exception {
		try (JMatcherConnectionClient jmatcherConnectionClient = new JMatcherConnectionClient("Crystal mountain", "localhost")) { //$NON-NLS-1$ //$NON-NLS-2$
			jmatcherConnectionClient.setInternalNetworkPortTellerPort(portTellerPort);
			try (final InvitationServiceClient client = new InvitationServiceClient(jmatcherConnectionClient)) {
				// before connect
				this.doTestCancelConnection(client, false);

				// cancel while trying to connect
				client.connect(key, null);
				this.doTestCancelConnection(client, false);
				client.close();
				
				// cancel after connecting
				this.makeUpConnection(key, client);
				this.doTestCancelConnection(client, true);

				// closed while cancelling
				this.makeUpConnection(key, client);
				this.doTestCancelConnectionWithClose(client, false);
			}
		}
	}

	private void doTestCancelConnection(final InvitationServiceClient client, boolean expected) throws InterruptedException {
		final AtomicBoolean actual = new AtomicBoolean();
		final EndListener<Void> listener = new EndListener<Void>() {
			@Override
			public void success(Void result) {
				actual.set(true);
				synchronized (InvitationServiceClientTest.this) {
					InvitationServiceClientTest.this.notifyAll();
				}
			}

			@Override
			public void failure(Exception e) {
				actual.set(false);
				synchronized (InvitationServiceClientTest.this) {
					InvitationServiceClientTest.this.notifyAll();
				}
			}
		};
		client.cancelConnection(listener);
		synchronized (this) {
			this.wait();
		}
		assertThat(actual.get(), is(expected));
	}
	
	private void doTestCancelConnectionWithClose(final InvitationServiceClient client, boolean expected) throws InterruptedException {
		final AtomicBoolean actual = new AtomicBoolean();
		final EndListener<Void> listener = new EndListener<Void>() {
			@Override
			public void success(Void result) {
				actual.set(true);
				synchronized (InvitationServiceClientTest.this) {
					InvitationServiceClientTest.this.notifyAll();
				}
			}

			@Override
			public void failure(Exception e) {
				actual.set(false);
				synchronized (InvitationServiceClientTest.this) {
					InvitationServiceClientTest.this.notifyAll();
				}
			}
		};
		client.cancelConnection(listener);
		client.close();
		synchronized (this) {
			this.wait();
		}
		assertThat(actual.get(), is(expected));
	}
	
	/**
	 * @param client
	 * @throws InterruptedException 
	 */
	private void makeUpConnection(int key, InvitationServiceClient client) throws InterruptedException {
		final EndListener<Void> listener = new EndListener<Void>() {
			@Override
			public void success(Void result) {
				synchronized (InvitationServiceClientTest.this) {
					InvitationServiceClientTest.this.notifyAll();
				}
			}

			@Override
			public void failure(Exception e) {
				synchronized (InvitationServiceClientTest.this) {
					InvitationServiceClientTest.this.notifyAll();
				}
			}
		};
		client.connect(key, listener);
		synchronized (this) {
			this.wait();
		}
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.client.service.InvitationServiceClient#connect(int, org.nognog.jmatcher.client.service.EndListener)}
	 * .
	 * 
	 * @param listener
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testClose(@Mocked EndListener<Void> listener) throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try (final JMatcherEntryClient entryClient = new JMatcherEntryClient("Colombia", "localhost")) { //$NON-NLS-1$ //$NON-NLS-2$
			final int portTellerPort = JMatcher.PORT - 1;
			entryClient.setPortTellerPort(portTellerPort);
			final Integer key = entryClient.startInvitation();
			this.doTestClose(key, portTellerPort, listener);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param key
	 * @param portTellerPort
	 * @throws InterruptedException
	 */
	private void doTestClose(Integer key, int portTellerPort, EndListener<Void> mockListener) throws InterruptedException {
		try (JMatcherConnectionClient jmatcherConnectionClient = new JMatcherConnectionClient("Crystal mountain", "localhost")) { //$NON-NLS-1$ //$NON-NLS-2$
			jmatcherConnectionClient.setInternalNetworkPortTellerPort(portTellerPort);
			try (final InvitationServiceClient client = new InvitationServiceClient(jmatcherConnectionClient)) {
				this.testCloseAfterConnect(key, client);
				this.testCloseWhileConnecting(key, client, mockListener);
			}
		}
	}

	private void testCloseAfterConnect(Integer key, final InvitationServiceClient client) throws InterruptedException {
		final EndListener<Void> listener = new EndListener<Void>() {
			@Override
			public void success(Void result) {
				synchronized (InvitationServiceClientTest.this) {
					InvitationServiceClientTest.this.notifyAll();
				}
			}

			@Override
			public void failure(Exception e) {
				synchronized (InvitationServiceClientTest.this) {
					InvitationServiceClientTest.this.notifyAll();
				}
			}
		};
		client.connect(key, listener);
		synchronized (this) {
			this.wait();
		}
		assertThat(client.getConnectingHost(), is(not(nullValue())));
		assertThat(client.getConnectingSocket(), is(not(nullValue())));
		client.close();
		assertThat(client.getConnectingHost(), is(nullValue()));
		assertThat(client.getConnectingSocket(), is(nullValue()));
	}

	/**
	 * @param key
	 * @param client
	 * @param mockListener
	 * @throws InterruptedException
	 */
	@SuppressWarnings({ "unused", "static-method" })
	private void testCloseWhileConnecting(Integer key, InvitationServiceClient client, final EndListener<Void> mockListener) throws InterruptedException {
		client.connect(key, mockListener);
		new Verifications() {
			{
				mockListener.success((Void) any);
				times = 0;
				mockListener.failure((Exception) any);
				times = 0;
			}
		};
		assertThat(client.getConnectingHost(), is(nullValue()));
		assertThat(client.getConnectingSocket(), is(not(nullValue())));
		client.close();
		Thread.sleep(2000);
		new Verifications() {
			{
				mockListener.success((Void) any);
				times = 0;
				mockListener.failure((Exception) any);
				times = 1;
			}
		};
		assertThat(client.getConnectingHost(), is(nullValue()));
		assertThat(client.getConnectingSocket(), is(nullValue()));
	}
}
