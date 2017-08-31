package org.nodel.discovery;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.net.DatagramPacket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.nodel.Threads;

/**
 * Used as a singleton to recycle memory hungry UDP packets / buffers
 */
public class UDPPacketRecycleQueue {
	
	private final static int PACKET_LENGTH = 65536;

	/**
	 * The maximum number of packets to keep in this queue to avoid large memory
	 * allocation that cannot be garbage collected.
	 * 
	 * (roughly equals number of threads that'd be using the queue)
	 */
	private final static int MAX_PACKETS = 64;

	/**
	 * Keeps track of the number of packets created (without using locks);
	 */
	private AtomicInteger _packetsCounter = new AtomicInteger();

	/**
	 * A fast lock-less queue
	 */
	private ConcurrentLinkedQueue<DatagramPacket> _queue = new ConcurrentLinkedQueue<DatagramPacket>();

	/**
	 * See 'instance()'
	 */
	private UDPPacketRecycleQueue() {
	}

	/**
	 * Returns a ready-to-use packet.
	 */
	public DatagramPacket getReadyToUsePacket() {
		// 'poll' actually removes a packet or returns null if none available
		DatagramPacket packet = _queue.poll();

		if (packet == null) {
			int count = _packetsCounter.incrementAndGet();

			// make sure we haven't created too many packets
			// (lock-less)
			if (count > MAX_PACKETS) {
				_packetsCounter.decrementAndGet();

				while (packet == null) {
					// highly unexpected situation so just
					// spin while we wait for packets to come back
					Threads.sleep(200);

					packet = _queue.poll();
				}
			} else {
				// create a new one and return it
				byte[] buffer = new byte[PACKET_LENGTH];
				packet = new DatagramPacket(buffer, PACKET_LENGTH);

				return packet;
			}
		}
		
		// reset the packet length field of a pre-created packet
		packet.setLength(PACKET_LENGTH);

		return packet;
	}

	/**
	 * Releases a packet back into the recycle queue.
	 */
	public void returnPacket(DatagramPacket packet) {
		_queue.offer(packet);
	}

	/**
	 * (singleton, thread-safe, non-blocking)
	 */
	private static class Instance {

		private static final UDPPacketRecycleQueue INSTANCE = new UDPPacketRecycleQueue();

	}

	/**
	 * Returns the singleton instance of this class.
	 */
	public static UDPPacketRecycleQueue instance() {
		return Instance.INSTANCE;
	}

} // (class)
