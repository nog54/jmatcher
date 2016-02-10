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

package org.nognog.jmatcher.client;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.nognog.jmatcher.Host;
import org.nognog.jmatcher.JMatcher;
import org.nognog.jmatcher.client.Connector.ConnectorPeer;
import org.nognog.jmatcher.server.JMatcherDaemon;

import mockit.Deencapsulation;
import mockit.Mocked;
import mockit.Verifications;

/**
 * @author goshi 2015/12/28
 */
@SuppressWarnings({ "static-method", "boxing" })
public class ConnectionInviterPeerTest {

	/**
	 * Test method for {@link org.nognog.jmatcher.client.ConnectionInviterPeer#startInvitation()}.
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testStartInvitation() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doStartInvitation(daemon, JMatcher.PORT - 1);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void doStartInvitation(JMatcherDaemon daemon, int portTellerPort) throws IOException, InterruptedException {
		final String wrongJmatcherHost = "rokalfost"; //$NON-NLS-1$
		final int wrongPort = 80;
		try (final ConnectionInviterPeer inviter = new ConnectionInviterPeer(null, wrongJmatcherHost, wrongPort)) {
			inviter.setPortTellerPort(portTellerPort);
			assertThat(inviter.getJmatcherServer(), is(wrongJmatcherHost));
			assertThat(inviter.getConnectingHosts(), is(not(nullValue())));
			assertThat(inviter.getConnectingHosts().size(), is(0));
			assertThat(inviter.getJMatcherServerPort(), is(wrongPort));
			assertThat(inviter.getRetryCount(), is(ConnectionInviterPeer.defalutRetryCount));
			// ---- Test with wrong configuration ----
			try {
				inviter.startInvitation();
				fail();
			} catch (IOException e) {
				// ok
			}
			final String jmatcherHost = "localhost"; //$NON-NLS-1$
			inviter.setJMatcherServer(jmatcherHost);

			try {
				inviter.startInvitation();
				fail();
			} catch (IOException e) {
				// ok
			}
			inviter.setJMatcherServerPort(JMatcher.PORT);
			// ---- start invitation correctly ----
			final Integer entryKey1 = inviter.startInvitation();
			assertThat(entryKey1, is(not(nullValue())));
			assertThat(daemon.getMatchingMap().keySet().contains(entryKey1), is(true));
			assertThat(daemon.getMatchingMap().keySet().size(), is(1));
			// ---- start invitation in case it has already started ----
			final Integer entryKey2 = inviter.startInvitation();
			assertThat(entryKey2, is(nullValue()));
			assertThat(daemon.getMatchingMap().keySet().contains(entryKey1), is(true));
			assertThat(daemon.getMatchingMap().keySet().size(), is(1));
			// ---- stop invitation ----
			inviter.stopInvitation();
			Thread.sleep(500); // wait for end of TCPClientRequestHandler
			assertThat(daemon.getMatchingMap().keySet().contains(entryKey1), is(false));
			assertThat(daemon.getMatchingMap().keySet().size(), is(0));
			// ---- start invitation in case it still has connection in udp ----
			final Integer entryKey3 = inviter.startInvitation();
			assertThat(entryKey3, is(nullValue()));
			assertThat(daemon.getMatchingMap().keySet().contains(entryKey1), is(false));
			assertThat(daemon.getMatchingMap().keySet().size(), is(0));

			inviter.stopCommunication();
			// ---- start invitation again after close all ----
			final Integer entryKey4 = inviter.startInvitation();
			assertThat(entryKey4, is(not(nullValue())));
			assertThat(daemon.getMatchingMap().keySet().contains(entryKey4), is(true));
			assertThat(daemon.getMatchingMap().keySet().size(), is(1));

			inviter.stopCommunication();
			daemon.setMatchingMapCapacity(0);
			// ---- start invitation in case the matching map is full ----
			final Integer entryKey5 = inviter.startInvitation();
			assertThat(entryKey5, is(nullValue()));
			assertThat(daemon.getMatchingMap().size(), is(0));
			daemon.setMatchingMapCapacity(JMatcherDaemon.DEFAULT_MATCHING_MAP_CAPACITY);
			// ---- start invitation after the matching map become not full ----
			final Integer entryKey6 = inviter.startInvitation();
			assertThat(entryKey6, is(not(nullValue())));
			inviter.stopCommunication();
			// ---- start invitation after stopCommunication without stopInvitation ----
			final Integer entryKey7 = inviter.startInvitation();
			assertThat(entryKey7, is(not(nullValue())));
			inviter.stopCommunication();
			Thread.sleep(500); // wait for end of TCPClientRequestHandler
			assertThat(daemon.getMatchingMap().keySet().size(), is(0));
		}
	}

	/**
	 * @throws Exception
	 */
	@Test
	public final void testInvitationWithConnectionClient() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestInvitationWithConnectionClient(daemon, JMatcher.PORT - 1);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 */
	private void doTestInvitationWithConnectionClient(JMatcherDaemon daemon, int portTellerPort) throws IOException {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		this.testInvitationWithFixedMaxSizeOfConnectingHosts(daemon, jmatcherHost, portTellerPort);
		this.testInvitationWithUnfixedMaxSizeOfConnectingHosts(jmatcherHost, portTellerPort);
	}

