# NAC Spring Boot MyBatis MariaDB Document Extraction

`CN_ELEC_DOC` 테이블의 전자문서 파일을 읽어서 텍스트를 추출하고, 결과를 `EXTRACT_ELEC_DOC` 테이블에 적재하는 Spring Boot + MyBatis 프로젝트입니다.

## 기술 스택

- Java 17
- Spring Boot 3.3.5
- MyBatis Spring Boot Starter
- MariaDB Java Client
- Apache Tika
- Maven

## DB 접속 정보

현재 `src/main/resources/application.yml`에는 아래 접속 정보가 반영되어 있습니다.

```yaml
spring:
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: jdbc:mariadb://192.168.250.25:33306/ARCHIVES_PUB_DB?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul
    username: root
    password: root
```

## 문서 파일 경로 규칙

기본 파일 저장소 경로는 다음과 같습니다.

```yaml
document:
  extract:
    base-path: /data/file-data/elec_doc
    default-transfer-year: 2023
    batch-size: 100
```

코드는 아래 규칙으로 실제 파일 경로를 만듭니다.

```text
/data/file-data/elec_doc/{TRANSFERYEAR}/{RC_RFILE_NO}/{RC_RITEM_NO}/{SAVE_FILE_NAME 또는 ORG_FILE_NAME}
```

예:

```text
/data/file-data/elec_doc/2023/202311293320/000000000073/202311293320_000000000073_N01.hwp
```

## MyBatis 설정

```yaml
mybatis:
  mapper-locations: classpath:/mapper/**/*.xml
  type-aliases-package: com.saltlux.nac.record,com.saltlux.nac.elecdoc
  configuration:
    map-underscore-to-camel-case: true
```

## 주요 처리 흐름

1. `CN_ELEC_DOC`에서 대상 문서 목록 조회
2. 파일 경로 생성
3. Apache Tika로 HWP, Office, PDF 등 텍스트 추출 시도
4. `EXTRACT_ELEC_DOC`에 결과 upsert
5. 성공 시 `EXTRACT_STATUS = 'PASS'`
6. 실패 시 `EXTRACT_STATUS = 'FAIL'`, `EXTRACT_ERR_MSG`에 오류 내용 저장

## 실행

```bash
mvn spring-boot:run
```

## 추출 실행 API

### 기본 실행

```bash
curl -X POST "http://localhost:8080/api/electronic-documents/extract"
```

### 연도/건수 지정

```bash
curl -X POST "http://localhost:8080/api/electronic-documents/extract?transferYear=2023&limit=100&offset=0"
```

응답 예시:

```json
{
  "transferYear": "2023",
  "requestedLimit": 100,
  "offset": 0,
  "targetCount": 100,
  "successCount": 95,
  "failCount": 5
}
```

## LLM 프롬프트 파일

LLM 프롬프트는 `src/main/java/com/saltlux/nac/prompt/*.txt` 파일로 관리합니다. 파일명에서 `.txt`를 제외한 값이 `promptName`입니다.

```text
src/main/java/com/saltlux/nac/prompt/summary.txt        -> promptName=summary
src/main/java/com/saltlux/nac/prompt/policy_extract.txt -> promptName=policy_extract
```

기본 요약 프롬프트는 `application.yml`의 `document.extract.llm.default-prompt-name`으로 지정합니다.

```yaml
document:
  extract:
    llm:
      default-prompt-name: summary
```

요약 API에서 다른 프롬프트를 선택하려면 `promptName`을 전달합니다. 단, 현재 요약 API는 LLM 응답을 `flag`, `summary` JSON으로 파싱하므로, `policy_extract`처럼 응답 형식이 다른 프롬프트는 별도 서비스에서 사용하는 구조로 확장해야 합니다.

```bash
curl -X POST "http://localhost:8081/api/electronic-documents/llm-summary?transferYear=2023&limit=100&promptName=summary"
```

## 정책명 추출 API

`policy_extract.txt` 프롬프트와 `TB_SUBJECT_ITEM_CODE`의 정책 후보 목록을 사용해 `CN_RITEM` 기록물 메타데이터에 정책명을 매핑합니다. 한 번에 기본 100건을 조회하고, `document.extract.llm.endpoints`에 설정된 LLM endpoint로 round-robin 분산 호출합니다.

