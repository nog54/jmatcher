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

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

import org.nognog.jmatcher.udp.request.UDPRequest;
import org.nognog.jmatcher.udp.request.UDPRequestSerializer;
import org.nognog.jmatcher.udp.response.UDPResponse;
import org.nognog.jmatcher.udp.response.UDPResponseSerializer;

/**
 * @author goshi 2015/12/27
 */
@SuppressWarnings("javadoc")
public class JMatcherClientUtil {

	static void sendUDPRequest(DatagramSocket datagramSocket, UDPRequest request, SocketAddress address) throws IOException {
		final String serializedRequest = UDPRequestSerializer.getInstance().serialize(request);
		sendUDPPacket(datagramSocket, serializedRequest, address);
	}

	static void sendJMatcherClientMessage(DatagramSocket datagramSocket, JMatcherClientMessage message, Host host) throws IOException {
		sendUDPPacket(datagramSocket, message.toString(), new InetSocketAddress(host.getAddress(), host.getPort()));
	}

	public static void sendMessage(DatagramSocket datagramSocket, String message, Host host) throws IOException {
		sendUDPPacket(datagramSocket, message, new InetSocketAddress(host.getAddress(), host.getPort()));
	}

	public static void sendMessage(DatagramSocket datagramSocket, String message, SocketAddress address) throws IOException {
		sendUDPPacket(datagramSocket, message, address);
	}

	private static void sendUDPPacket(DatagramSocket datagramSocket, String message, SocketAddress address) throws IOException {
		final byte[] buf = message.getBytes();
		final DatagramPacket packet = new DatagramPacket(buf, buf.length, address);
		datagramSocket.send(packet);
	}

	static UDPResponse receiveUDPResponse(DatagramSocket socket, int buffSize) throws IOException {
		return UDPResponseSerializer.getInstance().deserialize(receiveMessage(socket, buffSize));
	}

	public static String receiveMessage(DatagramSocket socket, int buffSize) throws IOException {
		final DatagramPacket packet = receiveUDPPacket(socket, buffSize);
		return getMessageFrom(packet);
	}

	public static DatagramPacket receiveUDPPacket(DatagramSocket socket, int buffSize) throws SocketTimeoutException, IOException {
		final byte[] buf = new byte[buffSize];
		final DatagramPacket packet = new DatagramPacket(buf, buf.length);
		socket.receive(packet);
		return packet;
	}

	static String getMessageFrom(DatagramPacket packet) {
		return new String(packet.getData(), 0, packet.getLength());
	}

	static JMatcherClientMessage getJMatcherMessageFrom(DatagramPacket packet) {
		try {
			return JMatcherClientMessage.valueOf(getMessageFrom(packet));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static boolean packetCameFrom(Host host, DatagramPacket packet) {
		return packetCameFrom(new InetSocketAddress(host.getAddress(), host.getPort()), packet);
	}

	public static boolean packetCameFrom(InetSocketAddress hostAddress, DatagramPacket packet) {
		return hostAddress.getAddress().equals(packet.getAddress()) && hostAddress.getPort() == packet.getPort();
	}

	public static void close(Closeable closeable) {
		if (closeable == null) {
			return;
		}
		try {
			closeable.close();
		} catch (IOException e) {
			// ignore
		}
	}
}