	private void testInvitationWithFixedMaxSizeOfConnectingHosts(JMatcherDaemon daemon, final String jmatcherHost, int portTellerPort) throws IOException {
		try (ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer(null, jmatcherHost)) {
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			final int numberOfParallelConnectionClient = 10;
			this.testConnect(jmatcherHost, portTellerPort, entryKey, numberOfParallelConnectionClient, numberOfParallelConnectionClient);
			assertThat(connectionInviter.getConnectingHosts().size(), is(numberOfParallelConnectionClient));
			this.testConnect(jmatcherHost, portTellerPort, entryKey, 1, 1);
			assertThat(connectionInviter.getConnectingHosts().size(), is(numberOfParallelConnectionClient + 1));
			assertThat(daemon.getMatchingMap().size(), is(1));
			connectionInviter.stopInvitation();
			assertThat(connectionInviter.getConnectingHosts().size(), is(numberOfParallelConnectionClient + 1));
			connectionInviter.stopCommunication();
			assertThat(connectionInviter.getConnectingHosts().size(), is(0));
			try {
				// wait for end of TCPClientRequestHandler in the daemon
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// nothing
			}
			assertThat(daemon.getMatchingMap().size(), is(0));
		}
	}

	private void testInvitationWithUnfixedMaxSizeOfConnectingHosts(final String jmatcherHost, int portTellerPort) throws IOException {
		try (ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer(null, jmatcherHost)) {
			connectionInviter.setPortTellerPort(portTellerPort);
			final int numberOfParallelConnectionClient = 20;
			final int maxSizeOfConnectingHosts1 = 15;
			connectionInviter.setMaxSizeOfConnectingHosts(maxSizeOfConnectingHosts1);
			final Integer entryKey = connectionInviter.startInvitation();

			this.testConnect(jmatcherHost, portTellerPort, entryKey, numberOfParallelConnectionClient, maxSizeOfConnectingHosts1);
			assertThat(connectionInviter.getConnectingHosts().size(), is(maxSizeOfConnectingHosts1));

			final int maxSizeOfConnectingHosts2 = 24;
			connectionInviter.setMaxSizeOfConnectingHosts(maxSizeOfConnectingHosts2);
			this.testConnect(jmatcherHost, portTellerPort, entryKey, numberOfParallelConnectionClient, maxSizeOfConnectingHosts2 - maxSizeOfConnectingHosts1);
			assertThat(connectionInviter.getConnectingHosts().size(), is(maxSizeOfConnectingHosts2));

			final int maxSizeOfConnectingHosts3 = 22;
			connectionInviter.setMaxSizeOfConnectingHosts(maxSizeOfConnectingHosts3);
			this.testConnect(jmatcherHost, portTellerPort, entryKey, numberOfParallelConnectionClient, 0);
			assertThat(connectionInviter.getConnectingHosts().size(), is(maxSizeOfConnectingHosts2));

			final int removeCountOfClient = maxSizeOfConnectingHosts2 / 2;
			removeConnectingHostsForcibly(connectionInviter, removeCountOfClient);
			try {
				this.testConnect(jmatcherHost, portTellerPort, entryKey, numberOfParallelConnectionClient, maxSizeOfConnectingHosts3 - (maxSizeOfConnectingHosts2 - removeCountOfClient));
			} finally {
				System.out.println(connectionInviter.getConnectingHosts().size());
			}
			assertThat(connectionInviter.getConnectingHosts().size(), is(maxSizeOfConnectingHosts3));
		}
	}

