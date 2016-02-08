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

package org.nognog.jmatcher.client;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;
import org.nognog.jmatcher.Host;
import org.nognog.jmatcher.client.JMatcherConnectionRequester.JMatcherConnectionRequesterPeer;

/**
 * @author goshi 2016/01/20
 */
@Ignore
@SuppressWarnings({ "boxing", "javadoc" })
public class ProductEnvironmentTest {

	@Test
	public void testMakeConnection() throws IOException, InterruptedException {
		final String jmatcherHost = "nog-jserver1.servehttp.com"; //$NON-NLS-1$
		final String entryClientName = "Darjeeling"; //$NON-NLS-1$
		final String connectionClientName = "EarlGrey"; //$NON-NLS-1$
		try (final JMatcherEntry entryClient = new JMatcherEntry(entryClientName, jmatcherHost)) {
			final Integer entryKey = entryClient.startInvitation();
			System.out.println("Product Environment test : invite with " + entryKey); //$NON-NLS-1$
			final JMatcherConnectionRequester connectionClient = new JMatcherConnectionRequester(connectionClientName, jmatcherHost);
			try (JMatcherConnectionRequesterPeer peer = connectionClient.connect(entryKey)) {
				assertThat(peer, is(not(nullValue())));
				assertThat(entryClient.getConnectingHosts().size(), is(1));
				assertThat(((Host) entryClient.getConnectingHosts().toArray()[0]).getName(), is(connectionClientName));
				assertThat(peer.getConnectingHost().getName(), is(entryClientName));
			}
			// wait for entryClient to handle cancel-request
			Thread.sleep(250);
			assertThat(entryClient.getConnectingHosts().size(), is(0));

			try (JMatcherConnectionRequesterPeer peer = connectionClient.connect(entryKey)) {
				assertThat(peer, is(not(nullValue())));
				assertThat(entryClient.getConnectingHosts().size(), is(1));
				assertThat(((Host) entryClient.getConnectingHosts().toArray()[0]).getName(), is(connectionClientName));
				assertThat(peer.getConnectingHost().getName(), is(entryClientName));
				this.testSendMessageFromConnectionClientToEntryClient(peer, entryClient);
				this.testSendMessageFromEntryClientToConnectionClient(entryClient, peer);
				entryClient.stopInvitation();
				this.testSendMessageFromConnectionClientToEntryClient(peer, entryClient);
				this.testSendMessageFromEntryClientToConnectionClient(entryClient, peer);
				peer.close();
				try {
					this.testSendMessageFromConnectionClientToEntryClient(peer, entryClient);
				} catch (Throwable t) {
					// success
				}
				try {
					this.testSendMessageFromEntryClientToConnectionClient(entryClient, peer);
				} catch (Throwable t) {
					// success
				}
			}
		}
	}

	@SuppressWarnings({ "static-method" })
	private void testSendMessageFromConnectionClientToEntryClient(JMatcherConnectionRequesterPeer connectionRequesterPeer, JMatcherEntry entryClient) {
		final Host connectionClientHost = (Host) entryClient.getConnectingHosts().toArray()[0];
		final String messageFromConnectionClient = "from connectionClient"; //$NON-NLS-1$
		assertThat(connectionRequesterPeer.sendMessage(messageFromConnectionClient), is(true));
		final String receivedMessage = entryClient.receiveMessageFrom(connectionClientHost);
		assertThat(receivedMessage, is(messageFromConnectionClient));
	}

	@SuppressWarnings({ "static-method" })
	private void testSendMessageFromEntryClientToConnectionClient(JMatcherEntry entryClient, JMatcherConnectionRequesterPeer connectionRequesterPeer) {
		final Host connectionClientHost = (Host) entryClient.getConnectingHosts().toArray()[0];
		final String messageFromEntryClient = "from entryClient"; //$NON-NLS-1$
		assertThat(entryClient.sendMessageTo(messageFromEntryClient, connectionClientHost), is(true));
		final ReceivedMessage receivedMessage = connectionRequesterPeer.receiveMessage();
		assertThat(receivedMessage.getSender(), is(connectionRequesterPeer.getConnectingHost()));
		assertThat(receivedMessage.getMessage(), is(messageFromEntryClient));
	}
}
