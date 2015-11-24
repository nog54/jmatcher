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
import java.net.Socket;

import org.apache.logging.log4j.Logger;

/**
 * @author goshi 2015/10/31
 */
public class ClientRequestHandler implements Runnable {

	private Socket socket;
	private Logger logger;
	private boolean hasClosedSocket;

	/**
	 * @param socket
	 * @param logger
	 * 
	 */
	public ClientRequestHandler(Socket socket, Logger logger) {
		this.socket = socket;
		this.logger = logger;
		this.hasClosedSocket = false;
	}

	@Override
	public void run() {
		this.close();
	}

	/**
	 * close this handler
	 * 
	 * @throws IOException
	 */
	public void close() {
		if (this.hasClosedSocket) {
			return;
		}
		try {
			this.socket.close();
			this.hasClosedSocket = true;
		} catch (IOException e) {
			this.logger.error("Failed to close socket", e); //$NON-NLS-1$
		}
	}
	
	/**
	 * @return true if this has closed
	 */
	public boolean hasClosedSocket(){
		return this.hasClosedSocket;
	}

}