	/**
	 * @param connectionInviter
	 * @param removeCountOfClient
	 */
	private static void removeConnectingHostsForcibly(ConnectionInviterPeer connectionInviter, int removeCountOfClient) {
		final Set<Host> connectingHosts = Deencapsulation.getField(connectionInviter, "connectingHosts"); //$NON-NLS-1$
		final Map<Host, InetSocketAddress> socketAddressCache = Deencapsulation.getField(connectionInviter, "socketAddressCache"); //$NON-NLS-1$
		final ReceivedMessageBuffer receivedMessageBuffer = Deencapsulation.getField(connectionInviter, "receivedMessageBuffer"); //$NON-NLS-1$
		int count = 0;
		for (Host host : connectionInviter.getConnectingHosts()) {
			if (count++ >= removeCountOfClient) {
				break;
			}
			connectingHosts.remove(host);
			socketAddressCache.remove(host);
			receivedMessageBuffer.clear(host);
		}
	}

	private void testConnect(final String jmatcherHost, final int portTellerPort, final Integer entryKey, final int numberOfParallelConnectors, int expectSuccessCounts) {
		final Thread[] threads = new Thread[numberOfParallelConnectors];
		final AtomicInteger failedThreadsCount = new AtomicInteger(0);
		for (int i = 0; i < numberOfParallelConnectors; i++) {
			threads[i] = new Thread(new Runnable() {
				@SuppressWarnings("resource")
				@Override
				public void run() {
					final Connector parallelConnector = new Connector(null, jmatcherHost);
					parallelConnector.setInternalNetworkPortTellerPort(portTellerPort);
					try {
						final ConnectorPeer peer = parallelConnector.connect(entryKey);
						assertThat(peer, is(is(not(nullValue()))));
					} catch (IOException | AssertionError e) {
						failedThreadsCount.incrementAndGet();
					}
				}
			});
			threads[i].start();
		}
		for (int i = 0; i < numberOfParallelConnectors; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				fail();
			}
		}
		final int actualSuccessCount = numberOfParallelConnectors - failedThreadsCount.intValue();
		assertThat(actualSuccessCount, is(expectSuccessCounts));
	}

	/**
	 * @param observer
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testNotifyObservers(@Mocked ConnectionInviterPeerObserver observer) throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestObservers(daemon, observer, JMatcher.PORT - 1);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 */
	private void doTestObservers(JMatcherDaemon daemon, final ConnectionInviterPeerObserver observer, int tellerPort) throws Exception {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer(null, jmatcherHost)) {
			connectionInviter.setPortTellerPort(tellerPort);
			Integer key = connectionInviter.startInvitation();
			assertThat(key, is(not(nullValue())));
			Connector connector = new Connector(null, jmatcherHost);
			connector.setInternalNetworkPortTellerPort(tellerPort);
			try (Peer peer = connector.connect(key)) {
				this.verifyCountOfNotificationOfObserver(observer, 0);
			}
			this.verifyCountOfNotificationOfObserver(observer, 0);
			connectionInviter.addObserver(observer);
			try (Peer peer = connector.connect(key)) {
				this.verifyCountOfNotificationOfObserver(observer, 1);
			}
			this.verifyCountOfNotificationOfObserver(observer, 2);
			try (Peer peer = connector.connect(key)) {
				this.verifyCountOfNotificationOfObserver(observer, 3);
				connectionInviter.stopInvitation();
				this.verifyCountOfNotificationOfObserver(observer, 3);
				connectionInviter.stopCommunication();
				this.verifyCountOfNotificationOfObserver(observer, 4);
				key = connectionInviter.startInvitation();
				this.verifyCountOfNotificationOfObserver(observer, 4);
			}
			this.verifyCountOfNotificationOfObserver(observer, 4);
			try (Peer peer = connector.connect(key)) {
				this.verifyCountOfNotificationOfObserver(observer, 5);
			}
		}
	}

	@SuppressWarnings({ "unused", "unchecked" })
	private void verifyCountOfNotificationOfObserver(final ConnectionInviterPeerObserver observer, final int expectedTimes) {
		new Verifications() {
			{
				observer.updateConnectingHosts((Set<Host>) any, (UpdateEvent) any, (Host) any);
				times = expectedTimes;
			}
		};
	}
}
