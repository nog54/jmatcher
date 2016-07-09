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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
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
	 * Test method for
	 * {@link org.nognog.jmatcher.client.ConnectionInviterPeer#startInvitation()}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testStartInvitation() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestStartInvitation(daemon, JMatcher.PORT - 1);
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
	private void doTestStartInvitation(JMatcherDaemon daemon, int portTellerPort) throws IOException, InterruptedException {
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
			// ---- start invitation after stopCommunication without
			// stopInvitation ----
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
	public final void testNotifyObservers(@Mocked PeerObserver observer) throws Exception {
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
	private void doTestObservers(JMatcherDaemon daemon, final PeerObserver observer, int tellerPort) throws Exception {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		final int delayTime = 300;
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
				Thread.sleep(delayTime);
				this.verifyCountOfNotificationOfObserver(observer, 1);
			}
			this.verifyCountOfNotificationOfObserver(observer, 2);
			try (Peer peer = connector.connect(key)) {
				Thread.sleep(delayTime);
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
				Thread.sleep(delayTime);
				this.verifyCountOfNotificationOfObserver(observer, 5);
				peer.disconnect(peer.getConnectingHosts().toArray(new Host[0])[0]);
				this.verifyCountOfNotificationOfObserver(observer, 6);
			}
			this.verifyCountOfNotificationOfObserver(observer, 6);
		}
		this.verifyCountOfNotificationOfObserver(observer, 7);
	}

	@SuppressWarnings({ "unused", "unchecked" })
	private void verifyCountOfNotificationOfObserver(final PeerObserver observer, final int expectedTimes) {
		new Verifications() {
			{
				observer.updateConnectingHosts((Set<Host>) any, (UpdateEvent) any, (Host) any);
				times = expectedTimes;
			}
		};
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.client.ConnectionInviterPeer#receiveMessage()}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testReceiveMessageSpeed() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestReceiveMessageSpeed(daemon, JMatcher.PORT - 1);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doTestReceiveMessageSpeed(JMatcherDaemon daemon, int portTellerPort) throws IOException {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer(null, jmatcherHost)) {
			connectionInviter.setPortTellerPort(portTellerPort);
			connectionInviter.setLogger(LogManager.getLogger(this.getClass()));
			final Integer entryKey = connectionInviter.startInvitation();
			this.testReceiveMessageSpeed(portTellerPort, jmatcherHost, connectionInviter, entryKey);
		}
	}

	private void testReceiveMessageSpeed(int portTellerPort, final String jmatcherHost, ConnectionInviterPeer connectionInviter, final Integer entryKey) throws IOException, SocketException {
		final int numberOFConnectorPeer = 10;
		final ConnectorPeer[] connectorPeers = this.createConnectorPeers(portTellerPort, jmatcherHost, entryKey, numberOFConnectorPeer);
		final String message = "java"; //$NON-NLS-1$
		final int delayTime = 500;
		if (delayTime > connectionInviter.getSocket().getSoTimeout() / 2) {
			System.err.println("delay time should be more small to ensure that this test runs correctly"); //$NON-NLS-1$
		}
		for (final ConnectorPeer connectorPeer : connectorPeers) {
			new Thread() {
				@Override
				public void run() {
					try {
						Thread.sleep(delayTime);
						connectorPeer.sendMessage(message);
					} catch (InterruptedException e) {
						System.err.println("unexpected interrupt occured"); //$NON-NLS-1$
					}
				}
			}.start();
		}
		final long startTime = System.currentTimeMillis();
		for (int i = 0; i < numberOFConnectorPeer; i++) {
			final ReceivedMessage receivedMessage = connectionInviter.receiveMessage();
			assertThat(receivedMessage, is(not(nullValue())));
			assertThat(receivedMessage.getMessage(), is(message));
		}
		final long endTime = System.currentTimeMillis();
		final long expectedTime = delayTime + 200; // 適当
		assertThat(endTime - startTime, is(lessThan(expectedTime)));
		for (final ConnectorPeer connectorPeer : connectorPeers) {
			connectorPeer.close();
		}
	}

	private ConnectorPeer[] createConnectorPeers(int portTellerPort, final String jmatcherHost, final Integer entryKey, final int numberOFConnectorPeer) throws IOException {
		final ConnectorPeer[] connectorPeers = new ConnectorPeer[numberOFConnectorPeer];
		for (int i = 0; i < connectorPeers.length; i++) {
			final Connector connector = new Connector("tea" + i, jmatcherHost); //$NON-NLS-1$
			connector.setInternalNetworkPortTellerPort(portTellerPort);
			connectorPeers[i] = connector.connect(entryKey);
		}
		return connectorPeers;
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.client.ConnectionInviterPeer#receiveMessage()}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testReceiveMessageTimeout() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestReceiveMessageTimeout(daemon, JMatcher.PORT - 1);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doTestReceiveMessageTimeout(JMatcherDaemon daemon, int portTellerPort) throws IOException {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer(null, jmatcherHost)) {
			connectionInviter.setPortTellerPort(portTellerPort);
			connectionInviter.setLogger(LogManager.getLogger(this.getClass()));
			final Integer entryKey = connectionInviter.startInvitation();
			assertThat(entryKey, is(not(nullValue())));
			this.testReceiveMessageTimeout(connectionInviter);
		}
	}

	private void testReceiveMessageTimeout(ConnectionInviterPeer connectionInviter) throws IOException, SocketException {
		final int originalSoTimeout = connectionInviter.getSocket().getSoTimeout();
		final long startTime1 = System.currentTimeMillis();
		final ReceivedMessage message1 = connectionInviter.receiveMessage();
		final long endTime1 = System.currentTimeMillis();
		assertThat(message1, is(nullValue()));
		assertThat(endTime1 - startTime1, is(greaterThanOrEqualTo((long) originalSoTimeout)));

		final int newSoTimeout = originalSoTimeout * 2;
		connectionInviter.getSocket().setSoTimeout(newSoTimeout);
		try {
			// sleep to apply the newSoTimeout
			Thread.sleep(originalSoTimeout);
		} catch (InterruptedException e) {
			fail();
		}

		final long startTime2 = System.currentTimeMillis();
		final ReceivedMessage message2 = connectionInviter.receiveMessage();
		final long endTime2 = System.currentTimeMillis();
		assertThat(message2, is(nullValue()));
		assertThat(endTime2 - startTime2, is(greaterThanOrEqualTo((long) newSoTimeout)));
	}

	/**
	 * @param observer
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testDisconnect() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestDisconnect(daemon, JMatcher.PORT - 1);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doTestDisconnect(JMatcherDaemon daemon, int portTellerPort) throws IOException {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer(null, jmatcherHost)) {
			connectionInviter.setPortTellerPort(portTellerPort);
			Integer key = connectionInviter.startInvitation();
			assertThat(key, is(not(nullValue())));
			Connector connector = new Connector(null, jmatcherHost);
			connector.setInternalNetworkPortTellerPort(portTellerPort);
			try (Peer peer = connector.connect(key)) {
				assertThat(peer.isOnline(), is(true));
				final Set<Host> connectingHosts = connectionInviter.getConnectingHosts();
				assertThat(connectingHosts.size(), is(1));
				connectionInviter.disconnect(connectingHosts.toArray(new Host[0])[0]);
				assertThat(connectionInviter.getConnectingHosts().size(), is(0));
				assertThat(connectionInviter.isOnline(), is(true));
				assertThat(connectionInviter.isInviting(), is(true));
				assertThat(connectionInviter.isCommunicating(), is(true));
				assertThat(connectionInviter.getSocket().isClosed(), is(false));
				assertThat(peer.isOnline(), is(true));
				assertThat(peer.getConnectingHosts().size(), is(1));
				peer.receiveMessage();
				assertThat(peer.isOnline(), is(true));
				assertThat(peer.getConnectingHosts().size(), is(0));
			}
		}
	}
}
