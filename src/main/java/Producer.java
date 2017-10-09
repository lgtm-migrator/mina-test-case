/*
 *  Based on Mina Chat Example DG
 *  
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.filter.logging.MdcInjectionFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import quickfix.field.Headline;
import quickfix.fix44.News;
import quickfix.mina.message.FIXProtocolCodecFactory;

/**
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class Producer {
	public static final int DEFAULT_PORT = 1234;
	private final NioSocketAcceptor acceptor = new NioSocketAcceptor();

	public Producer() {
		MdcInjectionFilter mdcInjectionFilter = new MdcInjectionFilter();
		ProtocolCodecFilter fixCodecFilter = new ProtocolCodecFilter(new FIXProtocolCodecFactory());

		DefaultIoFilterChainBuilder chain = acceptor.getFilterChain();
		chain.addLast("mdc", mdcInjectionFilter);
		chain.addLast("codec", fixCodecFilter);
		chain.addLast("logger", new LoggingFilter());

		ProducerProtocolHandler writer = new Producer.ProducerProtocolHandler();

		acceptor.setHandler(writer);
	}
	
	public static void main(String[] args) throws Exception {
		Producer producer = new Producer();
		producer.bind(new InetSocketAddress(DEFAULT_PORT));
		System.out.println("Listening on port " + DEFAULT_PORT);
	}

	private void bind(InetSocketAddress inetSocketAddress) throws IOException {
		this.acceptor.bind(inetSocketAddress);
	}

	static class ProducerProtocolHandler extends IoHandlerAdapter {
		private final Set<IoSession> sessions = Collections.synchronizedSet(new HashSet<IoSession>());
		private final ExecutorService exec = Executors.newSingleThreadExecutor();

		@Override
		public void exceptionCaught(IoSession session, Throwable cause) {
			System.out.println("Unexpected exception." + cause);
			// Close connection when unexpected exception is caught.
			session.closeNow();
		}

		@Override
		public void messageReceived(IoSession session, Object message) {
			System.out.println("received: " + message);
			sessions.add(session);
			// session.suspendWrite();
			Runnable r = new Runnable() {

				long lastScheduledWriteBytes = 0;
				@Override
				public void run() {
					// session.suspendWrite();
					for (int i = 0; i < 10000000; ++i) {
						System.out.println("scheduled write messages " + session.getScheduledWriteMessages());
						long scheduledWriteBytes = session.getScheduledWriteBytes();
						System.out.println("scheduled write bytes " + scheduledWriteBytes);
						System.out.println("scheduled write request queue size " + session.getWriteRequestQueue().size());
						System.out.println("in writer : writing " + i);
						if (scheduledWriteBytes < lastScheduledWriteBytes) {
							System.out.println("scheduled write bytes " + scheduledWriteBytes + 
									" less than last scheduled write bytes " + lastScheduledWriteBytes);
							//break;
						}
						lastScheduledWriteBytes = scheduledWriteBytes;
						News news = new News();
						news.set(new Headline("Headline : " + Integer.toString(i)));
						//try {
							//session.write(news).await(10);
							session.write(news);
//						} catch (InterruptedException e) {
//							e.printStackTrace();
//						}
					}
				}
			};
			exec.submit(r);
		}

		@Override
		public void sessionClosed(IoSession session) throws Exception {
			sessions.remove(session);
		}

	}

}
