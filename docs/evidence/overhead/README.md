# AC07 실행별 증거 디렉터리

`make test-overhead`를 실제 실행하면 각 `<runId>/`에 DB 이름·실행 조건을 가진 `manifest.json`, Locust warm-up/측정 상세 CSV, 시딩 할당과 DB 커밋 교차검증, 유효성 결과가 저장됩니다. 최종 `comparison.csv`는 유효한 정식 실행 3+3회를 원본에서 재계산한 결과입니다. 측정하지 않은 값을 미리 생성하지 않습니다.
