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
import java.util.Set;

import org.junit.Test;
import org.nognog.jmatcher.Host;
import org.nognog.jmatcher.JMatcher;
import org.nognog.jmatcher.client.Connector.ConnectorPeer;
import org.nognog.jmatcher.server.JMatcherDaemon;

import mockit.Mocked;
import mockit.Verifications;

/**
 * @author goshi 2015/12/29
 */
public class ConnectorTest {

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.client.Connector.ConnectorPeer#receiveMessage()}
	 * and
	 * {@link org.nognog.jmatcher.client.Connector.ConnectorPeer#receiveMessageFrom(Host)}
	 * and
	 * {@link org.nognog.jmatcher.client.Connector.ConnectorPeer#sendMessage(String)}
	 * and
	 * {@link org.nognog.jmatcher.client.Connector.ConnectorPeer#sendMessageTo(String, Host...)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testSendAndReceiveMessage() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestSendAndReceiveMessage(daemon, null, null, JMatcher.PORT - 1);
			this.doTestSendAndReceiveMessage(daemon, "entryCL", null, JMatcher.PORT - 1); //$NON-NLS-1$
			this.doTestSendAndReceiveMessage(daemon, null, "connectionCL", JMatcher.PORT - 1); //$NON-NLS-1$
			this.doTestSendAndReceiveMessage(daemon, "DIADORA", "PETER", JMatcher.PORT - 1); //$NON-NLS-1$ //$NON-NLS-2$
			try {
				this.doTestSendAndReceiveMessage(daemon, "tooLongNameあいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやいゆえよらりるれろわをん", null, JMatcher.PORT - 1); //$NON-NLS-1$
				fail();
			} catch (IllegalArgumentException e) {
				// success
			}
			try {
				this.doTestSendAndReceiveMessage(daemon, null, "tooLongNameあいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやいゆえよらりるれろわをん", JMatcher.PORT - 1); //$NON-NLS-1$
				fail();
			} catch (IllegalArgumentException e) {
				// success
			}
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 */
	@SuppressWarnings({ "boxing" })
	private void doTestSendAndReceiveMessage(JMatcherDaemon daemon, String connectionInviterName, String connectorName, int portTellerPort) throws Exception {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (final ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer(connectionInviterName, jmatcherHost)) {
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			assertThat(entryKey, is(not(nullValue())));
			final Connector connector = new Connector(connectorName, jmatcherHost);
			connector.setInternalNetworkPortTellerPort(portTellerPort);
			try (ConnectorPeer connectorPeer = connector.connect(entryKey)) {
				assertThat(connectorPeer, is(not(nullValue())));
				assertThat(connectionInviter.getConnectingHosts().size(), is(1));
				assertThat(((Host) connectionInviter.getConnectingHosts().toArray()[0]).getName(), is(connectorName));
				assertThat(connectorPeer.getConnectingHost().getName(), is(connectionInviterName));
			}

			// wait for connectionInviter to handle cancel-request
			Thread.sleep(250);
			assertThat(connectionInviter.getConnectingHosts().size(), is(0));
			try (ConnectorPeer connectorPeer = connector.connect(entryKey)) {
				assertThat(connectorPeer, is(not(nullValue())));
				assertThat(connectionInviter.getConnectingHosts().size(), is(1));
				assertThat(((Host) connectionInviter.getConnectingHosts().toArray()[0]).getName(), is(connectorName));
				assertThat(connectorPeer.getConnectingHost().getName(), is(connectionInviterName));
				final long timeBeforeStartingToExchangeMessages = System.nanoTime();
				System.out.println("start to exchange messages"); //$NON-NLS-1$
				this.testSendMessageFromConnectionInviterToConnector(connectionInviter, connectorPeer);
				System.out.println("finished sending two messages from inviter to connector " + ((System.nanoTime() - timeBeforeStartingToExchangeMessages) / 1000000) + "[ms]経過"); //$NON-NLS-1$ //$NON-NLS-2$
				this.testSendMessageFromConnectorToConnectionInviter(connectorPeer, connectionInviter);
				System.out.println("finished sending two messages from connector to inviter " + ((System.nanoTime() - timeBeforeStartingToExchangeMessages) / 1000000) + "[ms]経過"); //$NON-NLS-1$ //$NON-NLS-2$
				connectionInviter.stopInvitation();
				System.out.println("stopped the invitation " + +((System.nanoTime() - timeBeforeStartingToExchangeMessages) / 1000000) + "[ms]経過"); //$NON-NLS-1$ //$NON-NLS-2$
				this.testSendMessageFromConnectionInviterToConnector(connectionInviter, connectorPeer);
				System.out.println("finished sending two messages from inviter to connector " + ((System.nanoTime() - timeBeforeStartingToExchangeMessages) / 1000000) + "[ms]経過"); //$NON-NLS-1$ //$NON-NLS-2$
				this.testSendMessageFromConnectorToConnectionInviter(connectorPeer, connectionInviter);
				System.out.println("finished sending two messages from connector to inviter " + ((System.nanoTime() - timeBeforeStartingToExchangeMessages) / 1000000) + "[ms]経過"); //$NON-NLS-1$ //$NON-NLS-2$
				connectorPeer.close();
				System.out.println("finished exchanging messages"); //$NON-NLS-1$
				try {
					this.testSendMessageFromConnectorToConnectionInviter(connectorPeer, connectionInviter);
				} catch (Throwable t) {
					// success
				}
				try {
					this.testSendMessageFromConnectionInviterToConnector(connectionInviter, connectorPeer);
				} catch (Throwable t) {
					// success
				}
			}
		}
	}
	
	@SuppressWarnings({ "boxing", "static-method" })
	private void testSendMessageFromConnectionInviterToConnector(final ConnectionInviterPeer connectionInviter, final ConnectorPeer connectorPeer) {
		final Host connectorHost = (Host) connectionInviter.getConnectingHosts().toArray()[0];
		final String messageFromConnectionInviter1 = "from connectionInviter1"; //$NON-NLS-1$
		assertThat(connectionInviter.sendMessageTo(messageFromConnectionInviter1, connectorHost), is(true));
		final String messageFromInvalidMessage = connectorPeer.receiveMessageFrom(new Host(null, 0));
		assertThat(messageFromInvalidMessage, is(not(messageFromConnectionInviter1)));
		final String successMessage = connectorPeer.receiveMessageFrom(connectorPeer.getConnectingHost());
		assertThat(successMessage, is(messageFromConnectionInviter1));
		final String messageFromConnectionInviter2 = "from connectionInviter2"; //$NON-NLS-1$
		assertThat(connectionInviter.sendMessageTo(messageFromConnectionInviter2, connectorHost), is(true));
		final ReceivedMessage receivedMessage = connectorPeer.receiveMessage();
		assertThat(receivedMessage, is(not(nullValue())));
		assertThat(receivedMessage.getSender(), is(connectorPeer.getConnectingHost()));
		assertThat(receivedMessage.getMessage(), is(messageFromConnectionInviter2));
	}

	@SuppressWarnings({ "boxing", "static-method" })
	private void testSendMessageFromConnectorToConnectionInviter(final ConnectorPeer connectorPeer, final ConnectionInviterPeer connectionInviter) {
		final Host connectorHost = (Host) connectionInviter.getConnectingHosts().toArray()[0];
		final String messageFromConnector1 = "from connector1"; //$NON-NLS-1$
		assertThat(connectorPeer.sendMessage(messageFromConnector1), is(true));
		final String invalidMessage = connectionInviter.receiveMessageFrom(new Host(null, 0));
		assertThat(invalidMessage, is(not(messageFromConnector1)));
		final String successMessage = connectionInviter.receiveMessageFrom(connectorHost);
		assertThat(successMessage, is(messageFromConnector1));
		final String messageFromConnector2 = "from connector2"; //$NON-NLS-1$
		assertThat(connectorPeer.sendMessage(messageFromConnector2), is(true));		
		final ReceivedMessage receivedMessage = connectionInviter.receiveMessage();
		assertThat(receivedMessage.getSender(), is(connectorHost));
		assertThat(receivedMessage.getMessage(), is(messageFromConnector2));
	}

	/**
	 * Test method for {@link org.nognog.jmatcher.client.Connector#connect(int)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testConnect() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestConnect(daemon, JMatcher.PORT - 1);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @param i
	 * @throws IOException
	 */
	private void doTestConnect(JMatcherDaemon daemon, int portTellerPort) throws IOException {
		final String inviterName = "Assam"; //$NON-NLS-1$
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (final ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer(inviterName, jmatcherHost)) {
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			assertThat(entryKey, is(not(nullValue())));
			final String wrongJmatcherHost = "fake"; //$NON-NLS-1$
			final int correctEntryKey = entryKey.intValue();
			final int wrongEntryKey = (entryKey.intValue() + 1) % daemon.getBoundOfKeyNumber();
			final int wrongPortTellerPort = portTellerPort - 1;
			this.doTestConnectWith(wrongEntryKey, wrongJmatcherHost, wrongPortTellerPort, false);
			this.doTestConnectWith(wrongEntryKey, wrongJmatcherHost, portTellerPort, false);
			this.doTestConnectWith(wrongEntryKey, jmatcherHost, wrongPortTellerPort, false);
			this.doTestConnectWith(wrongEntryKey, jmatcherHost, portTellerPort, false);
			this.doTestConnectWith(correctEntryKey, wrongJmatcherHost, wrongPortTellerPort, false);
			this.doTestConnectWith(correctEntryKey, wrongJmatcherHost, portTellerPort, false);
			this.doTestConnectWith(correctEntryKey, jmatcherHost, wrongPortTellerPort, false);
			this.doTestConnectWith(correctEntryKey, jmatcherHost, portTellerPort, true);
			connectionInviter.setMaxSizeOfConnectingHosts(0);
			this.doTestConnectWith(correctEntryKey, jmatcherHost, portTellerPort, false);
		}
	}

	@SuppressWarnings({ "static-method" })
	private void doTestConnectWith(final int entryKey, final String jmatcherHost, int portTellerPort, boolean expected) throws IOException {
		final String connectorName = "Uva"; //$NON-NLS-1$
		final Connector connector = new Connector(connectorName, jmatcherHost);
		connector.setInternalNetworkPortTellerPort(portTellerPort);
		if (expected == false) {
			try (ConnectorPeer connectorPeer = connector.connect(entryKey)) {
				assertThat(connectorPeer, is(nullValue()));
			}
		} else {
			try (ConnectorPeer connectorPeer = connector.connect(entryKey)) {
				assertThat(connectorPeer, is(not(nullValue())));
				assertThat(connectorPeer.getConnectingHost(), is(not(nullValue())));
				assertThat(connectorPeer.getSocket(), is(not(nullValue())));
			}
		}
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.client.Connector.ConnectorPeer#addObserver(PeerObserver)}
	 * and
	 * {@link org.nognog.jmatcher.client.Connector.ConnectorPeer#removeObserver(PeerObserver)}
	 * .
	 * 
	 * @param observer
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testObserver(@Mocked PeerObserver observer) throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestObserver(daemon, JMatcher.PORT - 1, observer);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @param i
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@SuppressWarnings({ "static-method" })
	private void doTestObserver(JMatcherDaemon daemon, int portTellerPort, final PeerObserver mockObserver) throws IOException, InterruptedException {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (final ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer("Mandheling", jmatcherHost)) { //$NON-NLS-1$
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			final Connector connector = new Connector("mocha", jmatcherHost); //$NON-NLS-1$
			connector.setInternalNetworkPortTellerPort(portTellerPort);
			try (final ConnectorPeer connectorPeer = connector.connect(entryKey.intValue())) {
				verifyCountOfNotificationOfObserver(mockObserver, 0);
				connectionInviter.close();
				Thread.sleep(1000);
				verifyCountOfNotificationOfObserver(mockObserver, 0);
				connectorPeer.receiveMessage();
				verifyCountOfNotificationOfObserver(mockObserver, 0);
			}
		}
		// Almost the same so TODO extract common process
		try (final ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer("Mandheling", jmatcherHost)) { //$NON-NLS-1$
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			final Connector connector = new Connector("mocha", jmatcherHost); //$NON-NLS-1$
			connector.setInternalNetworkPortTellerPort(portTellerPort);
			try (final ConnectorPeer connectorPeer = connector.connect(entryKey.intValue())) {
				connectorPeer.addObserver(mockObserver);
				verifyCountOfNotificationOfObserver(mockObserver, 0);
				connectionInviter.close();
				Thread.sleep(1000);
				verifyCountOfNotificationOfObserver(mockObserver, 1);
				connectorPeer.receiveMessage();
				verifyCountOfNotificationOfObserver(mockObserver, 1);
			}
		}

		try (final ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer("Mandheling", jmatcherHost)) { //$NON-NLS-1$
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			final Connector connector = new Connector("mocha", jmatcherHost); //$NON-NLS-1$
			connector.setInternalNetworkPortTellerPort(portTellerPort);
			try (final ConnectorPeer connectorPeer = connector.connect(entryKey.intValue())) {
				connectorPeer.addObserver(mockObserver);
				connectorPeer.removeObserver(mockObserver);
				verifyCountOfNotificationOfObserver(mockObserver, 1);
				connectionInviter.close();
				Thread.sleep(1000);
				verifyCountOfNotificationOfObserver(mockObserver, 1);
				connectorPeer.receiveMessage();
				verifyCountOfNotificationOfObserver(mockObserver, 1);
			}
		}

		try (final ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer("Mandheling", jmatcherHost)) { //$NON-NLS-1$
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			final Connector connector = new Connector("mocha", jmatcherHost); //$NON-NLS-1$
			connector.setInternalNetworkPortTellerPort(portTellerPort);
			try (final ConnectorPeer connectorPeer = connector.connect(entryKey.intValue())) {
				connectorPeer.removeObserver(mockObserver);
				connectorPeer.addObserver(mockObserver);
				verifyCountOfNotificationOfObserver(mockObserver, 1);
				connectionInviter.close();
				Thread.sleep(1000);
				verifyCountOfNotificationOfObserver(mockObserver, 2);
				connectorPeer.receiveMessage();
				verifyCountOfNotificationOfObserver(mockObserver, 2);
			}
		}

		try (final ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer("Mandheling", jmatcherHost)) { //$NON-NLS-1$
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			final Connector connector = new Connector("mocha", jmatcherHost); //$NON-NLS-1$
			connector.setInternalNetworkPortTellerPort(portTellerPort);
			try (final ConnectorPeer connectorPeer = connector.connect(entryKey.intValue())) {
				connectorPeer.removeObserver(mockObserver);
				verifyCountOfNotificationOfObserver(mockObserver, 2);
				connectionInviter.close();
				Thread.sleep(1000);
				verifyCountOfNotificationOfObserver(mockObserver, 2);
				connectorPeer.receiveMessage();
				verifyCountOfNotificationOfObserver(mockObserver, 2);
			}
		}

		try (final ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer("Mandheling", jmatcherHost)) { //$NON-NLS-1$
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			final Connector connector = new Connector("mocha-nee", jmatcherHost); //$NON-NLS-1$
			connector.setInternalNetworkPortTellerPort(portTellerPort);
			try (final ConnectorPeer connectorPeer = connector.connect(entryKey.intValue())) {
				connectorPeer.addObserver(mockObserver);
				verifyCountOfNotificationOfObserver(mockObserver, 2);
				connectorPeer.disconnect();
				verifyCountOfNotificationOfObserver(mockObserver, 3);
				connectorPeer.close();
				verifyCountOfNotificationOfObserver(mockObserver, 3);
			}
		}
	}

	@SuppressWarnings({ "unused", "unchecked" })
	private static void verifyCountOfNotificationOfObserver(final PeerObserver mockObserver, final int expectedCount) {
		new Verifications() {
			{
				mockObserver.updateConnectingHosts((Set<Host>) any, UpdateEvent.REMOVE, (Host) any);
				times = expectedCount;
			}
		};
	}

	/**
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

	@SuppressWarnings({ "boxing", "static-method" })
	private void doTestDisconnect(JMatcherDaemon daemon, int portTellerPort) throws Exception {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (final ConnectionInviterPeer connectionInviter = new ConnectionInviterPeer("hot-cocoa", jmatcherHost)) { //$NON-NLS-1$
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			assertThat(entryKey, is(not(nullValue())));
			final Connector connector = new Connector("ice-cocoa", jmatcherHost); //$NON-NLS-1$
			connector.setInternalNetworkPortTellerPort(portTellerPort);
			try (ConnectorPeer connectorPeer = connector.connect(entryKey)) {
				assertThat(connectorPeer, is(not(nullValue())));
				assertThat(connectorPeer.isOnline(), is(true));
				assertThat(connectorPeer.getConnectingHost(), is(not(nullValue())));
				connectorPeer.disconnect();
				assertThat(connectorPeer.isOnline(), is(true));
				assertThat(connectorPeer.getConnectingHost(), is(nullValue()));
				connectorPeer.close();
				assertThat(connectorPeer.isOnline(), is(false));
				assertThat(connectorPeer.getConnectingHost(), is(nullValue()));
			}
		}
	}
}
