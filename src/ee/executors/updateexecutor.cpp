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

#include <cassert>
#include "boost/scoped_ptr.hpp"
#include "updateexecutor.h"
#include "common/debuglog.h"
#include "common/common.h"
#include "common/ValueFactory.hpp"
#include "common/ValuePeeker.hpp"
#include "common/types.h"
#include "common/tabletuple.h"
#include "common/FatalException.hpp"
#include "plannodes/updatenode.h"
#include "plannodes/projectionnode.h"
#include "storage/table.h"
#include "storage/tablefactory.h"
#include "indexes/tableindex.h"
#include "storage/tableiterator.h"
#include "storage/tableutil.h"
#include "storage/temptable.h"
#include "storage/persistenttable.h"

#ifdef ARIES_NIRMESH
#include "logging/Logrecord.h"
#endif



using namespace std;
using namespace voltdb;

bool UpdateExecutor::p_init(AbstractPlanNode* abstract_node,
                            TempTableLimits* limits)
{
    VOLT_TRACE("init Update Executor");

    m_node = dynamic_cast<UpdatePlanNode*>(abstract_node);
    assert(m_node);
    assert(m_node->getTargetTable());
    assert(m_node->getInputTables().size() == 1);
    m_inputTable = dynamic_cast<TempTable*>(m_node->getInputTables()[0]); //input table should be temptable
    assert(m_inputTable);
    m_targetTable = dynamic_cast<PersistentTable*>(m_node->getTargetTable()); //target table should be persistenttable
    assert(m_targetTable);
    assert(m_node->getTargetTable());

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

    // record if a full index update is needed, or if these checks can be skipped
    m_updatesIndexes = m_node->doesUpdateIndexes();

    AbstractPlanNode *child = m_node->getChildren()[0];
    ProjectionPlanNode *proj_node = NULL;
    if (NULL == child) {
        VOLT_ERROR("Attempted to initialize update executor with NULL child");
        return false;
    }

    PlanNodeType pnt = child->getPlanNodeType();
    if (pnt == PLAN_NODE_TYPE_PROJECTION) {
        proj_node = dynamic_cast<ProjectionPlanNode*>(child);
    } else if (pnt == PLAN_NODE_TYPE_SEQSCAN ||
            pnt == PLAN_NODE_TYPE_INDEXSCAN) {
        proj_node = dynamic_cast<ProjectionPlanNode*>(child->getInlinePlanNode(PLAN_NODE_TYPE_PROJECTION));
        assert(NULL != proj_node);
    }

    vector<string> output_column_names = proj_node->getOutputColumnNames();
    vector<string> targettable_column_names = m_targetTable->getColumnNames();

    /*
     * The first output column is the tuple address expression and it isn't part of our output so we skip
     * it when generating the map from input columns to the target table columns.
     */
    for (int ii = 1; ii < output_column_names.size(); ii++) {
        for (int jj=0; jj < targettable_column_names.size(); ++jj) {
            if (targettable_column_names[jj].compare(output_column_names[ii]) == 0) {
                m_inputTargetMap.push_back(pair<int,int>(ii, jj));
                break;
            }
        }
    }

    assert(m_inputTargetMap.size() == (output_column_names.size() - 1));
    m_inputTargetMapSize = (int)m_inputTargetMap.size();
    m_inputTuple = TableTuple(m_inputTable->schema());
    m_targetTuple = TableTuple(m_targetTable->schema());

    m_partitionColumn = m_targetTable->partitionColumn();
    m_partitionColumnIsString = false;
    if (m_partitionColumn != -1) {
        if (m_targetTable->schema()->columnType(m_partitionColumn) == VALUE_TYPE_VARCHAR) {
            m_partitionColumnIsString = true;
        }
    }

    return true;
}

