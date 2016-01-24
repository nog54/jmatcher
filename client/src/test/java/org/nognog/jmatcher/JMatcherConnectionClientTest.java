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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Test;

/**
 * @author goshi 2015/12/29
 */
public class JMatcherConnectionClientTest {

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
		try (final JMatcherEntryClient entryClient = new JMatcherEntryClient(entryClientName, jmatcherHost)) {
			entryClient.setPortTellerPort(portTellerPort);
			final Integer entryKey = entryClient.startInvitation();
			assertThat(entryKey, is(not(nullValue())));
			final JMatcherConnectionClient connectionClient = new JMatcherConnectionClient(connectionClientName, jmatcherHost);
			connectionClient.setInternalNetworkPortTellerPort(portTellerPort);
			assertThat(connectionClient.connect(entryKey), is(true));
			assertThat(entryClient.getConnectingHosts().size(), is(1));
			assertThat(((Host) entryClient.getConnectingHosts().toArray()[0]).getName(), is(connectionClientName));
			assertThat(connectionClient.getConnectingHost().getName(), is(entryClientName));

			connectionClient.cancelConnection();
			Thread.sleep(250); // wait for entryClient to handle cancel-request
			assertThat(entryClient.getConnectingHosts().size(), is(0));

			assertThat(connectionClient.connect(entryKey), is(true));
			assertThat(entryClient.getConnectingHosts().size(), is(1));
			assertThat(((Host) entryClient.getConnectingHosts().toArray()[0]).getName(), is(connectionClientName));
			assertThat(connectionClient.getConnectingHost().getName(), is(entryClientName));
			this.testSendMessageFromConnectionClientToEntryClient(connectionClient, entryClient);
			this.testSendMessageFromEntryClientToConnectionClient(entryClient, connectionClient);
			entryClient.stopInvitation();
			this.testSendMessageFromConnectionClientToEntryClient(connectionClient, entryClient);
			this.testSendMessageFromEntryClientToConnectionClient(entryClient, connectionClient);
			connectionClient.cancelConnection();
			try {
				this.testSendMessageFromConnectionClientToEntryClient(connectionClient, entryClient);
			} catch (Throwable t) {
				// success
			}
			try {
				this.testSendMessageFromEntryClientToConnectionClient(entryClient, connectionClient);
			} catch (Throwable t) {
				// success
			}
		}
	}

	@SuppressWarnings({ "boxing", "static-method" })
	private void testSendMessageFromConnectionClientToEntryClient(final JMatcherConnectionClient connectionClient, final JMatcherEntryClient entryClient) {
		final Host connectionClientHost = (Host) entryClient.getConnectingHosts().toArray()[0];
		final String messageFromConnectionClient = "from connectionClient"; //$NON-NLS-1$
		assertThat(connectionClient.sendMessage(messageFromConnectionClient), is(true));
		final String receivedMessage = entryClient.receiveMessageFrom(connectionClientHost);
		assertThat(receivedMessage, is(messageFromConnectionClient));
	}

	@SuppressWarnings({ "boxing", "static-method" })
	private void testSendMessageFromEntryClientToConnectionClient(final JMatcherEntryClient entryClient, final JMatcherConnectionClient connectionClient) {
		final Host connectionClientHost = (Host) entryClient.getConnectingHosts().toArray()[0];
		final String messageFromEntryClient = "from entryClient"; //$NON-NLS-1$
		assertThat(entryClient.sendMessageTo(connectionClientHost, messageFromEntryClient), is(true));
		final String receivedMessage = connectionClient.receiveMessage();
		assertThat(receivedMessage, is(messageFromEntryClient));
	}

}
