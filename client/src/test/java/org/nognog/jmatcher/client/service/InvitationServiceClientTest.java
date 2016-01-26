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

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.nognog.jmatcher.JMatcher;
import org.nognog.jmatcher.client.JMatcherConnectionClient;
import org.nognog.jmatcher.client.JMatcherEntryClient;
import org.nognog.jmatcher.server.JMatcherDaemon;

/**
 * @author goshi 2016/01/26
 */
@SuppressWarnings({ "boxing" })
public class InvitationServiceClientTest {

	/**
	 * Test method for
	 * {@link org.nognog.jmatcher.client.service.InvitationServiceClient#connect(int, org.nognog.jmatcher.client.service.EndListener)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testConnect() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try (final JMatcherEntryClient entryClient = new JMatcherEntryClient("Colombia", "localhost")) { //$NON-NLS-1$ //$NON-NLS-2$
			final int portTellerPort = JMatcher.PORT - 1;
			entryClient.setPortTellerPort(portTellerPort);
			final Integer key = entryClient.startInvitation();
			this.doConnectTest(key, portTellerPort);
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	/**
	 * @param key
	 * @throws InterruptedException
	 */
	private void doConnectTest(Integer key, int portTellerPort) throws InterruptedException {
		this.doConnectTestWith(key, portTellerPort, "Kenya", "localhost", true); //$NON-NLS-1$ //$NON-NLS-2$
		this.doConnectTestWith(key, portTellerPort, "Guatemala", "wrongHost", false); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void doConnectTestWith(Integer key, int portTellerPort, String name, String server, boolean expected) throws InterruptedException {
		try (JMatcherConnectionClient jmatcherConnectionClient = new JMatcherConnectionClient(name, server)) {
			jmatcherConnectionClient.setInternalNetworkPortTellerPort(portTellerPort);
			try (final InvitationServiceClient client = new InvitationServiceClient(jmatcherConnectionClient)) {
				final AtomicBoolean isSuccess = new AtomicBoolean();
				final EndListener<Void> listener = new EndListener<Void>() {
					@Override
					public void success(Void result) {
						isSuccess.set(true);
						synchronized (InvitationServiceClientTest.this) {
							InvitationServiceClientTest.this.notifyAll();
						}
					}

					@Override
					public void failure(Exception e) {
						isSuccess.set(false);
						synchronized (InvitationServiceClientTest.this) {
							InvitationServiceClientTest.this.notifyAll();
						}
					}
				};
				client.connect(key, listener);
				synchronized (this) {
					this.wait();
				}
				assertThat(isSuccess.get(), is(expected));
			}
		}
	}

}
