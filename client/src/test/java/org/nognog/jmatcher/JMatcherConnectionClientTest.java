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
import java.net.DatagramPacket;
import java.net.DatagramSocket;

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
	public final void testConnectAndCancelConnection() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doTestConnectAndCancelConnection(daemon);
		} catch (Throwable t) {
			t.printStackTrace();
			fail();
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param daemon
	 * @throws IOException
	 */
	@SuppressWarnings({ "boxing", "resource", "static-method" })
	private void doTestConnectAndCancelConnection(JMatcherDaemon daemon) throws Exception {
		final String jmatcherHost = "localhost"; //$NON-NLS-1$
		final JMatcherEntryClient entryClient = new JMatcherEntryClient(jmatcherHost);
		final Integer entryKey = entryClient.makeEntry();
		final JMatcherConnectionClient connectionClient = new JMatcherConnectionClient(jmatcherHost);

		createUpdateConnectedHostsThread(entryClient).start();
		assertThat(connectionClient.connect(entryKey), is(true));
		Thread.sleep(JMatcherEntryClient.defaultUdpSocketTimeoutMillSec);

		assertThat(entryClient.getConnectingHosts().size(), is(1));
		createUpdateConnectedHostsThread(entryClient).start();
		connectionClient.cancelConnection();
		Thread.sleep(1000);
		assertThat(entryClient.getConnectingHosts().size(), is(0));

		createUpdateConnectedHostsThread(entryClient).start();
		assertThat(connectionClient.connect(entryKey), is(true));
		assertThat(entryClient.getConnectingHosts().size(), is(1));
		Thread.sleep(JMatcherEntryClient.defaultUdpSocketTimeoutMillSec);

		final DatagramSocket entryClientSocket = entryClient.cancelEntry();
		final DatagramSocket connectionClientSocket = connectionClient.getConnectingSocket();
		assertThat(connectionClientSocket, is(not(nullValue())));
		assertThat(connectionClientSocket.isClosed(), is(false));

		final String messageFromConnectionClient = "from connectionClient"; //$NON-NLS-1$
		assertThat(connectionClient.sendMessageToConnectingHost(messageFromConnectionClient), is(true));
		final DatagramPacket receivedPacket = JMatcherClientUtil.receiveUDPPacket(entryClientSocket, 1024);
		assertThat(JMatcherClientUtil.getMessageFrom(receivedPacket), is(messageFromConnectionClient));

		final String messageFromEntryClient = "from entryClient"; //$NON-NLS-1$
		JMatcherClientUtil.sendMessage(entryClientSocket, messageFromEntryClient, receivedPacket.getSocketAddress());
		assertThat(connectionClient.receiveMessageFromConnectingHost(), is(messageFromEntryClient));

		assertThat(entryClient.getConnectingHosts().size(), is(1));
		createUpdateConnectedHostsThread(entryClient).start();
		connectionClient.cancelConnection();
		assertThat(entryClient.getConnectingHosts().size(), is(1));
		assertThat(connectionClient.sendMessageToConnectingHost(messageFromConnectionClient), is(false));
	}

	private static Thread createUpdateConnectedHostsThread(final JMatcherEntryClient entryClient) {
		return new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				try {
					entryClient.updateConnectingHosts();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}
}
