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

package org.nognog.jmatcher.client.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Set;
import java.util.concurrent.SynchronousQueue;

import org.junit.Test;
import org.nognog.jmatcher.Host;
import org.nognog.jmatcher.JMatcher;
import org.nognog.jmatcher.client.ConnectionInviterPeer;
import org.nognog.jmatcher.client.Connector;
import org.nognog.jmatcher.client.Peer;
import org.nognog.jmatcher.client.UpdateEvent;
import org.nognog.jmatcher.server.JMatcherDaemon;

/**
 * @author goshi 2016/01/26
 */
@SuppressWarnings({ "boxing" })
public class InvitationServiceClientTest {

	final long timeout = 5000;

	/**
	 * Test method for
	 * {@link org.nognog.jinroh.net.service.InvitationServiceClient#connect(int, EndListener)}
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
			this.doConnectTest();
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doConnectTest() throws InterruptedException {
		try (final ConnectionInviterPeer inviter = new ConnectionInviterPeer("Colombia", "localhost")) { //$NON-NLS-1$ //$NON-NLS-2$
			// inviter.setLogger(LogManager.getLogger(InvitationServiceClientTest.class));
			final int portTellerPort = JMatcher.PORT - 1;
			inviter.setPortTellerPort(portTellerPort);
			try (final InvitationService service = new InvitationService(inviter) {
				@Override
				protected void invitationTimeout() {
					// nop
				}

				@Override
				public void updateConnectingHosts(Set<Host> connectingHosts, UpdateEvent event, Host target) {
					// nop
				}
			}) {
				final SynchronousQueue<Integer> queue = new SynchronousQueue<>();
				final EndListener<Integer> endListener = new EndListener<Integer>() {
					@Override
					public void success(Integer result) {
						try {
							queue.put(result);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					@Override
					public void failure(Exception e1) {
						try {
							queue.put(Integer.MIN_VALUE);
						} catch (InterruptedException e2) {
							e2.printStackTrace();
						}
					}
				};
				service.start(endListener);
				final Integer key = queue.take();
				if (key == Integer.MIN_VALUE) {
					fail();
				}
				this.doActualConnectTest(key, portTellerPort);
			}
		}
	}

	/**
	 * @param key
	 * @throws InterruptedException
	 */
	private void doActualConnectTest(Integer key, int portTellerPort) throws InterruptedException {
			this.doActualConnectTestWith(key, portTellerPort, "Guatemala", "localhost", true); //$NON-NLS-1$ //$NON-NLS-2$
			this.doActualConnectTestWith(key, portTellerPort, "Kenya", "localhost", true); //$NON-NLS-1$ //$NON-NLS-2$
			this.doActualConnectTestWith(key, portTellerPort, "Guatemala", "wrongHost", false); //$NON-NLS-1$ //$NON-NLS-2$
			this.doActualConnectTestWith(key, portTellerPort, "Kenya", "wrongHost", false); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@SuppressWarnings("static-method")
	private void doActualConnectTestWith(Integer key, int portTellerPort, String name, String server, boolean expected)
			throws InterruptedException {
		final Connector connectionRequester = new Connector(name, server);
		connectionRequester.setInternalNetworkPortTellerPort(portTellerPort);
		final InvitationServiceClient client = new InvitationServiceClient(connectionRequester, 256);
		// client.setLogger(LogManager.getLogger(InvitationServiceClientTest.class));
		final SynchronousQueue<Boolean> queue = new SynchronousQueue<>();
		final EndListener<Peer> listener = new EndListener<Peer>() {
			@Override
			public void success(Peer peer) {
				try {
					queue.put(Boolean.TRUE);
					peer.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			public void failure(Exception e) {
				try {
					queue.put(Boolean.FALSE);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		};
		client.connect(key, listener);
		assertThat(queue.take(), is(expected));
	}
}
