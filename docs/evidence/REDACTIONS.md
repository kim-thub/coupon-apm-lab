# 공개 자료 경로 정리 기록

로깅 진단의 원본 `summary.json`, `all-runs.json`, `progress.json`에는 실행 호스트의 절대 경로가 들어 있다. 이 원본은 외부 원본 백업에 보존하고 공개하지 않는다. 공개용 `summary-public.json`은 숫자와 실행 식별자를 유지하면서 `runPath`를 상대 경로로 바꾼 파생본이다. 경로 치환 외에 판정 수치나 실행 결과를 변경하지 않았다.

AC07 공식 실험의 개별 `manifest.json`과 phase validation 요약도 공개용 `docs/evidence/overhead/selected/`에 원본 내용 그대로 복사했다. 원본 CSV·로그·DB 대조 상세는 외부 ZIP에 보존하며 공개 파일에는 포함하지 않는다.
