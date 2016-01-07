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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.nognog.jmatcher.tcp.request.PlainTCPRequest;
import org.nognog.jmatcher.tcp.request.TCPRequest;
import org.nognog.jmatcher.tcp.response.CheckConnectionResponse;
import org.nognog.jmatcher.tcp.response.PlainTCPResponse;
import org.nognog.jmatcher.tcp.response.PreEntryResponse;
import org.nognog.jmatcher.tcp.response.TCPResponse;
import org.nognog.jmatcher.udp.request.ConnectionRequest;
import org.nognog.jmatcher.udp.request.EnableEntryRequest;
import org.nognog.jmatcher.udp.request.UDPRequest;
import org.nognog.jmatcher.udp.request.UDPRequestSerializer;
import org.nognog.jmatcher.udp.response.ConnectionResponse;
import org.nognog.jmatcher.udp.response.UDPResponse;
import org.nognog.jmatcher.udp.response.UDPResponseSerializer;

/**
 * @author goshi 2015/12/25
 */
@SuppressWarnings({ "static-method", "nls", "boxing" })
public class MakeConnectionTest {

	static final String stringToRequestingHost = "public static";
	static final String stringToEntryHost = "void main"; // 適当

	private static final int soTimeout = TCPClientRequestHandler.WAIT_TIME_FOR_UDP_ENTRY; // 適当な時間