bool UpdateExecutor::p_execute(const NValueArray &params) {
    assert(m_inputTable);
    assert(m_targetTable);

    VOLT_TRACE("INPUT TABLE: %s\n", m_inputTable->debug().c_str());
    VOLT_TRACE("TARGET TABLE - BEFORE: %s\n", m_targetTable->debug().c_str());

    assert(m_inputTuple.sizeInValues() == m_inputTable->columnCount());
    assert(m_targetTuple.sizeInValues() == m_targetTable->columnCount());
    TableIterator input_iterator = m_inputTable->iterator();
    while (input_iterator.next(m_inputTuple)) {
        //
        // OPTIMIZATION: Single-Sited Query Plans
        // If our beloved UpdatePlanNode is apart of a single-site query plan,
        // then the first column in the input table will be the address of a
        // tuple on the target table that we will want to update. This saves us
        // the trouble of having to do an index lookup
        //
        void *target_address = m_inputTuple.getNValue(0).castAsAddress();
        m_targetTuple.move(target_address);

        // Loop through INPUT_COL_IDX->TARGET_COL_IDX mapping and only update
        // the values that we need to. The key thing to note here is that we
        // grab a temp tuple that is a copy of the target tuple (i.e., the tuple
        // we want to update). This insures that if the input tuple is somehow
        // bringing garbage with it, we're only going to copy what we really
        // need to into the target tuple.
        //
        TableTuple &tempTuple = m_targetTable->getTempTupleInlined(m_targetTuple);
        for (int map_ctr = 0; map_ctr < m_inputTargetMapSize; map_ctr++) {
            tempTuple.setNValue(m_inputTargetMap[map_ctr].second,
                                m_inputTuple.getNValue(m_inputTargetMap[map_ctr].first));
        }

        // XXX: use the magic of the map above to compress on the before and after images
        // written out. In fact the above might make it possible to do this straightaway.
        // OR: the m_inputTuple being passed in might itself be enough to be written out
        // reading it back could be a big problem though.


        // if there is a partition column for the target table
        if (m_partitionColumn != -1) {
            // check for partition problems
            // get the value for the partition column
            NValue value = tempTuple.getNValue(m_partitionColumn);
            bool isLocal = m_engine->isLocalSite(value);

            // if it doesn't map to this site
            if (!isLocal) {
                VOLT_ERROR("Mispartitioned tuple in single-partition plan for"
                           " table '%s'", m_targetTable->name().c_str());
                return false;
            }
        }

#ifdef ARIES_NIRMESH
        // add persistency check:
        PersistentTable* table = dynamic_cast<PersistentTable*>(m_targetTable);

        // only log if we are writing to a persistent table.
        if (table != NULL) {
        	//  before image -- target is old val with no updates
        	// XXX: what about uninlined fields?
        	// should we not be doing
        	// m_targetTable->getTempTupleInlined(m_targetTuple); instead?
        	TableTuple *beforeImage = &m_targetTuple;

        	// after image -- temp is NEW, created using target and input
        	TableTuple *afterImage =  &tempTuple;

        	TableTuple *keyTuple = NULL;
        	char *keydata = NULL;
            std::vector<int32_t> modifiedCols;

            int32_t numCols = -1;


        	// See if we can do better by using an index instead
        	TableIndex *index = table->primaryKeyIndex();

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

			// Set the modified column list
			numCols = m_inputTargetMapSize;

			modifiedCols.resize(m_inputTargetMapSize, -1);

			for (int map_ctr = 0; map_ctr < m_inputTargetMapSize; map_ctr++) {
				// can't use column-id directly, otherwise we would go over vector bounds
				int pos = m_inputTargetMap[map_ctr].first - 1;

				modifiedCols.at(pos)
						= m_inputTargetMap[map_ctr].second;
			}

			// Next, let the input tuple be the diff after image
			afterImage = &m_inputTuple;

            LogRecord *logrecord = new LogRecord(computeTimeStamp(),
            		LogRecord::T_UPDATE,	// this is an update record
            		LogRecord::T_FORWARD,	// the system is running normally
            		-1, // XXX: prevLSN must be fetched from table!
            	    ExecutorContext::getExecutorContext()->currentTxnId(), // xid
            	    m_engine->getSiteId(),	// which execution site
            		m_targetTable->name(), 	// the table affected
            		keyTuple,				// primary key
            		numCols,
            		(numCols > 0) ? &modifiedCols : NULL,
            		beforeImage,
            		afterImage
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
        }
#endif

        if (!m_targetTable->updateTuple(tempTuple, m_targetTuple,
                                        m_updatesIndexes)) {
            VOLT_INFO("Failed to update tuple from table '%s'",
                      m_targetTable->name().c_str());
            return false;
        }
    }

    TableTuple& count_tuple = m_node->getOutputTable()->tempTuple();
    count_tuple.setNValue(0, ValueFactory::getBigIntValue(m_inputTable->activeTupleCount()));
    // try to put the tuple into the output table
    if (!m_node->getOutputTable()->insertTuple(count_tuple)) {
        VOLT_ERROR("Failed to insert tuple count (%ld) into"
                   " output table '%s'",
                   static_cast<long int>(m_inputTable->activeTupleCount()),
                   m_node->getOutputTable()->name().c_str());
        return false;
    }

    VOLT_TRACE("TARGET TABLE - AFTER: %s\n", m_targetTable->debug().c_str());
    // TODO lets output result table here, not in result executor. same thing in
    // delete/insert

    // add to the planfragments count of modified tuples
    m_engine->m_tuplesModified += m_inputTable->activeTupleCount();

    return true;
}
