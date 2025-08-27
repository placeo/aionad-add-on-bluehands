#include "CCustomData.h"
#include "common/log/JniLogger.h"

CCustomData* CCustomData::pInstance_ = NULL;

CCustomData::CCustomData() {
  
}

CCustomData::~CCustomData() {
  destroyInstance();
}