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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.junit.Test;
import org.nognog.jmatcher.Host;
import org.nognog.jmatcher.JMatcher;
import org.nognog.jmatcher.client.ConnectionInviterPeer;
import org.nognog.jmatcher.client.UpdateEvent;
import org.nognog.jmatcher.server.JMatcherDaemon;

import mockit.Mocked;
import mockit.Verifications;

/**
 * @author goshi 2016/01/26
 */
@SuppressWarnings({ "boxing" })
public class InvitationServiceTest {

	/**
	 * Test method for
	 * {@link org.nognog.jinroh.net.service.InvitationService#start(EndListener)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testStart() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doStartTest();
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doStartTest() throws InterruptedException {
		final String name = "kilimanjaro"; //$NON-NLS-1$
		final String wrongServer = "rokalfotht"; //$NON-NLS-1$
		this.doStartTestWith(false, name, wrongServer);
		final String correctServer = "localhost"; //$NON-NLS-1$
		// expect false because the service try to use the same port as
		// jmatcherDaemon
		this.doStartTestWith(false, name, correctServer);
		try (ConnectionInviterPeer inviterPeer = new ConnectionInviterPeer(name, correctServer)) {
			inviterPeer.setPortTellerPort(JMatcher.PORT - 1);
			this.doStartTestWith(true, inviterPeer);
		}
	}

	@SuppressWarnings("resource")
	private void doStartTestWith(final boolean expected, final String name, final String server) throws InterruptedException {
		this.doStartTestWith(expected, new ConnectionInviterPeer(name, server));
	}

	private void doStartTestWith(final boolean expected, ConnectionInviterPeer inviterPeer) throws InterruptedException {
		final AtomicBoolean isSuccess = new AtomicBoolean();
		final EndListener<Integer> endListener = new EndListener<Integer>() {
			@Override
			public void success(Integer result) {
				isSuccess.set(true);
				synchronized (InvitationServiceTest.this) {
					InvitationServiceTest.this.notifyAll();
				}
			}

			@Override
			public void failure(Exception e) {
				isSuccess.set(false);
				synchronized (InvitationServiceTest.this) {
					InvitationServiceTest.this.notifyAll();
				}
			}
		};
		try (InvitationService service = new InvitationService(inviterPeer) {
			@Override
			protected void invitationTimeout() {
				// nop
			}

			@Override
			public void updateConnectingHosts(Set<Host> connectingHosts, UpdateEvent event, Host target) {
				// nop
			}
		}) {
			service.setLogger(LogManager.getLogger(InvitationServiceTest.class));
			service.start(endListener);
			synchronized (this) {
				this.wait();
			}
			assertThat(isSuccess.get(), is(expected));
			assertThat(service.isInviting(), is(expected));
		}
	}

	/**
	 * Test method for
	 * {@link org.nognog.jinroh.net.service.InvitationService#stopInvitation(EndListener)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testStopInvitation() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doStopInvitationTest();
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doStopInvitationTest() throws InterruptedException {
		@SuppressWarnings("resource")
		final ConnectionInviterPeer inviterPeer = new ConnectionInviterPeer("Kona", "localhost"); //$NON-NLS-1$ //$NON-NLS-2$
		inviterPeer.setPortTellerPort(JMatcher.PORT - 2);
		try (final InvitationService service = new InvitationService(inviterPeer) {
			@Override
			protected void invitationTimeout() {
				// nop
			}

			@Override
			public void updateConnectingHosts(Set<Host> connectingHosts, UpdateEvent event, Host target) {
				// nop
			}
		}) {
			service.setLogger(LogManager.getLogger(InvitationServiceTest.class));
			assertThat(service.isCommunicating(), is(false));
			assertThat(service.isInviting(), is(false));
			this.doStopInvitationTestWith(service, false);
			assertThat(service.getUdpSocket(), is(nullValue()));
			this.makeServiceStart(service);
			assertThat(service.isCommunicating(), is(true));
			assertThat(service.isInviting(), is(true));
			assertThat(service.getUdpSocket(), is(not(nullValue())));
			this.doStopInvitationTestWith(service, true);
			assertThat(service.isCommunicating(), is(true));
			assertThat(service.isInviting(), is(false));
			assertThat(service.getUdpSocket(), is(not(nullValue())));
		}
	}

	private void doStopInvitationTestWith(final InvitationService service, final boolean expected) {
		final AtomicBoolean success = new AtomicBoolean();
		final EndListener<Void> endListener = new EndListener<Void>() {
			@Override
			public void success(Void result) {
				success.set(true);
				synchronized (InvitationServiceTest.this) {
					InvitationServiceTest.this.notifyAll();
				}
			}

			@Override
			public void failure(Exception e) {
				success.set(false);
				synchronized (InvitationServiceTest.this) {
					InvitationServiceTest.this.notifyAll();
				}
			}
		};
		service.stopInvitation(endListener);
		synchronized (this) {
			try {
				// timeout will occur if stopCommunication method has already
				// finished at this point
				this.wait(2000);
			} catch (InterruptedException e1) {
				fail();
			}
		}
		assertThat(success.get(), is(expected));
	}

	/**
	 * Test method for
	 * {@link org.nognog.jinroh.net.service.InvitationService#stopCommunication(EndListener)}
	 * .
	 * 
	 * @throws Exception
	 */
	@Test
	public final void testStopCommunication() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doStopCommunicationTest();
		} finally {
			daemon.stop();
			daemon.destroy();
		}
	}

	private void doStopCommunicationTest() throws InterruptedException {
		@SuppressWarnings("resource")
		final ConnectionInviterPeer inviterPeer = new ConnectionInviterPeer("Mandheling", "localhost"); //$NON-NLS-1$ //$NON-NLS-2$
		inviterPeer.setPortTellerPort(JMatcher.PORT - 3);
		try (final InvitationService service = new InvitationService(inviterPeer) {
			@Override
			protected void invitationTimeout() {
				// nop
			}

			@Override
			public void updateConnectingHosts(Set<Host> connectingHosts, UpdateEvent event, Host target) {
				// nop
			}
		}) {
			service.setLogger(LogManager.getLogger(InvitationServiceTest.class));
			assertThat(service.isCommunicating(), is(false));
			assertThat(service.isInviting(), is(false));
			this.doStopCommunicationTestWith(service, false);
			assertThat(service.getUdpSocket(), is(nullValue()));

			// stop communication without stop invitation
			this.makeServiceStart(service);
			assertThat(service.isCommunicating(), is(true));
			assertThat(service.isInviting(), is(true));
			assertThat(service.getUdpSocket(), is(not(nullValue())));
			this.doStopCommunicationTestWith(service, true);
			assertThat(service.isCommunicating(), is(false));
			assertThat(service.isInviting(), is(false));
			assertThat(service.getUdpSocket(), is(nullValue()));

			// stop communication after stop invitation
			this.makeServiceStart(service);
			assertThat(service.isCommunicating(), is(true));
			assertThat(service.isInviting(), is(true));
			assertThat(service.getUdpSocket(), is(not(nullValue())));
			this.doStopInvitationTestWith(service, true);
			assertThat(service.isCommunicating(), is(true));
			assertThat(service.isInviting(), is(false));
			assertThat(service.getUdpSocket(), is(not(nullValue())));
			this.doStopCommunicationTestWith(service, true);
			assertThat(service.isCommunicating(), is(false));
			assertThat(service.isInviting(), is(false));
			assertThat(service.getUdpSocket(), is(nullValue()));
		}
	}

	private void makeServiceStart(final InvitationService service) throws InterruptedException {
		service.start(new EndListener<Integer>() {
			@Override
			public void success(Integer result) {
				synchronized (InvitationServiceTest.this) {
					InvitationServiceTest.this.notifyAll();
				}
			}

			@Override
			public void failure(Exception e) {
				synchronized (InvitationServiceTest.this) {
					InvitationServiceTest.this.notifyAll();
				}
			}
		});
		synchronized (this) {
			this.wait();
		}
	}

	private void doStopCommunicationTestWith(final InvitationService service, final boolean expected) {
		final AtomicBoolean success = new AtomicBoolean();
		final EndListener<Void> endListener = new EndListener<Void>() {
			@Override
			public void success(Void result) {
				success.set(true);
				synchronized (InvitationServiceTest.this) {
					InvitationServiceTest.this.notifyAll();
				}
			}

			@Override
			public void failure(Exception e) {
				success.set(false);
				synchronized (InvitationServiceTest.this) {
					InvitationServiceTest.this.notifyAll();
				}
			}
		};
		service.stopCommunication(endListener);
		synchronized (this) {
			try {
				// timeout will occur if stopCommunication method has already
				// finished at this point
				this.wait(2000);
			} catch (InterruptedException e1) {
				fail();
			}
		}
		assertThat(success.get(), is(expected));
	}

	/**
	 * Test method for
	 * {@link org.nognog.jinroh.net.service.InvitationService#setMaxSizeOfConnectingHosts(int)}
	 * .
	 * 
	 * @param inviterPeer
	 */
	@SuppressWarnings({ "unused", "static-method" })
	@Test
	public final void testSetMaxSizeOfConnectingHosts(@Mocked final ConnectionInviterPeer inviterPeer) {
		try (final InvitationService service = new InvitationService(inviterPeer) {
			@Override
			protected void invitationTimeout() {
				// nop
			}

			@Override
			public void updateConnectingHosts(Set<Host> connectingHosts, UpdateEvent event, Host target) {
				// nop
			}
		}) {
			final int newMaxSizeOfConnectingHosts = Integer.MAX_VALUE;
			new Verifications() {
				{
					inviterPeer.setMaxSizeOfConnectingHosts(newMaxSizeOfConnectingHosts);
					times = 0;
				}
			};
			inviterPeer.setMaxSizeOfConnectingHosts(newMaxSizeOfConnectingHosts);
			new Verifications() {
				{
					inviterPeer.setMaxSizeOfConnectingHosts(newMaxSizeOfConnectingHosts);
					times = 1;
				}
			};
		}
	}

	/**
	 * Test method for
	 * {@link org.nognog.jinroh.net.service.InvitationService#close()}.
	 * 
	 * @param inviterPeer
	 */
	@SuppressWarnings({ "unused", "static-method" })
	@Test
	public final void testClose(@Mocked final ConnectionInviterPeer inviterPeer) {
		try (final InvitationService service = new InvitationService(inviterPeer) {
			@Override
			protected void invitationTimeout() {
				// nop
			}

			@Override
			public void updateConnectingHosts(Set<Host> connectingHosts, UpdateEvent event, Host target) {
				// nop
			}

		}) {
			service.setLogger(LogManager.getLogger(InvitationServiceTest.class));
			new Verifications() {
				{
					inviterPeer.close();
					times = 0;
				}
			};
			service.close();
			new Verifications() {
				{
					inviterPeer.close();
					times = 1;
				}
			};
		}
	}
}
