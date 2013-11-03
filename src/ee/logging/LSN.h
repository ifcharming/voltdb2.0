/*
 * LSN.h
 *
 *      Author: nirmeshmalviya
 */

#ifndef LSN_H_
#define LSN_H_

class LSN {
	uint64_t logid;		// the actual number

public:
	LSN();
	LSN(uint64_t _logid);

	virtual ~LSN();
};

#endif /* LSN_H_ */
