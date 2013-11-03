/*
 * LSN.cpp
 *
 *      Author: nirmeshmalviya
 */

#include "LSN.h"

LSN::LSN() {
	logid = 0;
}

LSN::LSN(uint64_t _logid)
	: logid(_logid) {
}

LSN::~LSN() {
}