	/**
	 * @throws Exception
	 */
	@Test
	public final void testConnectRequest() throws Exception {
		final JMatcherDaemon daemon = new JMatcherDaemon();
		daemon.init(null);
		daemon.start();
		try {
			this.doConnectTest(daemon);
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
	 * @throws UnknownHostException
	 */
	private void doConnectTest(JMatcherDaemon daemon) throws Exception {
		try (final Socket entryHostTCPSocket = new Socket("localhost", JMatcher.PORT);
				final ObjectOutputStream oos = new ObjectOutputStream(entryHostTCPSocket.getOutputStream());
				final ObjectInputStream ois = new ObjectInputStream(entryHostTCPSocket.getInputStream());) {
			entryHostTCPSocket.setSoTimeout(soTimeout);
			final PreEntryResponse preEntryResponse = (PreEntryResponse) this.sendTCPRequestAndGetResponse(oos, ois, PlainTCPRequest.ENTRY);
			final int key = preEntryResponse.getKeyNumber().intValue();
			try (DatagramSocket entryHostUDPSocket = new DatagramSocket()) {
				entryHostUDPSocket.setSoTimeout(soTimeout);
				this.sendUDPRequest(entryHostUDPSocket, new EnableEntryRequest(key), new InetSocketAddress("localhost", JMatcher.PORT));
				assertThat((TCPResponse) ois.readObject() == PlainTCPResponse.COMPLETE_ENTRY, is(true));

				final Set<Thread> failedThreads = new HashSet<>();
				final int numberOfConnectionRequestHosts = 2;
				final Thread[] connectionRequestThreads = new Thread[numberOfConnectionRequestHosts];
				for (int i = 0; i < numberOfConnectionRequestHosts; i++) {
					connectionRequestThreads[i] = this.createConnectionRequestHostThread(key, failedThreads);
					connectionRequestThreads[i].start();
				}

				final Thread entryHostThread = this.createEntryHostThread(oos, ois, entryHostUDPSocket, failedThreads, numberOfConnectionRequestHosts);
				entryHostThread.start();
				this.waitForThread(connectionRequestThreads);
				this.waitForThread(entryHostThread);
				assertThat(failedThreads.size(), is(0));
			}
		}
	}

	private Thread createConnectionRequestHostThread(final int key, final Set<Thread> failedThreads) {
		return new Thread(new Runnable() {
			@Override
			public void run() {
				try (DatagramSocket socket = new DatagramSocket()) {
					socket.setSoTimeout(soTimeout);
					sendUDPRequest(socket, new ConnectionRequest(key), new InetSocketAddress("localhost", JMatcher.PORT));
					ConnectionResponse response = (ConnectionResponse) receiveUDPResponse(socket);
					final Host connectionTargetHost = response.getHost();
					assertThat(connectionTargetHost, is(not(nullValue())));
					sendUDPPacket(socket, stringToEntryHost, new InetSocketAddress(connectionTargetHost.getAddress(), connectionTargetHost.getPort()));
					String receivedMessage = receiveUDPMessage(socket);
					assertThat(receivedMessage, is(stringToRequestingHost));
				} catch (Exception | AssertionError e) {
					e.printStackTrace();
					failedThreads.add(Thread.currentThread());
				}
			}
		});
	}

	private Thread createEntryHostThread(final ObjectOutputStream oos, final ObjectInputStream ois, final DatagramSocket entryHostUDPSocket, final Set<Thread> failedThreads,
			final int numberOfConnectionRequestHosts) {
		return new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1000);
					final CheckConnectionResponse checkConnectionResponse = (CheckConnectionResponse) sendTCPRequestAndGetResponse(oos, ois, PlainTCPRequest.CHECK_CONNECTION_REQUEST);
					final Host[] requestingHosts = checkConnectionResponse.getRequestingHosts();
					assertThat(requestingHosts.length, is(numberOfConnectionRequestHosts));
					for (Host requestingHost : requestingHosts) {
						sendUDPPacket(entryHostUDPSocket, stringToRequestingHost, new InetSocketAddress(requestingHost.getAddress(), requestingHost.getPort()));
					}
					for (int i = 0; i < requestingHosts.length; i++) {
						String receivedMessage = receiveUDPMessage(entryHostUDPSocket);
						assertThat(receivedMessage, is(stringToEntryHost));
					}
					oos.writeObject("invalid request");
					try {
						sendTCPRequestAndGetResponse(oos, ois, PlainTCPRequest.CHECK_CONNECTION_REQUEST);
						fail();
					} catch (IOException e) {
						// success
					}
				} catch (Exception | AssertionError e) {
					e.printStackTrace();
					failedThreads.add(Thread.currentThread());
				}

			}
		});
	}

	private void waitForThread(final Thread... threads) {
		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				fail();
			}
		}
	}

	TCPResponse sendTCPRequestAndGetResponse(final Socket socket, TCPRequest request) throws IOException, ClassNotFoundException {
		final ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
		final ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
		return this.sendTCPRequestAndGetResponse(oos, ois, request);
	}

	TCPResponse sendTCPRequestAndGetResponse(ObjectOutputStream oos, ObjectInputStream ois, TCPRequest request) throws IOException, ClassNotFoundException {
		oos.writeObject(request);
		final TCPResponse response = (TCPResponse) ois.readObject();
		return response;
	}

	void sendUDPRequest(DatagramSocket socket, UDPRequest request, SocketAddress address) throws IOException {
		final String serializedRequest = UDPRequestSerializer.getInstance().serialize(request);
		this.sendUDPPacket(socket, serializedRequest, address);
	}

	void sendUDPPacket(DatagramSocket socket, String message, SocketAddress address) throws IOException {
		final byte[] buf = message.getBytes();
		final DatagramPacket packet = new DatagramPacket(buf, buf.length, address);
		socket.send(packet);
		System.out.println(socket.getLocalSocketAddress() + " -> " + address + " : " + message);
	}

	UDPResponse receiveUDPResponse(DatagramSocket socket) throws IOException {
		return UDPResponseSerializer.getInstance().deserialize(this.receiveUDPMessage(socket));
	}

	String receiveUDPMessage(DatagramSocket socket) throws IOException {
		final byte[] buf = new byte[1024];
		final DatagramPacket packet = new DatagramPacket(buf, buf.length);
		socket.receive(packet);
		final String result = new String(buf, 0, packet.getLength());
		System.out.println(socket.getLocalSocketAddress() + " <- " + packet.getSocketAddress() + " : " + result);
		return result;
	}
}
