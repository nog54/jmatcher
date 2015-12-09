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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;

import org.nognog.jmatcher.request.Request;
import org.nognog.jmatcher.request.RequestType;
import org.nognog.jmatcher.response.Response;

/**
 * @author goshi 2015/11/30
 */
public class JMatcherClientUtils {
	/**
	 * @param oos
	 * @param ois
	 * @return entry key number, or null is returned if failed to get entry key
	 * @throws IOException It's thrown if failed to connect to the server
	 */
	public static Integer makeEntry(ObjectOutputStream oos, ObjectInputStream ois) throws IOException {
		final Response response = execute(null, RequestType.ENTRY, oos, ois);
		if (response != null && response.completesRequest()) {
			return response.getKeyNumber();
		}
		return null;
	}

	/**
	 * @param key
	 * @param oos
	 * @param ois
	 * @return true if success
	 * @throws IOException It's thrown if failed to connect to the server
	 */
	public static boolean cancelEntry(Integer key, ObjectOutputStream oos, ObjectInputStream ois) throws IOException {
		final Response response = execute(key, RequestType.CANCEL_ENTRY, oos, ois);
		if (response != null && response.completesRequest()) {
			return true;
		}
		return false;
	}

	/**
	 * @param key
	 * @param oos
	 * @param ois
	 * @return response
	 * @throws IOException It's thrown if failed to connect to the server
	 */
	public static InetAddress findEntry(Integer key, ObjectOutputStream oos, ObjectInputStream ois) throws IOException {
		final Response response = execute(key, RequestType.FIND, oos, ois);
		if (response != null && response.completesRequest()) {
			return response.getAddress();
		}
		return null;
	}

	/**
	 * @param key
	 * @param type
	 * @param oos
	 * @param ois
	 * @return response
	 * @throws IOException It's thrown if failed to connect to the server
	 */
	public static Response execute(Integer key, RequestType type, ObjectOutputStream oos, ObjectInputStream ois) throws IOException {
		if (key == null && type != RequestType.ENTRY) {
			return null;
		}
		oos.writeObject(new Request(type, key));
		oos.flush();
		try {
			return (Response) ois.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
}
