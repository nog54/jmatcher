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
import org.nognog.jmatcher.client.Connector.ConnectorPeer;

/**
 * @author goshi 2016/01/20
 */
@Ignore
@SuppressWarnings({ "boxing", "javadoc" })
public class ProductEnvironmentTest {

	@Test
	public void testMakeConnection() throws IOException, InterruptedException {
		final String jmatcherHost = "nog-jserver1.servehttp.com"; //$NON-NLS-1$
		final String inviterName = "Darjeeling"; //$NON-NLS-1$
		final String connectorName = "EarlGrey"; //$NON-NLS-1$
		try (final ConnectionInviterPeer inviter = new ConnectionInviterPeer(inviterName, jmatcherHost)) {
			final Integer entryKey = inviter.startInvitation();
			System.out.println("Product Environment test : invite with " + entryKey); //$NON-NLS-1$
			final Connector connector = new Connector(connectorName, jmatcherHost);
			try (ConnectorPeer peer = connector.connect(entryKey)) {
				assertThat(peer, is(not(nullValue())));
				assertThat(inviter.getConnectingHosts().size(), is(1));
				assertThat(((Host) inviter.getConnectingHosts().toArray()[0]).getName(), is(connectorName));
				assertThat(peer.getConnectingHost().getName(), is(inviterName));
			}
			// wait for connectionInviter to handle cancel-request
			Thread.sleep(250);
			assertThat(inviter.getConnectingHosts().size(), is(0));

			try (ConnectorPeer peer = connector.connect(entryKey)) {
				assertThat(peer, is(not(nullValue())));
				assertThat(inviter.getConnectingHosts().size(), is(1));
				assertThat(((Host) inviter.getConnectingHosts().toArray()[0]).getName(), is(connectorName));
				assertThat(peer.getConnectingHost().getName(), is(inviterName));
				this.testSendMessageFromConnectorToConnectionInviter(peer, inviter);
				this.testSendMessageFromConnectionInviterToConnector(inviter, peer);
				inviter.stopInvitation();
				this.testSendMessageFromConnectorToConnectionInviter(peer, inviter);
				this.testSendMessageFromConnectionInviterToConnector(inviter, peer);
				peer.close();
				try {
					this.testSendMessageFromConnectorToConnectionInviter(peer, inviter);
				} catch (Throwable t) {
					// success
				}
				try {
					this.testSendMessageFromConnectionInviterToConnector(inviter, peer);
				} catch (Throwable t) {
					// success
				}
			}
		}
	}

	@SuppressWarnings({ "static-method" })
	private void testSendMessageFromConnectorToConnectionInviter(final ConnectorPeer connectorPeer, final ConnectionInviterPeer connectionInviter) {
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

	@SuppressWarnings({ "static-method" })
	private void testSendMessageFromConnectionInviterToConnector(final ConnectionInviterPeer connectionInviter, final ConnectorPeer connectorPeer) {
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
}
