/* This file is part of VoltDB.
 * Copyright (C) 2008-2011 VoltDB Inc.
 *
 * This file contains original code and/or modifications of original code.
 * Any modifications made by VoltDB Inc. are licensed under the following
 * terms and conditions:
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
/* Copyright (C) 2008 by H-Store Project
 * Brown University
 * Massachusetts Institute of Technology
 * Yale University
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

#include "deleteexecutor.h"

#include "common/ValueFactory.hpp"
#include "common/debuglog.h"
#include "common/tabletuple.h"
#include "storage/table.h"
#include "storage/tablefactory.h"
#include "storage/tableiterator.h"
#include "indexes/tableindex.h"
#include "storage/tableutil.h"
#include "storage/temptable.h"
#include "storage/persistenttable.h"

#include <vector>
#include <cassert>

#ifdef ARIES_NIRMESH
#include "logging/Logrecord.h"
#endif

using namespace std;
using namespace voltdb;

bool DeleteExecutor::p_init(AbstractPlanNode *abstract_node,
                            TempTableLimits* limits)
{
    VOLT_TRACE("init Delete Executor");

    m_node = dynamic_cast<DeletePlanNode*>(abstract_node);
    assert(m_node);
    assert(m_node->getTargetTable());
    m_targetTable = dynamic_cast<PersistentTable*>(m_node->getTargetTable()); //target table should be persistenttable
    assert(m_targetTable);

    TupleSchema* schema = m_node->generateTupleSchema(false);
    int column_count = static_cast<int>(m_node->getOutputSchema().size());
    std::string* column_names = new std::string[column_count];
    for (int ctr = 0; ctr < column_count; ctr++)
    {
        column_names[ctr] = m_node->getOutputSchema()[ctr]->getColumnName();
    }
    m_node->setOutputTable(TableFactory::getTempTable(m_node->databaseId(),
                                                      "temp",
                                                      schema,
                                                      column_names,
                                                      limits));
    delete[] column_names;

    m_truncate = m_node->getTruncate();
    if (m_truncate) {
        assert(m_node->getInputTables().size() == 0);
        return true;
    }

    assert(m_node->getInputTables().size() == 1);
    m_inputTable = dynamic_cast<TempTable*>(m_node->getInputTables()[0]); //input table should be temptable
    assert(m_inputTable);

    m_inputTuple = TableTuple(m_inputTable->schema());
    m_targetTuple = TableTuple(m_targetTable->schema());

    return true;
}

bool DeleteExecutor::p_execute(const NValueArray &params) {
    assert(m_targetTable);
    int64_t modified_tuples = 0;

    if (m_truncate) {
        VOLT_TRACE("truncating table %s...", m_targetTable->name().c_str());
        // count the truncated tuples as deleted
        modified_tuples = m_targetTable->activeTupleCount();

#ifdef ARIES_NIRMESH
            // no need of persistency check, m_targetTable is
            // always persistent for deletes

            LogRecord *logrecord = new LogRecord(computeTimeStamp(),
            		LogRecord::T_TRUNCATE,	// this is a truncate record
            		LogRecord::T_FORWARD,	// the system is running normally
            		-1, 					// XXX: prevLSN must be fetched from table!
            		ExecutorContext::getExecutorContext()->currentTxnId(), // xid
            		m_engine->getSiteId(),	// which execution site
            		m_targetTable->name(), 	// the table affected
            		NULL,				// primary key irrelevant
            		-1,						// irrelevant numCols
            		NULL,					// list of modified cols irrelevant
            		NULL,			// before image irrelevant
            		NULL					// after image irrelevant
            );

            size_t logrecordLength = logrecord->getEstimatedLength();
            char *logrecordBuffer = new char[logrecordLength];

            FallbackSerializeOutput output;
            output.initializeWithPosition(logrecordBuffer, logrecordLength, 0);

            logrecord->serializeTo(output);

            const Logger *logger = LogManager::getThreadLogger(LOGGERID_MM_ARIES);
            logger->log(LOGLEVEL_INFO, output.data(), output.position());

            delete[] logrecordBuffer;
            logrecordBuffer = NULL;

            delete logrecord;
            logrecord = NULL;
#endif

        // actually delete all the tuples
        m_targetTable->deleteAllTuples(true);
    }
    else
    {
        assert(m_inputTable);
        assert(m_inputTuple.sizeInValues() == m_inputTable->columnCount());
        assert(m_targetTuple.sizeInValues() == m_targetTable->columnCount());
        TableIterator inputIterator = m_inputTable->iterator();
        while (inputIterator.next(m_inputTuple)) {
            //
            // OPTIMIZATION: Single-Sited Query Plans
            // If our beloved DeletePlanNode is apart of a single-site query plan,
            // then the first column in the input table will be the address of a
            // tuple on the target table that we will want to blow away. This saves
            // us the trouble of having to do an index lookup
            //
            void *targetAddress = m_inputTuple.getNValue(0).castAsAddress();
            m_targetTuple.move(targetAddress);

#ifdef ARIES_NIRMESH
            // no need of persistency check, m_targetTable is
            // always persistent for deletes

            //  before image -- target is tuple to be deleted.
            TableTuple *beforeImage = &m_targetTuple;

            TableTuple *keyTuple = NULL;
            char *keydata = NULL;

            // See if we use an index instead
            TableIndex *index = m_targetTable->primaryKeyIndex();

            if (index != NULL) {
            	// First construct tuple for primary key
            	keydata = new char[index->getKeySchema()->tupleLength()];
            	keyTuple = new TableTuple(keydata, index->getKeySchema());

            	for (int i = 0; i < index->getKeySchema()->columnCount(); i++) {
            		keyTuple->setNValue(i, beforeImage->getNValue(index->getColumnIndices()[i]));
            	}

            	// no before image need be recorded, just the primary key
            	beforeImage = NULL;
            }

            LogRecord *logrecord = new LogRecord(computeTimeStamp(),
            		LogRecord::T_DELETE,	// this is a delete record
            		LogRecord::T_FORWARD,	// the system is running normally
            		-1, // XXX: prevLSN must be fetched from table!
            		ExecutorContext::getExecutorContext()->currentTxnId(), // xid
            		m_engine->getSiteId(),	// which execution site
            		m_targetTable->name(), 	// the table affected
            		keyTuple,				// primary key
            		-1,						// must delete all columns
            		NULL,					// no list of modified cols
            		beforeImage,
            		NULL					// no after image
            );

            size_t logrecordLength = logrecord->getEstimatedLength();
            char *logrecordBuffer = new char[logrecordLength];

            FallbackSerializeOutput output;
            output.initializeWithPosition(logrecordBuffer, logrecordLength, 0);

            logrecord->serializeTo(output);

            const Logger *logger = LogManager::getThreadLogger(LOGGERID_MM_ARIES);
            logger->log(LOGLEVEL_INFO, output.data(), output.position());

            delete[] logrecordBuffer;
            logrecordBuffer = NULL;

            delete logrecord;
            logrecord = NULL;

            if (keydata != NULL) {
            	delete[] keydata;
            	keydata = NULL;
            }

            if (keyTuple != NULL) {
            	delete keyTuple;
            	keyTuple = NULL;
            }
#endif

            // Delete from target table
            if (!m_targetTable->deleteTuple(m_targetTuple, true)) {
                VOLT_ERROR("Failed to delete tuple from table '%s'",
                           m_targetTable->name().c_str());
                return false;
            }
        }
        modified_tuples = m_inputTable->activeTupleCount();
    }

    TableTuple& count_tuple = m_node->getOutputTable()->tempTuple();
    count_tuple.setNValue(0, ValueFactory::getBigIntValue(modified_tuples));
    // try to put the tuple into the output table
    if (!m_node->getOutputTable()->insertTuple(count_tuple)) {
        VOLT_ERROR("Failed to insert tuple count (%ld) into"
                   " output table '%s'",
                   static_cast<long int>(modified_tuples),
                   m_node->getOutputTable()->name().c_str());
        return false;
    }
    m_engine->m_tuplesModified += modified_tuples;

    return true;
}
