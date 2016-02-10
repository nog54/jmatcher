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

import org.junit.Test;
import org.nognog.jmatcher.Host;
import org.nognog.jmatcher.JMatcher;
import org.nognog.jmatcher.client.Connector.ConnectorPeer;
import org.nognog.jmatcher.server.JMatcherDaemon;

/**
 * @author goshi 2015/12/29
 */
public class ConnectorTest {

	/**
	 * Test method for {@link org.nognog.jmatcher.client.Connector.ConnectorPeer#receiveMessage()} and {@link org.nognog.jmatcher.client.Connector.ConnectorPeer#receiveMessageFrom(Host)} and
	 * {@link org.nognog.jmatcher.client.Connector.ConnectorPeer#sendMessage(String)} and {@link org.nognog.jmatcher.client.Connector.ConnectorPeer#sendMessageTo(String, Host...)}.
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
		try (final ConnectionInviter connectionInviter = new ConnectionInviter(connectionInviterName, jmatcherHost)) {
			connectionInviter.setPortTellerPort(portTellerPort);
			final Integer entryKey = connectionInviter.startInvitation();
			assertThat(entryKey, is(not(nullValue())));
			final Connector requester = new Connector(connectorName, jmatcherHost);
			requester.setInternalNetworkPortTellerPort(portTellerPort);
			try (ConnectorPeer connectorPeer = requester.connect(entryKey)) {
				assertThat(connectorPeer, is(not(nullValue())));
				assertThat(connectionInviter.getConnectingHosts().size(), is(1));
				assertThat(((Host) connectionInviter.getConnectingHosts().toArray()[0]).getName(), is(connectorName));
				assertThat(connectorPeer.getConnectingHost().getName(), is(connectionInviterName));
			}

			// wait for connectionInviter to handle cancel-request
			Thread.sleep(250);
			assertThat(connectionInviter.getConnectingHosts().size(), is(0));
			try (ConnectorPeer connectionRequesterPeer = requester.connect(entryKey)) {
				assertThat(connectionRequesterPeer, is(not(nullValue())));
				assertThat(connectionInviter.getConnectingHosts().size(), is(1));
				assertThat(((Host) connectionInviter.getConnectingHosts().toArray()[0]).getName(), is(connectorName));
				assertThat(connectionRequesterPeer.getConnectingHost().getName(), is(connectionInviterName));
				this.testSendMessageFromConnectorToConnectionInviter(connectionRequesterPeer, connectionInviter);
				this.testSendMessageFromConnectionInviterToConnector(connectionInviter, connectionRequesterPeer);
				connectionInviter.stopInvitation();
				this.testSendMessageFromConnectorToConnectionInviter(connectionRequesterPeer, connectionInviter);
				this.testSendMessageFromConnectionInviterToConnector(connectionInviter, connectionRequesterPeer);
				connectionRequesterPeer.close();
				try {
					this.testSendMessageFromConnectorToConnectionInviter(connectionRequesterPeer, connectionInviter);
				} catch (Throwable t) {
					// success
				}
				try {
					this.testSendMessageFromConnectionInviterToConnector(connectionInviter, connectionRequesterPeer);
				} catch (Throwable t) {
					// success
				}
			}
		}
	}

	@SuppressWarnings({ "boxing", "static-method" })
	private void testSendMessageFromConnectorToConnectionInviter(final ConnectorPeer connectorPeer, final ConnectionInviter connectionInviter) {
		final Host connectorHost = (Host) connectionInviter.getConnectingHosts().toArray()[0];
		final String messageFromConnector1 = "from connector1"; //$NON-NLS-1$
		assertThat(connectorPeer.sendMessage(messageFromConnector1), is(true));
		final String failedMessage = connectionInviter.receiveMessageFrom(new Host(null, 0));
		assertThat(failedMessage, is(not(messageFromConnector1)));
		final String successMessage = connectionInviter.receiveMessageFrom(connectorHost);
		assertThat(successMessage, is(messageFromConnector1));

		final String messageFromConnector2 = "from connector2"; //$NON-NLS-1$
		assertThat(connectorPeer.sendMessage(messageFromConnector2), is(true));
		final ReceivedMessage receivedMessage = connectionInviter.receiveMessage();
		assertThat(receivedMessage.getSender(), is(connectorHost));
		assertThat(receivedMessage.getMessage(), is(messageFromConnector2));
	}

	@SuppressWarnings({ "boxing", "static-method" })
	private void testSendMessageFromConnectionInviterToConnector(final ConnectionInviter connectionInviter, final ConnectorPeer connectorPeer) {
		final Host connectorHost = (Host) connectionInviter.getConnectingHosts().toArray()[0];
		final String messageFromConnectionInviter1 = "from connectionInviter1"; //$NON-NLS-1$
		assertThat(connectionInviter.sendMessageTo(messageFromConnectionInviter1, connectorHost), is(true));
		final String failedMessage = connectorPeer.receiveMessageFrom(new Host(null, 0));
		assertThat(failedMessage, is(not(messageFromConnectionInviter1)));
		final String successMessage = connectorPeer.receiveMessageFrom(connectorPeer.getConnectingHost());
		assertThat(successMessage, is(messageFromConnectionInviter1));

		final String messageFromConnectionInviter2 = "from connectionInviter2"; //$NON-NLS-1$
		assertThat(connectionInviter.sendMessageTo(messageFromConnectionInviter2, connectorHost), is(true));
		final ReceivedMessage receivedMessage = connectorPeer.receiveMessage();
		assertThat(receivedMessage.getSender(), is(connectorPeer.getConnectingHost()));
		assertThat(receivedMessage.getMessage(), is(messageFromConnectionInviter2));
	}

	/**
	 * Test method for {@link org.nognog.jmatcher.client.Connector#connect(int)}.
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
		try (final ConnectionInviter connectionInviter = new ConnectionInviter(inviterName, jmatcherHost)) {
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
	private void doTestConnectWith(final int entryKey, final String wrongJmatcherHost, int portTellerPort, boolean expected) throws IOException {
		final String connectorName = "Uva"; //$NON-NLS-1$
		final Connector requester = new Connector(connectorName, wrongJmatcherHost);
		requester.setInternalNetworkPortTellerPort(portTellerPort);
		if (expected == false) {
			try (ConnectorPeer connectionRequesterPeer = requester.connect(entryKey)) {
				assertThat(connectionRequesterPeer, is(nullValue()));
			}
		} else {
			try (ConnectorPeer connectionRequesterPeer = requester.connect(entryKey)) {
				assertThat(connectionRequesterPeer, is(not(nullValue())));
				assertThat(connectionRequesterPeer.getConnectingHost(), is(not(nullValue())));
				assertThat(connectionRequesterPeer.getSocket(), is(not(nullValue())));
			}
		}

	}

}
