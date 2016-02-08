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
import org.nognog.jmatcher.client.JMatcherConnectionRequester.JMatcherConnectionRequesterPeer;
import org.nognog.jmatcher.server.JMatcherDaemon;

/**
 * @author goshi 2015/12/29
 */
public class JMatcherConnectionRequesterTest {

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherConnectionClient#connect(int)}.
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
				this.doTestSendAndReceiveMessage(daemon, "tooLongEntryClientNameあいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやいゆえよらりるれろわをん", null, JMatcher.PORT - 1); //$NON-NLS-1$
				fail();
			} catch (IllegalArgumentException e) {
				// success
			}
			try {
				this.doTestSendAndReceiveMessage(daemon, null, "tooLongEntryClientNameあいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやいゆえよらりるれろわをん", JMatcher.PORT - 1); //$NON-NLS-1$
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
	private void doTestSendAndReceiveMessage(JMatcherDaemon daemon, String entryClientName, String connectionClientName, int portTellerPort) throws Exception {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (final JMatcherEntry entryClient = new JMatcherEntry(entryClientName, jmatcherHost)) {
			entryClient.setPortTellerPort(portTellerPort);
			final Integer entryKey = entryClient.startInvitation();
			assertThat(entryKey, is(not(nullValue())));
			final JMatcherConnectionRequester requester = new JMatcherConnectionRequester(connectionClientName, jmatcherHost);
			requester.setInternalNetworkPortTellerPort(portTellerPort);
			try (JMatcherConnectionRequesterPeer connectionRequesterPeer = requester.connect(entryKey)) {
				assertThat(connectionRequesterPeer, is(not(nullValue())));
				assertThat(entryClient.getConnectingHosts().size(), is(1));
				assertThat(((Host) entryClient.getConnectingHosts().toArray()[0]).getName(), is(connectionClientName));
				assertThat(connectionRequesterPeer.getConnectingHost().getName(), is(entryClientName));
			}

			// wait for entryClient to handle cancel-request
			Thread.sleep(250);
			assertThat(entryClient.getConnectingHosts().size(), is(0));
			try (JMatcherConnectionRequesterPeer connectionRequesterPeer = requester.connect(entryKey)) {
				assertThat(connectionRequesterPeer, is(not(nullValue())));
				assertThat(entryClient.getConnectingHosts().size(), is(1));
				assertThat(((Host) entryClient.getConnectingHosts().toArray()[0]).getName(), is(connectionClientName));
				assertThat(connectionRequesterPeer.getConnectingHost().getName(), is(entryClientName));
				this.testSendMessageFromConnectionClientToEntryClient(connectionRequesterPeer, entryClient);
				this.testSendMessageFromEntryClientToConnectionClient(entryClient, connectionRequesterPeer);
				entryClient.stopInvitation();
				this.testSendMessageFromConnectionClientToEntryClient(connectionRequesterPeer, entryClient);
				this.testSendMessageFromEntryClientToConnectionClient(entryClient, connectionRequesterPeer);
				connectionRequesterPeer.close();
				try {
					this.testSendMessageFromConnectionClientToEntryClient(connectionRequesterPeer, entryClient);
				} catch (Throwable t) {
					// success
				}
				try {
					this.testSendMessageFromEntryClientToConnectionClient(entryClient, connectionRequesterPeer);
				} catch (Throwable t) {
					// success
				}
			}
		}
	}

	@SuppressWarnings({ "boxing", "static-method" })
	private void testSendMessageFromConnectionClientToEntryClient(final JMatcherConnectionRequesterPeer connectionClient, final JMatcherEntry entryClient) {
		final Host connectionClientHost = (Host) entryClient.getConnectingHosts().toArray()[0];
		final String messageFromConnectionClient1 = "from connectionClient1"; //$NON-NLS-1$
		assertThat(connectionClient.sendMessage(messageFromConnectionClient1), is(true));
		final String failedMessage = entryClient.receiveMessageFrom(new Host(null, 0));
		assertThat(failedMessage, is(not(messageFromConnectionClient1)));
		final String successMessage = entryClient.receiveMessageFrom(connectionClientHost);
		assertThat(successMessage, is(messageFromConnectionClient1));

		final String messageFromConnectionClient2 = "from connectionClient2"; //$NON-NLS-1$
		assertThat(connectionClient.sendMessage(messageFromConnectionClient2), is(true));
		final ReceivedMessage receivedMessage = entryClient.receiveMessage();
		assertThat(receivedMessage.getSender(), is(connectionClientHost));
		assertThat(receivedMessage.getMessage(), is(messageFromConnectionClient2));
	}

	@SuppressWarnings({ "boxing", "static-method" })
	private void testSendMessageFromEntryClientToConnectionClient(final JMatcherEntry entryClient, final JMatcherConnectionRequesterPeer connectionClient) {
		final Host connectionClientHost = (Host) entryClient.getConnectingHosts().toArray()[0];
		final String messageFromEntryClient1 = "from entryClient1"; //$NON-NLS-1$
		assertThat(entryClient.sendMessageTo(messageFromEntryClient1, connectionClientHost), is(true));
		final String failedMessage = connectionClient.receiveMessageFrom(new Host(null, 0));
		assertThat(failedMessage, is(not(messageFromEntryClient1)));
		final String successMessage = connectionClient.receiveMessageFrom(connectionClient.getConnectingHost());
		assertThat(successMessage, is(messageFromEntryClient1));

		final String messageFromEntryClient2 = "from entryClient2"; //$NON-NLS-1$
		assertThat(entryClient.sendMessageTo(messageFromEntryClient2, connectionClientHost), is(true));
		final ReceivedMessage receivedMessage = connectionClient.receiveMessage();
		assertThat(receivedMessage.getSender(), is(connectionClient.getConnectingHost()));
		assertThat(receivedMessage.getMessage(), is(messageFromEntryClient2));
	}

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.JMatcherConnectionClient#connect(int)}.
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
		final String entryClientName = "Assam"; //$NON-NLS-1$
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		try (final JMatcherEntry entryClient = new JMatcherEntry(entryClientName, jmatcherHost)) {
			entryClient.setPortTellerPort(portTellerPort);
			final Integer entryKey = entryClient.startInvitation();
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
			entryClient.setMaxSizeOfConnectingHosts(0);
			this.doTestConnectWith(correctEntryKey, jmatcherHost, portTellerPort, false);
		}
	}

	@SuppressWarnings({ "static-method" })
	private void doTestConnectWith(final int entryKey, final String wrongJmatcherHost, int portTellerPort, boolean expected) throws IOException {
		final String connectionClientName = "Uva"; //$NON-NLS-1$
		final JMatcherConnectionRequester requester = new JMatcherConnectionRequester(connectionClientName, wrongJmatcherHost);
		requester.setInternalNetworkPortTellerPort(portTellerPort);
		if (expected == false) {
			try (JMatcherConnectionRequesterPeer connectionRequesterPeer = requester.connect(entryKey)) {
				assertThat(connectionRequesterPeer, is(nullValue()));
			}
		} else {
			try (JMatcherConnectionRequesterPeer connectionRequesterPeer = requester.connect(entryKey)) {
				assertThat(connectionRequesterPeer, is(not(nullValue())));
				assertThat(connectionRequesterPeer.getConnectingHost(), is(not(nullValue())));
				assertThat(connectionRequesterPeer.getSocket(), is(not(nullValue())));
			}
		}

	}

}
