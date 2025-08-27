#!/bin/bash

# 1. 기본 클린 실행 (build 디렉토리 및 하위 .cxx 포함하여 삭제)
./gradlew clean

# 2. Gradle 데몬 중지
./gradlew --stop

# 3. 프로젝트 레벨 .gradle 캐시 삭제
rm -rf .gradle

rm -f logcat.log

# 4. 남아있을 수 있는 모든 .cxx 디렉토리 명시적 삭제 (더 확실하게)
find . -name ".cxx" -type d -exec rm -rf {} +

# 5. 모든 빌드 캐시 삭제
# rm -rf ~/.gradle/caches/