```bash
curl -X POST "http://localhost:8081/api/electronic-documents/policy-extract?transferYear=2023&limit=100&offset=0"
```

전체 실행은 미처리 건(`LLM_POLICY_STATUS IS NULL`)을 100건씩 반복 처리합니다.

```bash
curl -X POST "http://localhost:8081/api/electronic-documents/policy-extract/all?transferYear=2023&limit=100"
```

실패 건까지 다시 처리하려면 `retryFail=true`를 전달합니다.

```bash
curl -X POST "http://localhost:8081/api/electronic-documents/policy-extract/all?transferYear=2023&limit=100&retryFail=true"
```

정상 응답은 `CN_RITEM`을 `RC_CODE`, `RC_RFILE_NO`, `RC_RITEM_NO` 기준으로 찾아 `POLICY_CD`, `POLICY_NM`, `LLM_POLICY_STATUS` 컬럼에 저장합니다. 실패 시 `LLM_POLICY_STATUS = 'FAIL'`, `LLM_POLICY_ERR_MSG`에 오류 메시지를 저장합니다.

## 이벤트 추출 API

`event_extract.txt` 프롬프트와 `TB_SUBJECT_ITEM_CODE`의 `CLS_CD = 'EVENT'` 후보 목록을 사용해 이벤트를 매핑합니다. 정책 추출과 동일하게 `transferYear`, `prodYear`, `limit`, `offset`, `retryFail` 파라미터를 사용할 수 있습니다.

```bash
curl -X POST "http://localhost:8081/api/electronic-documents/event-extract?transferYear=2023&prodYear=2012&limit=100&offset=0"
```

전체 실행:

```bash
curl -X POST "http://localhost:8081/api/electronic-documents/event-extract/all?transferYear=2023&prodYear=2012&limit=100"
```

정상 응답은 `CN_RITEM`을 `RC_CODE`, `RC_RFILE_NO`, `RC_RITEM_NO` 기준으로 찾아 `EX_EVENT_CD`, `EX_EVENT_NM`, `LLM_EVENT_STATUS` 컬럼에 저장합니다. 실패 시 `LLM_EVENT_STATUS = 'FAIL'`, `LLM_EVENT_ERR_MSG`에 오류 메시지를 저장합니다.

## 행사 추출 API

`activity_extract.txt` 프롬프트와 `TB_SUBJECT_ITEM_CODE`의 `CLS_CD = 'ACTIVITY'` 후보 목록을 사용해 행사를 매핑합니다.

```bash
curl -X POST "http://localhost:8081/api/electronic-documents/activity-extract?transferYear=2023&prodYear=2012&limit=100&offset=0"
```

전체 실행:

```bash
curl -X POST "http://localhost:8081/api/electronic-documents/activity-extract/all?transferYear=2023&prodYear=2012&limit=100"
```

정상 응답은 `CN_RITEM`을 `RC_CODE`, `RC_RFILE_NO`, `RC_RITEM_NO` 기준으로 찾아 `ACTIVITY_CD`, `ACTIVITY_NM`, `LLM_ACTIVITY_STATUS` 컬럼에 저장합니다. 실패 시 `LLM_ACTIVITY_STATUS = 'FAIL'`, `LLM_ACTIVITY_ERR_MSG`에 오류 메시지를 저장합니다.

## 주의사항

- `CN_ELEC_DOC.SAVE_FILE_NAME`이 있으면 우선 사용하고, 없으면 `ORG_FILE_NAME`을 사용합니다.
- `EXTRACT_ELEC_DOC`에는 `FILE_NAME`, `RC_RFILE_NO`, `RC_RITEM_NO` 유니크 키 기준으로 upsert합니다.
- Apache Tika는 PDF, Word, Excel, PowerPoint 계열은 비교적 안정적으로 처리합니다.
- HWP/HWPX는 파일 버전과 내부 구조에 따라 추출 실패 가능성이 있으므로, 실패 건은 `EXTRACT_ERR_MSG`를 확인해야 합니다.
- 이미지 OCR은 아직 포함하지 않았고, `IMG_DATAS`는 기본값 `[]`로 적재합니다.

## 기존 샘플 CRUD API

기존 테스트용 `records` CRUD API도 남겨두었습니다.

```bash
curl http://localhost:8080/api/records
```
