/* This file is part of VoltDB.
 * Copyright (C) 2008-2011 VoltDB Inc.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.dtxn;

import java.util.HashSet;

import org.voltdb.ClientResponseImpl;
import org.voltdb.ExecutionSite;
import org.voltdb.TransactionIdManager;
import org.voltdb.VoltTable;
import org.voltdb.client.ClientResponse;
import org.voltdb.messaging.InitiateResponseMessage;
import org.voltdb.messaging.InitiateTaskMessage;
import org.voltdb.messaging.Mailbox;
import org.voltdb.messaging.MessagingException;
import org.voltdb.messaging.TransactionInfoBaseMessage;

public class SinglePartitionTxnState extends TransactionState {

	InitiateTaskMessage m_task = null;

	public SinglePartitionTxnState(Mailbox mbox,
			ExecutionSite site,
			TransactionInfoBaseMessage task)
	{
		super(mbox, site, task);
		assert(task instanceof InitiateTaskMessage) :
			"Creating single partition txn from invalid membership notice.";
		m_task = (InitiateTaskMessage)task;
	}

	@Override
	public boolean isSinglePartition()
	{
		return true;
	}

	// Single-partition transactions run only one place and it is always
	// the coordinator (replicas all run in parallel, not coordinated)
	@Override
	public boolean isCoordinator()
	{
		return true;
	}

	// Single-partition transactions should never block
	@Override
	public boolean isBlocked()
	{
		return false;
	}

	// Single-partition transactions better always touch persistent tables
	@Override
	public boolean hasTransactionalWork()
	{
		return true;
	}

	@Override
	public boolean doWork(boolean recovering) {
		if (recovering) {
			return doWorkRecovering();
		}
		if (!m_done) {
			m_site.beginNewTxn(this);

			InitiateResponseMessage response = m_site.processInitiateTask(this, m_task);
			if (response.shouldCommit() == false) {
				m_needsRollback = true;
			}

			try {
				/*// nirmesh
				 * Change this code here so that it doesn't return the message if there is log data, instead have
				 * it pass the log data to the logger along with the AtomicBoolean, and put into a queue in m_site
				 * 
				 * XXX: Even if there is no log data, wouldn't this lead to reads after non-durable
				 * writes returning?
				 * Chaomin: to make it right without NULL pointer error?
				 */
				if (response.hasAriesLogData()) {
					m_site.getAriesLogger().log(response.getClientResponseData().getAriesLogData(), m_task.getDurabilityFlag());

					responseToSend = response;
					m_site.getCompletedTransactionsQueue().add(this);
				} else {
					m_mbox.send(initiatorSiteId, 0, response);
				}
			} catch (MessagingException e) {
				throw new RuntimeException(e);
			}
			m_done = true;
		}
		return m_done;
	}

	private boolean doWorkRecovering() {
		if (!m_done) {
			InitiateResponseMessage response = new InitiateResponseMessage(m_task);

			// add an empty dummy response
			response.setResults(new ClientResponseImpl(
					ClientResponse.SUCCESS,
					new VoltTable[0],
					null));

			// this tells the initiator that the response is a dummy
			response.setRecovering(true);

			try {
				m_mbox.send(initiatorSiteId, 0, response);
			} catch (MessagingException e) {
				throw new RuntimeException(e);
			}

			m_done = true;
		}
		return m_done;
	}

	@Override
	public String toString() {
		return "SinglePartitionTxnState initiator: " + initiatorSiteId +
				" txnId: " + TransactionIdManager.toString(txnId);
	}

	@Override
	public void handleSiteFaults(HashSet<Integer> failedSites) {
		// nothing to be done here.
	}

	@Override
	public boolean isDurable() {
		java.util.concurrent.atomic.AtomicBoolean durableFlag = m_task.getDurabilityFlagIfItExists();
		return durableFlag == null ? true : durableFlag.get();
	}

	public InitiateTaskMessage getInitiateTaskMessage() {
		return m_task;
	}

	// nirmesh
	@Override
	public void sendResponse() {
		try {
			m_mbox.send(initiatorSiteId, 0, responseToSend);
		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
	}
}
