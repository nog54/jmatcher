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
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;
import org.nognog.jmatcher.Host;

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
		try (final JMatcherEntryClient entryClient = new JMatcherEntryClient(entryClientName, jmatcherHost)) {
			final Integer entryKey = entryClient.startInvitation();
			System.out.println("Product Environment test : invite with " + entryKey); //$NON-NLS-1$
			final JMatcherConnectionClient connectionClient = new JMatcherConnectionClient(connectionClientName, jmatcherHost);
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

	@SuppressWarnings({ "static-method" })
	private void testSendMessageFromConnectionClientToEntryClient(final JMatcherConnectionClient connectionClient, final JMatcherEntryClient entryClient) {
		final Host connectionClientHost = (Host) entryClient.getConnectingHosts().toArray()[0];
		final String messageFromConnectionClient = "from connectionClient"; //$NON-NLS-1$
		assertThat(connectionClient.sendMessage(messageFromConnectionClient), is(true));
		final String receivedMessage = entryClient.receiveMessageFrom(connectionClientHost);
		assertThat(receivedMessage, is(messageFromConnectionClient));
	}

	@SuppressWarnings({ "static-method" })
	private void testSendMessageFromEntryClientToConnectionClient(final JMatcherEntryClient entryClient, final JMatcherConnectionClient connectionClient) {
		final Host connectionClientHost = (Host) entryClient.getConnectingHosts().toArray()[0];
		final String messageFromEntryClient = "from entryClient"; //$NON-NLS-1$
		assertThat(entryClient.sendMessageTo(connectionClientHost, messageFromEntryClient), is(true));
		final String receivedMessage = connectionClient.receiveMessage();
		assertThat(receivedMessage, is(messageFromEntryClient));
	}
}
